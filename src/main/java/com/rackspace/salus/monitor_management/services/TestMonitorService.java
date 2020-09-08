/*
 * Copyright 2020 Rackspace US, Inc.
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

import static com.rackspace.salus.telemetry.entities.Resource.REGION_METADATA;
import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.resolveZone;

import com.rackspace.salus.monitor_management.config.TestMonitorProperties;
import com.rackspace.salus.monitor_management.errors.InvalidTemplateException;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.MonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.TestMonitorResult;
import com.rackspace.salus.monitor_management.web.model.TestMonitorResult.TestMonitorResultData;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.entities.Resource;
import com.rackspace.salus.telemetry.errors.MissingRequirementException;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.messaging.TestMonitorRequestEvent;
import com.rackspace.salus.telemetry.messaging.TestMonitorResultsEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import com.rackspace.salus.telemetry.repositories.ResourceRepository;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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
import org.springframework.util.CollectionUtils;

/**
 * Given external requests for test monitors this service takes care of populating and initiating
 * the request and tracking the correlation of requests to results.
 */
@Service
@Slf4j
public class TestMonitorService {

  private static final Set<AgentType> SUPPORTED_AGENT_TYPES = Set
      .of(AgentType.TELEGRAF, AgentType.PACKAGES);

  private final MonitorConversionService monitorConversionService;
  private final ResourceRepository resourceRepository;
  private final EnvoyResourceManagement envoyResourceManagement;
  private final MonitorContentRenderer monitorContentRenderer;
  private final MonitorManagement monitorManagement;
  private final TestMonitorEventProducer testMonitorEventProducer;
  private final TestMonitorProperties testMonitorProperties;
  private ConcurrentHashMap<String/*correlationId*/, CompletableFuture<TestMonitorResult>> pending =
      new ConcurrentHashMap<>();

  @Autowired
  public TestMonitorService(MonitorConversionService monitorConversionService,
      ResourceRepository resourceRepository,
      EnvoyResourceManagement envoyResourceManagement,
      MonitorContentRenderer monitorContentRenderer,
      MonitorManagement monitorManagement,
      TestMonitorProperties testMonitorProperties,
      TestMonitorEventProducer testMonitorEventProducer) {
    this.monitorConversionService = monitorConversionService;
    this.resourceRepository = resourceRepository;
    this.envoyResourceManagement = envoyResourceManagement;
    this.monitorContentRenderer = monitorContentRenderer;
    this.monitorManagement = monitorManagement;
    this.testMonitorEventProducer = testMonitorEventProducer;
    this.testMonitorProperties = testMonitorProperties;
  }

