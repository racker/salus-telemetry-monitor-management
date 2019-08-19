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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.rackspace.salus.monitor_management.config.TestMonitorProperties;
import com.rackspace.salus.monitor_management.errors.InvalidTemplateException;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.MonitorDetails;
import com.rackspace.salus.monitor_management.web.model.TestMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.telegraf.Cpu;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.messaging.TestMonitorRequestEvent;
import com.rackspace.salus.telemetry.messaging.TestMonitorResultsEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import com.rackspace.salus.telemetry.model.SimpleNameTagValueMetric;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestMonitorServiceTest {

  private static Duration resultsTimeout = Duration.ofMillis(500);

  @Configuration
  @Import({TestMonitorService.class})
  public static class TestConfig {

    @Bean
    public TestMonitorProperties testMonitorProperties() {
      final TestMonitorProperties properties = new TestMonitorProperties();
      properties.setResultsTimeout(resultsTimeout);
      return properties;
    }
  }

  @MockBean
  MonitorConversionService monitorConversionService;

  @MockBean
  MonitorContentRenderer monitorContentRenderer;

  @MockBean
  ResourceApi resourceApi;

  @MockBean
  EnvoyResourceManagement envoyResourceManagement;

  @MockBean
  TestMonitorEventProducer testMonitorEventProducer;

  @Autowired
  TestMonitorService testMonitorService;

  @Captor
  ArgumentCaptor<TestMonitorRequestEvent> reqEventCaptor;

  @Test
  public void testPerformTestMonitorOnResource_normal()
      throws InvalidTemplateException, ExecutionException, InterruptedException {

    MonitorCU monitorCU = new MonitorCU()
        .setAgentType(AgentType.TELEGRAF)
        .setContent("content-1");
    when(monitorConversionService.convertFromInput(any()))
        .thenReturn(monitorCU);

    ResourceDTO resource = new ResourceDTO()
        .setResourceId("r-1")
        .setLabels(Map.of("key-1", "value-1"));
    when(resourceApi.getByResourceId(any(), any()))
        .thenReturn(resource);

    ResourceInfo resourceInfo = new ResourceInfo()
        .setResourceId("r-1")
        .setEnvoyId("e-1");
    when(envoyResourceManagement.getOne(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(resourceInfo));

    when(monitorContentRenderer.render(any(), any()))
        .thenReturn("rendered-1");

    // EXECUTE

    MonitorDetails monitorDetails = new LocalMonitorDetails()
        .setPlugin(new Cpu());
    final CompletableFuture<TestMonitorOutput> future = testMonitorService
        .performTestMonitorOnResource("t-1", "r-1", monitorDetails);

    // VERIFY

    verify(testMonitorEventProducer).send(reqEventCaptor.capture());
    assertThat(reqEventCaptor.getValue().getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(reqEventCaptor.getValue().getEnvoyId()).isEqualTo("e-1");
    assertThat(reqEventCaptor.getValue().getRenderedContent()).isEqualTo("rendered-1");
    assertThat(reqEventCaptor.getValue().getResourceId()).isEqualTo("r-1");
    assertThat(reqEventCaptor.getValue().getTenantId()).isEqualTo("t-1");

    // exercise result processing

    final String correlationId = reqEventCaptor.getValue().getCorrelationId();
    assertThat(correlationId).isNotBlank();

    // Simulate a results event getting consumed

    final List<SimpleNameTagValueMetric> expectedMetrics = List.of(
        new SimpleNameTagValueMetric()
            .setName("cpu")
            .setTags(Map.of("cpu", "cpu1"))
            .setFvalues(Map.of("usage", 1.45))
    );
    TestMonitorResultsEvent resultsEvent = new TestMonitorResultsEvent()
        .setCorrelationId(correlationId)
        .setErrors(List.of("error-1"))
        .setMetrics(expectedMetrics);
    testMonitorService.handleTestMonitorResultsEvent(resultsEvent);

    assertThat(future.isDone()).isTrue();
    final TestMonitorOutput output = future.get();
    assertThat(output).isNotNull();
    assertThat(output.getErrors()).containsExactly("error-1");
    assertThat(output.getMetrics()).isEqualTo(expectedMetrics);

    assertThat(testMonitorService.containsCorrelationId(correlationId)).isFalse();

    verify(monitorConversionService)
        .convertFromInput(ArgumentMatchers.argThat(detailedMonitorInput -> {
          assertThat(detailedMonitorInput.getDetails()).isInstanceOf(LocalMonitorDetails.class);
          assertThat(((LocalMonitorDetails) detailedMonitorInput.getDetails()).getPlugin())
              .isInstanceOf(Cpu.class);
          return true;
        }));

    verify(resourceApi).getByResourceId("t-1", "r-1");

    verify(monitorContentRenderer).render("content-1", resource);

    verify(envoyResourceManagement).getOne("t-1", "r-1");

    verifyNoMoreInteractions(
        monitorConversionService, monitorContentRenderer, resourceApi, testMonitorEventProducer);
  }

  @Test
  public void testPerformTestMonitorOnResource_timeout()
      throws InvalidTemplateException, InterruptedException, TimeoutException, ExecutionException {
    MonitorCU monitorCU = new MonitorCU()
        .setAgentType(AgentType.TELEGRAF)
        .setContent("content-1");
    when(monitorConversionService.convertFromInput(any()))
        .thenReturn(monitorCU);

    ResourceDTO resource = new ResourceDTO()
        .setResourceId("r-1")
        .setLabels(Map.of("key-1", "value-1"));
    when(resourceApi.getByResourceId(any(), any()))
        .thenReturn(resource);

    ResourceInfo resourceInfo = new ResourceInfo()
        .setResourceId("r-1")
        .setEnvoyId("e-1");
    when(envoyResourceManagement.getOne(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(resourceInfo));

    when(monitorContentRenderer.render(any(), any()))
        .thenReturn("rendered-1");

    // EXECUTE

    MonitorDetails monitorDetails = new LocalMonitorDetails()
        .setPlugin(new Cpu());
    final CompletableFuture<TestMonitorOutput> future = testMonitorService
        .performTestMonitorOnResource("t-1", "r-1", monitorDetails);

    // VERIFY

    verify(testMonitorEventProducer).send(reqEventCaptor.capture());
    assertThat(reqEventCaptor.getValue().getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(reqEventCaptor.getValue().getEnvoyId()).isEqualTo("e-1");
    assertThat(reqEventCaptor.getValue().getRenderedContent()).isEqualTo("rendered-1");
    assertThat(reqEventCaptor.getValue().getResourceId()).isEqualTo("r-1");
    assertThat(reqEventCaptor.getValue().getTenantId()).isEqualTo("t-1");
    final String correlationId = reqEventCaptor.getValue().getCorrelationId();
    assertThat(correlationId).isNotBlank();

    // Purposely don't pass a results event to the service and just let timeout happen

    // ...but timeout gets re-mapped by the service to an output object with error set
    final TestMonitorOutput testMonitorOutput = future
        .get(resultsTimeout.toMillis() + 100, TimeUnit.MILLISECONDS);

    assertThat(testMonitorOutput).isNotNull();
    assertThat(testMonitorOutput.getMetrics()).isNull();
    assertThat(testMonitorOutput.getErrors())
        .containsExactly("Test-monitor did not receive results within the expected duration of 0s");

    assertThat(testMonitorService.containsCorrelationId(correlationId)).isFalse();

    verify(monitorConversionService)
        .convertFromInput(ArgumentMatchers.argThat(detailedMonitorInput -> {
          assertThat(detailedMonitorInput.getDetails()).isInstanceOf(LocalMonitorDetails.class);
          assertThat(((LocalMonitorDetails) detailedMonitorInput.getDetails()).getPlugin())
              .isInstanceOf(Cpu.class);
          return true;
        }));

    verify(resourceApi).getByResourceId("t-1", "r-1");

    verify(monitorContentRenderer).render("content-1", resource);

    verify(envoyResourceManagement).getOne("t-1", "r-1");


    verifyNoMoreInteractions(
        monitorConversionService, monitorContentRenderer, resourceApi, testMonitorEventProducer);
  }
}