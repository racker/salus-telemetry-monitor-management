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

import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.resolveZone;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.rackspace.salus.monitor_management.config.TestMonitorProperties;
import com.rackspace.salus.monitor_management.errors.InvalidTemplateException;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.MonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.TestMonitorResult;
import com.rackspace.salus.monitor_management.web.model.telegraf.Cpu;
import com.rackspace.salus.monitor_management.web.model.telegraf.Ping;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.telemetry.entities.Resource;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.messaging.TestMonitorRequestEvent;
import com.rackspace.salus.telemetry.messaging.TestMonitorResultsEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import com.rackspace.salus.telemetry.model.SimpleNameTagValueMetric;
import com.rackspace.salus.telemetry.repositories.ResourceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@Import({SimpleMeterRegistry.class})
@SpringBootTest
@Profile("less-logging")
public class TestMonitorServiceTest {

  private static final long DEFAULT_TIMEOUT = 2;
  private static final Duration resultsTimeout = Duration.ofMillis(500);

  @Configuration
  @Import({TestMonitorService.class, SimpleMeterRegistry.class})
  public static class TestConfig {

    @Bean
    public TestMonitorProperties testMonitorProperties() {
      final TestMonitorProperties properties = new TestMonitorProperties();
      properties.setDefaultTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT));
      properties.setEndToEndTimeoutExtension(resultsTimeout);
      return properties;
    }
  }

  @MockBean
  MonitorConversionService monitorConversionService;

  @MockBean
  MonitorContentRenderer monitorContentRenderer;

  @MockBean
  MonitorManagement monitorManagement;

  @MockBean
  PolicyApi policyApi;

  @MockBean
  ResourceRepository resourceRepository;

  @MockBean
  EnvoyResourceManagement envoyResourceManagement;

  @MockBean
  TestMonitorEventProducer testMonitorEventProducer;

  @Autowired
  TestMonitorService testMonitorService;

  @Captor
  ArgumentCaptor<TestMonitorRequestEvent> reqEventCaptor;

  @Test
  public void testPerformTestMonitorOnResource_local_normal()
      throws InvalidTemplateException, ExecutionException, InterruptedException {

    MonitorCU monitorCU = new MonitorCU()
        .setAgentType(AgentType.TELEGRAF)
        .setContent("content-1");
    when(monitorConversionService.convertFromInput(anyString(), any(), any()))
        .thenReturn(monitorCU);

    Resource resource = new Resource()
        .setResourceId("r-1")
        .setLabels(Map.of("key-1", "value-1"))
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);
    when(resourceRepository.findByTenantIdAndResourceId(any(), any()))
        .thenReturn(Optional.of(resource));

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
    final CompletableFuture<TestMonitorResult> future = testMonitorService
        .performTestMonitorOnResource("t-1", "r-1", 3L, monitorDetails);

    // VERIFY

    verify(testMonitorEventProducer).send(reqEventCaptor.capture());
    assertThat(reqEventCaptor.getValue().getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(reqEventCaptor.getValue().getEnvoyId()).isEqualTo("e-1");
    assertThat(reqEventCaptor.getValue().getRenderedContent()).isEqualTo("rendered-1");
    assertThat(reqEventCaptor.getValue().getResourceId()).isEqualTo("r-1");
    assertThat(reqEventCaptor.getValue().getTenantId()).isEqualTo("t-1");
    assertThat(reqEventCaptor.getValue().getTimeout()).isEqualTo(3L);

    // exercise result processing

    final String correlationId = reqEventCaptor.getValue().getCorrelationId();
    assertThat(correlationId).isNotBlank();
    assertThat(testMonitorService.containsCorrelationId(correlationId)).isTrue();

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
    final TestMonitorResult output = future.get();
    assertThat(output).isNotNull();
    assertThat(output.getErrors()).containsExactly("error-1");
    assertThat(output.getData().getMetrics()).isEqualTo(expectedMetrics);

    assertThat(testMonitorService.containsCorrelationId(correlationId)).isFalse();

    verify(monitorConversionService)
        .convertFromInput(eq("t-1"),
            isNull(),
            ArgumentMatchers.argThat(detailedMonitorInput -> {
              assertThat(detailedMonitorInput.getDetails()).isInstanceOf(LocalMonitorDetails.class);
              assertThat(((LocalMonitorDetails) detailedMonitorInput.getDetails()).getPlugin())
                  .isInstanceOf(Cpu.class);
              return true;
            }));

    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");

    verify(monitorContentRenderer).render(eq("content-1"), argThat(resourceDTO -> {
      assertThat(resourceDTO.getResourceId()).isEqualTo(resource.getResourceId());
      assertThat(resourceDTO.getLabels()).isEqualTo(resource.getLabels());
      return true;
    }));

    verify(envoyResourceManagement).getOne("t-1", "r-1");

    verifyNoMoreInteractions(
        monitorConversionService, monitorContentRenderer, resourceRepository,
        testMonitorEventProducer, monitorManagement
    );
  }

  @Test
  public void testPerformTestMonitorOnResource_resourceNotFound()
      throws InterruptedException, ExecutionException, TimeoutException {

    MonitorCU monitorCU = new MonitorCU()
        .setAgentType(AgentType.TELEGRAF)
        .setContent("content-1");
    when(monitorConversionService.convertFromInput(anyString(), any(), any()))
        .thenReturn(monitorCU);

    when(resourceRepository.findByTenantIdAndResourceId(any(), any()))
        .thenReturn(Optional.empty());

    // EXECUTE

    MonitorDetails monitorDetails = new LocalMonitorDetails()
        .setPlugin(new Cpu());

    CompletableFuture<TestMonitorResult> completableResult = testMonitorService
        .performTestMonitorOnResource("t-1", "r-1", null, monitorDetails);
    final TestMonitorResult result = completableResult.get(5, TimeUnit.SECONDS);
    assertThat(
        result.getErrors().get(0).equals("Unable to locate the resource for the test-monitor"));

    verify(monitorConversionService)
        .convertFromInput(eq("t-1"),
            isNull(),
            ArgumentMatchers.argThat(detailedMonitorInput -> {
              assertThat(detailedMonitorInput.getDetails()).isInstanceOf(LocalMonitorDetails.class);
              assertThat(((LocalMonitorDetails) detailedMonitorInput.getDetails()).getPlugin())
                  .isInstanceOf(Cpu.class);
              return true;
            }));

    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");

    verifyNoMoreInteractions(
        monitorConversionService, monitorContentRenderer, resourceRepository,
        testMonitorEventProducer
    );
  }

  @Test
  public void testPerformTestMonitorOnResource_timeout()
      throws InvalidTemplateException, InterruptedException, TimeoutException, ExecutionException {
    MonitorCU monitorCU = new MonitorCU()
        .setAgentType(AgentType.TELEGRAF)
        .setContent("content-1");
    when(monitorConversionService.convertFromInput(anyString(), any(), any()))
        .thenReturn(monitorCU);

    Resource resource = new Resource()
        .setResourceId("r-1")
        .setLabels(Map.of("key-1", "value-1"))
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);
    when(resourceRepository.findByTenantIdAndResourceId(any(), any()))
        .thenReturn(Optional.of(resource));

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
    final CompletableFuture<TestMonitorResult> future = testMonitorService
        .performTestMonitorOnResource("t-1", "r-1", 1L, monitorDetails);

    // VERIFY

    verify(testMonitorEventProducer).send(reqEventCaptor.capture());
    assertThat(reqEventCaptor.getValue().getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(reqEventCaptor.getValue().getEnvoyId()).isEqualTo("e-1");
    assertThat(reqEventCaptor.getValue().getRenderedContent()).isEqualTo("rendered-1");
    assertThat(reqEventCaptor.getValue().getResourceId()).isEqualTo("r-1");
    assertThat(reqEventCaptor.getValue().getTenantId()).isEqualTo("t-1");
    assertThat(reqEventCaptor.getValue().getTimeout()).isEqualTo(1L);
    final String correlationId = reqEventCaptor.getValue().getCorrelationId();
    assertThat(correlationId).isNotBlank();
    assertThat(testMonitorService.containsCorrelationId(correlationId)).isTrue();

    // Purposely don't pass a results event to the service and just let timeout happen

    // ...but timeout gets re-mapped by the service to an output object with error set
    final TestMonitorResult testMonitorResult = future
        .get(resultsTimeout.toMillis() + 1100, TimeUnit.MILLISECONDS);

    assertThat(testMonitorResult).isNotNull();
    assertThat(testMonitorResult.getData()).isNull();
    assertThat(testMonitorResult.getErrors().get(0)
        .equals("Test-monitor did not receive results within the expected duration of 0s"));

    assertThat(testMonitorService.containsCorrelationId(correlationId)).isFalse();

    verify(monitorConversionService)
        .convertFromInput(eq("t-1"),
            isNull(),
            ArgumentMatchers.argThat(detailedMonitorInput -> {
              assertThat(detailedMonitorInput.getDetails()).isInstanceOf(LocalMonitorDetails.class);
              assertThat(((LocalMonitorDetails) detailedMonitorInput.getDetails()).getPlugin())
                  .isInstanceOf(Cpu.class);
              return true;
            }));

    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");

    verify(monitorContentRenderer).render(eq("content-1"), argThat(resourceDTO -> {
      assertThat(resourceDTO.getResourceId()).isEqualTo(resource.getResourceId());
      assertThat(resourceDTO.getLabels()).isEqualTo(resource.getLabels());
      return true;
    }));

    verify(envoyResourceManagement).getOne("t-1", "r-1");

    verifyNoMoreInteractions(
        monitorConversionService, monitorContentRenderer, resourceRepository,
        testMonitorEventProducer
    );
  }

  @Test
  public void testPerformTestMonitorOnResource_remote_normal_explicitTimeout()
      throws InvalidTemplateException, ExecutionException, InterruptedException {
    final List<String> monitoringZones = List.of("z-1");

    commonPerformTestMonitorOnResource_remote_normal(monitoringZones, 7L, 7L);
  }

  @Test
  public void testPerformTestMonitorOnResource_remote_normal_defaultTimeout()
      throws InvalidTemplateException, ExecutionException, InterruptedException {
    final List<String> monitoringZones = List.of("z-1");

    commonPerformTestMonitorOnResource_remote_normal(monitoringZones, null, DEFAULT_TIMEOUT);
  }

  private void commonPerformTestMonitorOnResource_remote_normal(List<String> monitoringZones,
      Long timeout, Long expectedTimeout)
      throws InvalidTemplateException, InterruptedException, ExecutionException {
    MonitorCU monitorCU = new MonitorCU()
        .setAgentType(AgentType.TELEGRAF)
        .setContent("content-1");
    when(monitorConversionService.convertFromInput(anyString(), any(), any()))
        .thenReturn(monitorCU);

    Resource resource = new Resource()
        .setResourceId("r-1")
        .setLabels(Map.of("key-1", "value-1"))
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);
    when(resourceRepository.findByTenantIdAndResourceId(any(), any()))
        .thenReturn(Optional.of(resource));

    // just return the zones given
    when(monitorManagement.determineMonitoringZones(any(), (String) any()))
        .then(invocationOnMock -> invocationOnMock.getArgument(0));

    when(monitorManagement.findLeastLoadedEnvoyInZone(any()))
        .thenReturn(Optional.of(new EnvoyResourcePair().setEnvoyId("e-1")));

    when(monitorContentRenderer.render(any(), any()))
        .thenReturn("rendered-1");

    // EXECUTE

    MonitorDetails monitorDetails = new RemoteMonitorDetails()
        .setMonitoringZones(monitoringZones)
        .setPlugin(new Ping());
    final CompletableFuture<TestMonitorResult> future = testMonitorService
        .performTestMonitorOnResource("t-1", "r-1", timeout, monitorDetails);

    // VERIFY

    verify(testMonitorEventProducer).send(reqEventCaptor.capture());
    assertThat(reqEventCaptor.getValue().getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(reqEventCaptor.getValue().getEnvoyId()).isEqualTo("e-1");
    assertThat(reqEventCaptor.getValue().getRenderedContent()).isEqualTo("rendered-1");
    assertThat(reqEventCaptor.getValue().getResourceId()).isEqualTo("r-1");
    assertThat(reqEventCaptor.getValue().getTenantId()).isEqualTo("t-1");
    assertThat(reqEventCaptor.getValue().getTimeout()).isEqualTo(expectedTimeout);

    // exercise result processing

    final String correlationId = reqEventCaptor.getValue().getCorrelationId();
    assertThat(correlationId).isNotBlank();
    assertThat(testMonitorService.containsCorrelationId(correlationId)).isTrue();

    // Simulate a results event getting consumed

    final List<SimpleNameTagValueMetric> expectedMetrics = List.of(
        new SimpleNameTagValueMetric()
            .setName("ping")
            .setIvalues(Map.of("average_response_ms", 12L))
    );
    TestMonitorResultsEvent resultsEvent = new TestMonitorResultsEvent()
        .setCorrelationId(correlationId)
        .setErrors(List.of("error-1"))
        .setMetrics(expectedMetrics);
    testMonitorService.handleTestMonitorResultsEvent(resultsEvent);

    assertThat(future.isDone()).isTrue();
    final TestMonitorResult output = future.get();
    assertThat(output).isNotNull();
    assertThat(output.getErrors()).containsExactly("error-1");
    assertThat(output.getData().getMetrics()).isEqualTo(expectedMetrics);

    assertThat(testMonitorService.containsCorrelationId(correlationId)).isFalse();

    verify(monitorConversionService)
        .convertFromInput(eq("t-1"),
            isNull(),
            ArgumentMatchers.argThat(detailedMonitorInput -> {
              assertThat(detailedMonitorInput.getDetails())
                  .isInstanceOf(RemoteMonitorDetails.class);
              assertThat(((RemoteMonitorDetails) detailedMonitorInput.getDetails()).getPlugin())
                  .isInstanceOf(Ping.class);
              return true;
            }));

    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");

    verify(monitorContentRenderer).render(eq("content-1"), argThat(resourceDTO -> {
      assertThat(resourceDTO.getResourceId()).isEqualTo(resource.getResourceId());
      assertThat(resourceDTO.getLabels()).isEqualTo(resource.getLabels());
      return true;
    }));

    verify(monitorManagement).determineMonitoringZones(monitoringZones, null);
    verify(monitorManagement).findLeastLoadedEnvoyInZone(resolveZone("t-1", "z-1"));

    verifyNoMoreInteractions(
        monitorConversionService, monitorContentRenderer, resourceRepository,
        testMonitorEventProducer, monitorManagement
    );
  }

  @Test
  public void testPerformTestMonitorOnResource_remote_nullZones()
      throws InterruptedException, ExecutionException, TimeoutException {
    commonPerformTestMonitorOnResource_remote_failedZones(null,
        "test-monitor requires one monitoring zone to be given");
  }

  @Test
  public void testPerformTestMonitorOnResource_remote_emptyZones()
      throws InterruptedException, ExecutionException, TimeoutException {
    commonPerformTestMonitorOnResource_remote_failedZones(List.of(),
        "test-monitor requires one monitoring zone to be given");
  }

  @Test
  public void testPerformTestMonitorOnResource_remote_tooManyZones()
      throws InterruptedException, ExecutionException, TimeoutException {
    commonPerformTestMonitorOnResource_remote_failedZones(List.of("z-1", "z-2"),
        "test-monitor requires only one monitoring zone to be given");
  }

  private void commonPerformTestMonitorOnResource_remote_failedZones(List<String> monitoringZones,
      String expectedMessage) throws InterruptedException, ExecutionException, TimeoutException {
    MonitorCU monitorCU = new MonitorCU()
        .setAgentType(AgentType.TELEGRAF)
        .setContent("content-1");
    when(monitorConversionService.convertFromInput(anyString(), any(), any()))
        .thenReturn(monitorCU);

    Resource resource = new Resource()
        .setResourceId("r-1")
        .setLabels(Map.of("key-1", "value-1"))
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);
    when(resourceRepository.findByTenantIdAndResourceId(any(), any()))
        .thenReturn(Optional.of(resource));

    // just return the zones given
    when(monitorManagement.determineMonitoringZones(any(), (String) any()))
        .then(invocationOnMock -> invocationOnMock.getArgument(0));

    // EXECUTE

    MonitorDetails monitorDetails = new RemoteMonitorDetails()
        .setMonitoringZones(monitoringZones)
        .setPlugin(new Ping());

    CompletableFuture<TestMonitorResult> completableResult = testMonitorService
        .performTestMonitorOnResource("t-1", "r-1", null, monitorDetails);
    final TestMonitorResult result = completableResult.get(5, TimeUnit.SECONDS);
    assertThat(
        result.getErrors().get(0)
            .equals("test-monitor requires only one monitoring zone to be given"));

    // VERIFY

    verify(monitorConversionService)
        .convertFromInput(eq("t-1"),
            isNull(),
            ArgumentMatchers.argThat(detailedMonitorInput -> {
              assertThat(detailedMonitorInput.getDetails())
                  .isInstanceOf(RemoteMonitorDetails.class);
              assertThat(((RemoteMonitorDetails) detailedMonitorInput.getDetails()).getPlugin())
                  .isInstanceOf(Ping.class);
              return true;
            }));

    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");

    verify(monitorManagement).determineMonitoringZones(monitoringZones, null);

    verifyNoMoreInteractions(
        monitorConversionService, monitorContentRenderer, resourceRepository,
        testMonitorEventProducer, monitorManagement
    );
  }

  @Test
  public void testPerformTestMonitorOnResource_remote_noEnvoyInZone()
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<String> monitoringZones = List.of("z-1");

    MonitorCU monitorCU = new MonitorCU()
        .setAgentType(AgentType.TELEGRAF)
        .setContent("content-1");
    when(monitorConversionService.convertFromInput(anyString(), any(), any()))
        .thenReturn(monitorCU);

    Resource resource = new Resource()
        .setResourceId("r-1")
        .setLabels(Map.of("key-1", "value-1"))
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);
    when(resourceRepository.findByTenantIdAndResourceId(any(), any()))
        .thenReturn(Optional.of(resource));

    // just return the zones given
    when(monitorManagement.determineMonitoringZones(any(), (String) any()))
        .then(invocationOnMock -> invocationOnMock.getArgument(0));

    //noinspection ConstantConditions
    when(monitorManagement.findLeastLoadedEnvoyInZone(any()))
        .thenReturn(Optional.empty());

    // EXECUTE

    MonitorDetails monitorDetails = new RemoteMonitorDetails()
        .setMonitoringZones(monitoringZones)
        .setPlugin(new Ping());

    CompletableFuture<TestMonitorResult> completableResult = testMonitorService
        .performTestMonitorOnResource("t-1", "r-1", null, monitorDetails);
    final TestMonitorResult result = completableResult.get(5, TimeUnit.SECONDS);
    assertThat(
        result.getErrors().get(0)
            .equals("No envoys were available in the given monitoring zone"));

    // VERIFY

    verify(monitorConversionService)
        .convertFromInput(eq("t-1"),
            isNull(),
            ArgumentMatchers.argThat(detailedMonitorInput -> {
              assertThat(detailedMonitorInput.getDetails())
                  .isInstanceOf(RemoteMonitorDetails.class);
              assertThat(((RemoteMonitorDetails) detailedMonitorInput.getDetails()).getPlugin())
                  .isInstanceOf(Ping.class);
              return true;
            }));

    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");

    verify(monitorManagement).determineMonitoringZones(monitoringZones, null);
    verify(monitorManagement).findLeastLoadedEnvoyInZone(resolveZone("t-1", "z-1"));

    verifyNoMoreInteractions(
        monitorConversionService, monitorContentRenderer, resourceRepository,
        testMonitorEventProducer, monitorManagement
    );
  }
}