  public CompletableFuture<TestMonitorResult> performTestMonitorOnResource(String tenantId,
      String resourceId,
      Long timeout,
      MonitorDetails details) {

    final boolean isRemote = details instanceof RemoteMonitorDetails;

    final MonitorCU monitorCU = monitorConversionService.convertFromInput(
        tenantId,
        null,
        new DetailedMonitorInput()
            .setDetails(details)
    );
    final String envoyId;
    final String correlationId;
    final TestMonitorRequestEvent event;
    try {
      if (!SUPPORTED_AGENT_TYPES.contains(monitorCU.getAgentType())) {
        throw new IllegalArgumentException("The given monitor type does not support test-monitors");
      }

      correlationId = UUID.randomUUID().toString();

      event = new TestMonitorRequestEvent()
          .setCorrelationId(correlationId)
          .setAgentType(monitorCU.getAgentType())
          .setMonitorType(monitorCU.getMonitorType())
          .setScope(monitorCU.getSelectorScope())
          .setTenantId(tenantId)
          .setResourceId(resourceId);
      final Optional<Resource> optionalResource = resourceRepository
          .findByTenantIdAndResourceId(tenantId, resourceId);
      if (optionalResource.isEmpty()) {
        return CompletableFuture.completedFuture(new TestMonitorResult()
            .setErrors(List.of("Unable to locate the resource for the test-monitor")));
      }

      Resource resource = optionalResource.get();
      if (isRemote) {
        final RemoteMonitorDetails remoteMonitorDetails = (RemoteMonitorDetails) details;
        List<String> monitoringZones = monitorManagement.determineMonitoringZones(
            remoteMonitorDetails.getMonitoringZones(),
            resource.getMetadata().get(REGION_METADATA));
        envoyId = resolveRemoteEnvoy(tenantId, monitoringZones);
      } else {
        envoyId = resolveLocalEnvoy(tenantId, resourceId);
      }

      try {
        event.setRenderedContent(
            monitorContentRenderer.render(monitorCU.getContent(), new ResourceDTO(resource, null))
        );
      } catch (InvalidTemplateException e) {
        throw new IllegalArgumentException("Failed to render monitor configuration content", e);
      }
    } catch (MissingRequirementException | IllegalArgumentException e) {
      return CompletableFuture
          .completedFuture(new TestMonitorResult().setErrors(List.of(e.getMessage())));
    }
    event.setEnvoyId(envoyId);

    if (timeout == null) {
      timeout = testMonitorProperties.getDefaultTimeout().toSeconds();
    }
    event.setTimeout(timeout);
    final CompletableFuture<TestMonitorResult> future = new CompletableFuture<TestMonitorResult>()
        .orTimeout(
            testMonitorProperties.getEndToEndTimeoutExtension()
                .plus(timeout, ChronoUnit.SECONDS)
                .toMillis(),
            TimeUnit.MILLISECONDS
        );

    pending.put(correlationId, future);

    final CompletableFuture<TestMonitorResult> interceptedFuture = future
        .handle((testMonitorOutput, throwable) -> {
          removeCompletedRequest(correlationId);

          if (throwable instanceof TimeoutException) {
            return buildTimedOutResult();
          } else if (throwable != null) {
            return new TestMonitorResult()
                .setErrors(List.of(String
                    .format("An unexpected internal error occurred: %s", throwable.getMessage())));
          } else {
            return testMonitorOutput;
          }
        });

    log.info("Sending test-monitor request for tenant={} with correlationId={}",
        tenantId, correlationId);
    testMonitorEventProducer.send(event);

    return interceptedFuture;
  }

  private String resolveRemoteEnvoy(String tenantId,
      List<String> monitoringZones) {
    if (CollectionUtils.isEmpty(monitoringZones)) {
      throw new IllegalArgumentException("test-monitor requires one monitoring zone to be given");
    } else if (monitoringZones.size() > 1) {
      throw new IllegalArgumentException(
          "test-monitor requires only one monitoring zone to be given");
    }

    final EnvoyResourcePair leastLoaded = monitorManagement
        .findLeastLoadedEnvoyInZone(resolveZone(tenantId, monitoringZones.get(0)))
        .orElseThrow(() -> new MissingRequirementException(
            "No envoys were available in the given monitoring zone"));

    return leastLoaded.getEnvoyId();
  }

  private String resolveLocalEnvoy(String tenantId, String resourceId) {
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
    return resourceInfo.getEnvoyId();
  }

  void handleTestMonitorResultsEvent(TestMonitorResultsEvent event) {
    final String correlationId = event.getCorrelationId();
    final CompletableFuture<TestMonitorResult> future =
        pending.get(correlationId);

    if (future == null) {
      log.trace(
          "Ignoring test-monitor results with correlationId={} not tracked by this instance",
          correlationId
      );
      return;
    }

    final TestMonitorResult result = new TestMonitorResult()
        .setErrors(event.getErrors())
        .setData(new TestMonitorResultData().setMetrics(event.getMetrics()));

    future.complete(result);
    if (log.isDebugEnabled()) {
      log.debug("Resolved test-monitor request with correlationId={} with result={}", correlationId,
          result);
    } else {
      log.info("Resolved test-monitor request with correlationId={}", correlationId);
    }
  }

  // for unit test validation
  boolean containsCorrelationId(String correlationId) {
    return pending.containsKey(correlationId);
  }

  private void removeCompletedRequest(String correlationId) {
    log.debug("Removing completed test-monitor with correlationId={} from table", correlationId);
    final CompletableFuture<TestMonitorResult> prev = pending.remove(correlationId);
    if (prev == null) {
      log.warn(
          "Test-monitor with correlationId={} was unexpected absent during removal", correlationId);
    }
  }

  private TestMonitorResult buildTimedOutResult() {
    return new TestMonitorResult()
        .setErrors(
            List.of(String.format(
                "Test-monitor did not receive results within the expected duration of %ds",
                testMonitorProperties.getEndToEndTimeoutExtension().getSeconds()
            ))
        );
  }
}
