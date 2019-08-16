/*
 * Copyright 2019 Rackspace US, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rackspace.salus.monitor_management.services;

import static java.util.Collections.singletonList;

import com.rackspace.salus.monitor_management.config.TestMonitorProperties;
import com.rackspace.salus.monitor_management.errors.InvalidTemplateException;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.MonitorDetails;
import com.rackspace.salus.monitor_management.web.model.TestMonitorOutput;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.errors.MissingRequirementException;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.messaging.TestMonitorRequestEvent;
import com.rackspace.salus.telemetry.messaging.TestMonitorResultsEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Given external requests for test monitors this service takes care of populating and initiating
 * the request and tracking the correlation of requests to results.
 */
@Service
@Slf4j
public class TestMonitorService {

  private static final Set<AgentType> SUPPORTED_AGENT_TYPES = Set.of(AgentType.TELEGRAF);

  private final MonitorConversionService monitorConversionService;
  private final ResourceApi resourceApi;
  private final EnvoyResourceManagement envoyResourceManagement;
  private final MonitorContentRenderer monitorContentRenderer;
  private final TestMonitorEventProducer testMonitorEventProducer;
  private final TestMonitorProperties testMonitorProperties;
  private ConcurrentHashMap<String/*correlationId*/, CompletableFuture<TestMonitorOutput>> pending =
      new ConcurrentHashMap<>();

  @Autowired
  public TestMonitorService(MonitorConversionService monitorConversionService,
                            ResourceApi resourceApi,
                            EnvoyResourceManagement envoyResourceManagement,
                            MonitorContentRenderer monitorContentRenderer,
                            TestMonitorProperties testMonitorProperties,
                            TestMonitorEventProducer testMonitorEventProducer) {
    this.monitorConversionService = monitorConversionService;
    this.resourceApi = resourceApi;
    this.envoyResourceManagement = envoyResourceManagement;
    this.monitorContentRenderer = monitorContentRenderer;
    this.testMonitorEventProducer = testMonitorEventProducer;
    this.testMonitorProperties = testMonitorProperties;
  }

  public CompletableFuture<TestMonitorOutput> performTestMonitorOnResource(String tenantId,
                                                                           String resourceId,
                                                                           MonitorDetails details) {

    if (!(details instanceof LocalMonitorDetails)) {
      // this restriction will be removed in a future enhancement
      throw new IllegalArgumentException("test-monitor currently only supports local monitors");
    }

    final MonitorCU monitorCU = monitorConversionService.convertFromInput(
        new DetailedMonitorInput()
            .setDetails(details)
    );

    if (!SUPPORTED_AGENT_TYPES.contains(monitorCU.getAgentType())) {
      throw new IllegalArgumentException("The given monitor type does not support test-monitors");
    }

    final String correlationId = UUID.randomUUID().toString();

    final TestMonitorRequestEvent event = new TestMonitorRequestEvent()
        .setCorrelationId(correlationId)
        .setAgentType(monitorCU.getAgentType())
        .setTenantId(tenantId)
        .setResourceId(resourceId);

    final ResourceDTO resource = resourceApi.getByResourceId(tenantId, resourceId);
    if (resource == null) {
      throw new MissingRequirementException("Unable to locate the resource for the test-monitor");
    }

    final ResourceInfo resourceInfo;
    try {
      resourceInfo = envoyResourceManagement.getOne(tenantId, resourceId)
          .get();
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException("Failed to locate Envoy for resource", e);
    }

    if (resourceInfo == null) {
      throw new MissingRequirementException(
          "An Envoy is not currently attached for the requested resource");
    }
    event.setEnvoyId(resourceInfo.getEnvoyId());

    try {
      event.setRenderedContent(
          monitorContentRenderer.render(monitorCU.getContent(), resource)
      );
    } catch (InvalidTemplateException e) {
      throw new IllegalArgumentException("Failed to render monitor configuration content", e);
    }

    final CompletableFuture<TestMonitorOutput> future = new CompletableFuture<TestMonitorOutput>()
        .orTimeout(testMonitorProperties.getResultsTimeout().toMillis(), TimeUnit.MILLISECONDS);

    pending.put(correlationId, future);

    final CompletableFuture<TestMonitorOutput> interceptedFuture = future
        .handle((testMonitorOutput, throwable) -> {
          removeCompletedRequest(correlationId);

          if (throwable instanceof TimeoutException) {
            return buildTimedOutResult();
          } else if (throwable != null) {
            return new TestMonitorOutput()
                .setErrors(List.of(String
                    .format("An unexpected internal error occurred: %s", throwable.getMessage())));
          } else {
            return testMonitorOutput;
          }
        });

    testMonitorEventProducer.send(event);

    return interceptedFuture;
  }

  void handleTestMonitorResultsEvent(TestMonitorResultsEvent event) {
    final String correlationId = event.getCorrelationId();
    final CompletableFuture<TestMonitorOutput> future =
        pending.get(correlationId);

    if (future == null) {
      log.trace(
          "Ignoring test-monitor results with correlationId={} not tracked by this instance",
          correlationId
      );
      return;
    }

    final TestMonitorOutput result = new TestMonitorOutput()
        .setErrors(event.getErrors())
        .setMetrics(event.getMetrics());

    future.complete(result);
    log.debug("Set result of request correlationId={} to result={}", correlationId, result);
  }

  // for unit test validation
  boolean containsCorrelationId(String correlationId) {
    return pending.containsKey(correlationId);
  }

  private void removeCompletedRequest(String correlationId) {
    log.debug("Removing completed test-monitor with correlationId={} from table", correlationId);
    final CompletableFuture<TestMonitorOutput> prev = pending.remove(correlationId);
    if (prev == null) {
      log.warn(
          "Test-monitor with correlationId={} was unexpected absent during removal", correlationId);
    }
  }

  private TestMonitorOutput buildTimedOutResult() {
    return new TestMonitorOutput()
        .setErrors(
            singletonList(String.format(
                "Test-monitor did not receive results within the expected duration of %ds",
                testMonitorProperties.getResultsTimeout().getSeconds()
            ))
        );
  }
}
