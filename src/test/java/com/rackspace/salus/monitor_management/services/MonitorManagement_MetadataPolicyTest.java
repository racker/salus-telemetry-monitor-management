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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isOneOf;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.config.DatabaseConfig;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.policy.manage.web.model.MonitorMetadataPolicyDTO;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.messaging.MetadataPolicyEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import com.rackspace.salus.telemetry.model.MetadataValueType;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import com.rackspace.salus.telemetry.model.TargetClassName;
import com.rackspace.salus.telemetry.repositories.BoundMonitorRepository;
import com.rackspace.salus.telemetry.repositories.MonitorPolicyRepository;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.validation.ConstraintViolationException;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit4.SpringRunner;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@DataJpaTest(showSql = false)
@Import({ServicesProperties.class, ObjectMapper.class, MonitorManagement.class,
    MonitorContentRenderer.class,
    MonitorContentProperties.class,
    MonitorConversionService.class,
    MetadataUtils.class,
    DatabaseConfig.class})
public class MonitorManagement_MetadataPolicyTest {

  private static final Duration UPDATED_DURATION_VALUE = Duration.ofSeconds(99);
  private static final int UPDATED_COUNT_VALUE = 55;
  private static final String DEFAULT_ENVOY_ID = "env1";
  private static final String DEFAULT_RESOURCE_ID = "os:LINUX";

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @TestConfiguration
  static class TestConfig {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @MockBean
  MonitorEventProducer monitorEventProducer;

  @MockBean
  EnvoyResourceManagement envoyResourceManagement;

  @MockBean
  ZoneStorage zoneStorage;

  @MockBean
  BoundMonitorRepository boundMonitorRepository;

  @MockBean
  MonitorPolicyRepository monitorPolicyRepository;

  @MockBean
  ResourceApi resourceApi;

  @MockBean
  PolicyApi policyApi;

  @MockBean
  ZoneManagement zoneManagement;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  MonitorRepository monitorRepository;

  @Autowired
  private MonitorManagement monitorManagement;

  @Autowired
  EntityManager entityManager;

  @Autowired
  MetadataUtils metadataUtils;

  private PodamFactory podamFactory = new PodamFactoryImpl();

  @Captor
  private ArgumentCaptor<List<BoundMonitor>> captorOfBoundMonitorList;

  @TestConfiguration
  public static class Config {

    @Bean
    public ZonesProperties zonesProperties() {
      return new ZonesProperties();
    }

    @Bean
    public ServicesProperties servicesProperties() {
      return new ServicesProperties()
          .setResourceManagementUrl("");
    }
  }

  @Before
  public void setUp() {
    monitorManagement = Mockito.spy(monitorManagement);

    List<ResourceDTO> resourceList = new ArrayList<>();
    resourceList.add(new ResourceDTO()
        .setResourceId(DEFAULT_RESOURCE_ID)
        .setLabels(Collections.singletonMap("os", "LINUX"))
        .setAssociatedWithEnvoy(true)
    );

    ResourceInfo resourceInfo = new ResourceInfo()
        .setTenantId("abcde")
        .setResourceId(DEFAULT_RESOURCE_ID)
        .setLabels(Collections.singletonMap("os", "LINUX"))
        .setEnvoyId(DEFAULT_ENVOY_ID);

    when(envoyResourceManagement.getOne(anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(resourceInfo));

    when(resourceApi.getResourcesWithLabels(any(), any()))
        .thenReturn(resourceList);

    EnvoyResourcePair pair = new EnvoyResourcePair().setEnvoyId("e-new").setResourceId("r-new-1");

    when(zoneStorage.findLeastLoadedEnvoy(any()))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(pair)));
    when(zoneStorage.incrementBoundCount(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(1));
    when(zoneStorage.decrementBoundCount(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(1));

    when(zoneStorage.getEnvoyIdToResourceIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap("e-new", "r-new-1")));
  }

  @Test
  public void createMonitor_withIntervalPolicy() {
    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any()))
        .thenReturn(Map.of("interval",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("interval")
                .setValue("60")
                .setValueType(MetadataValueType.DURATION)));

    String tenantId = RandomStringUtils.randomAlphabetic(10);
    MonitorCU create = getBaseMonitorCU();

    Monitor monitor = monitorManagement.createMonitor(tenantId, create);

    assertThat(monitor.getInterval(), equalTo(Duration.ofSeconds(60)));
  }

  @Test
  public void createMonitor_intervalPolicyMissing() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    MonitorCU create = getBaseMonitorCU();

    exceptionRule.expect(ConstraintViolationException.class);
    exceptionRule.expectMessage("must not be null");
    monitorManagement.createMonitor(tenantId, create);
    entityManager.flush(); // flush must be called to trigger jpa exceptions in tests
  }

  @Test
  public void testHandleMetadataPolicyEvent_pluginMetadata() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    UUID policyId = UUID.randomUUID();

    // Returns the list of monitor metadata policies effective on this account
    when(policyApi.getEffectiveMonitorMetadataPolicies(tenantId))
        .thenReturn(List.of(
            // The actual RemotePlugin policy that will be used
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setMonitorType(MonitorType.ping)
                .setKey("count")
                .setValue(Integer.toString(UPDATED_COUNT_VALUE))
                .setValueType(MetadataValueType.INT)
                .setTargetClassName(TargetClassName.RemotePlugin)
                .setId(policyId),
            // A similar but irrelevant LocalPlugin policy
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setMonitorType(MonitorType.ping)
                .setKey("count")
                .setValue("677")
                .setValueType(MetadataValueType.INT)
                .setTargetClassName(TargetClassName.LocalPlugin)
                .setId(UUID.randomUUID()),
            // A similar but irrelevant Monitor policy
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setMonitorType(MonitorType.ping)
                .setKey("interval")
                .setValue(Long.toString(UPDATED_DURATION_VALUE.getSeconds()))
                .setValueType(MetadataValueType.DURATION)
                .setTargetClassName(TargetClassName.Monitor)
                .setId(UUID.randomUUID())));


    List<Monitor> savedMonitors = saveAssortmentOfPingMonitors(tenantId);
    List<Monitor> monitorsThatUseMetadata = savedMonitors.stream().filter(
        m -> !m.getPluginMetadataFields().isEmpty())
        .collect(Collectors.toList());
    List<UUID> monitorIds = monitorsThatUseMetadata.stream().map(Monitor::getId).collect(Collectors.toList());

    monitorManagement.handleMetadataPolicyEvent((MetadataPolicyEvent) new MetadataPolicyEvent()
        .setTenantId(tenantId)
        .setPolicyId(policyId));

    List<Monitor> updatedMonitors = monitorManagement.getMonitors(tenantId, Pageable.unpaged()).getContent();

    // The below tests that the "count" was updated where applicable.
    // Interval is not updated even though it is a policy field since this metadata event did not relate to it.

    String expectedPolicyUpdateContent = "{\"type\":\"ping\",\"urls\":[\"localhost\"],\"count\":" + UPDATED_COUNT_VALUE + "}";
    String expectedOriginalContent = "{\"type\":\"ping\",\"urls\":[\"localhost\"],\"count\":72}";

    // monitor 1 had a custom count value, but count was in its pluginMetadata so it should now be
    // using the policy metadata value
    assertThat(updatedMonitors.get(0).getContent(), equalTo(expectedPolicyUpdateContent));
    assertThat(updatedMonitors.get(0).getPluginMetadataFields().contains("count"), equalTo(true));
    assertThat(updatedMonitors.get(0).getInterval(), equalTo(Duration.ofSeconds(0)));
    assertThat(updatedMonitors.get(0).getMonitorMetadataFields().contains("interval"), equalTo(true));
    // monitor 2 had a custom count with no pluginMetadata so should still have that value
    assertThat(updatedMonitors.get(1).getContent(), equalTo(expectedOriginalContent));
    assertThat(updatedMonitors.get(1).getPluginMetadataFields().contains("count"), equalTo(false));
    assertThat(updatedMonitors.get(1).getInterval(), equalTo(Duration.ofSeconds(60)));
    assertThat(updatedMonitors.get(1).getMonitorMetadataFields().contains("interval"), equalTo(false));
    // monitor 3 had the count set to the metadata value and count was in its pluginMetadata
    // so it should still have that value
    assertThat(updatedMonitors.get(2).getContent(), equalTo(expectedPolicyUpdateContent));
    assertThat(updatedMonitors.get(2).getPluginMetadataFields().contains("count"), equalTo(true));
    assertThat(updatedMonitors.get(2).getInterval(), equalTo(UPDATED_DURATION_VALUE));
    assertThat(updatedMonitors.get(2).getMonitorMetadataFields().contains("interval"), equalTo(true));

    // verify the unbind / bind process happened
    verify(monitorManagement).unbindByTenantAndMonitorId(tenantId, monitorIds);
    verify(monitorManagement, times(monitorsThatUseMetadata.size())).bindNewMonitor(eq(tenantId), argThat(m -> {
      assertThat(m, isOneOf(monitorsThatUseMetadata.toArray()));
      return true;
    }));
  }

  /**
   * Same as the above test except it verifies the Monitor interval metadata policy
   * was used, and the "count" policy had no effect.
   */
  @Test
  public void testHandleMetadataPolicyEvent_monitorMetadata() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    UUID policyId = UUID.randomUUID();

    // Returns the list of monitor metadata policies effective on this account
    when(policyApi.getEffectiveMonitorMetadataPolicies(tenantId))
        .thenReturn(List.of(
            // An irrelevant RemotePlugin policy
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setMonitorType(MonitorType.ping)
                .setKey("count")
                .setValue(Integer.toString(UPDATED_COUNT_VALUE))
                .setValueType(MetadataValueType.INT)
                .setTargetClassName(TargetClassName.RemotePlugin)
                .setId(UUID.randomUUID()),
            // An irrelevant LocalPlugin policy
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setMonitorType(MonitorType.ping)
                .setKey("count")
                .setValue("677")
                .setValueType(MetadataValueType.INT)
                .setTargetClassName(TargetClassName.LocalPlugin)
                .setId(UUID.randomUUID()),
            // The actual RemotePlugin policy that will be used
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setMonitorType(MonitorType.ping)
                .setKey("interval")
                .setValue(Long.toString(UPDATED_DURATION_VALUE.getSeconds()))
                .setValueType(MetadataValueType.DURATION)
                .setTargetClassName(TargetClassName.Monitor)
                .setId(policyId)));


    List<Monitor> savedMonitors = saveAssortmentOfPingMonitors(tenantId);
    List<Monitor> monitorsThatUseMetadata = savedMonitors.stream().filter(
        m -> !m.getMonitorMetadataFields().isEmpty())
        .collect(Collectors.toList());
    List<UUID> monitorIds = monitorsThatUseMetadata.stream().map(Monitor::getId).collect(Collectors.toList());

    monitorManagement.handleMetadataPolicyEvent((MetadataPolicyEvent) new MetadataPolicyEvent()
        .setTenantId(tenantId)
        .setPolicyId(policyId));

    List<Monitor> updatedMonitors = monitorManagement.getMonitors(tenantId, Pageable.unpaged()).getContent();

    assertThat(updatedMonitors.get(0).getInterval(), equalTo(UPDATED_DURATION_VALUE));
    assertThat(updatedMonitors.get(0).getMonitorMetadataFields().contains("interval"), equalTo(true));

    assertThat(updatedMonitors.get(1).getInterval(), equalTo(Duration.ofSeconds(60)));
    assertThat(updatedMonitors.get(1).getMonitorMetadataFields().contains("interval"), equalTo(false));

    assertThat(updatedMonitors.get(2).getInterval(), equalTo(UPDATED_DURATION_VALUE));
    assertThat(updatedMonitors.get(2).getMonitorMetadataFields().contains("interval"), equalTo(true));

    // verify the unbind / bind process happened
    verify(monitorManagement).unbindByTenantAndMonitorId(tenantId, monitorIds);
    verify(monitorManagement, times(monitorsThatUseMetadata.size())).bindNewMonitor(eq(tenantId), argThat(m -> {
      assertThat(m, isOneOf(monitorsThatUseMetadata.toArray()));
      return true;
    }));
  }

  private MonitorCU getBaseMonitorCU() {
    return new MonitorCU()
        .setMonitorType(MonitorType.cpu)
        .setAgentType(AgentType.TELEGRAF)
        .setLabelSelector(Collections.emptyMap())
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setMonitorName(RandomStringUtils.randomAlphabetic(10))
        .setContent("{}")
        .setPluginMetadataFields(null)
        .setResourceId(null)
        .setZones(null)
        .setInterval(null);
  }

  private List<Monitor> saveAssortmentOfPingMonitors(String tenantId) {
    List<Monitor> monitors = Arrays.asList(
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("{\"type\": \"ping\", \"urls\": [\"localhost\"]}")
            .setTenantId(tenantId)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(0))
            .setPluginMetadataFields(List.of("count"))
            .setMonitorMetadataFields(List.of("interval")),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("{\"type\":\"ping\",\"urls\":[\"localhost\"],\"count\":72}")
            .setTenantId(tenantId)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60))
            .setPluginMetadataFields(Collections.emptyList())
            .setMonitorMetadataFields(Collections.emptyList()),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("{\"type\":\"ping\",\"urls\":[\"localhost\"],\"count\":" + UPDATED_COUNT_VALUE + "}")
            .setTenantId(tenantId)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(UPDATED_DURATION_VALUE)
            .setPluginMetadataFields(List.of("count"))
            .setMonitorMetadataFields(List.of("interval"))
    );

    List<Monitor> savedMonitors = Lists.newArrayList(monitorRepository.saveAll(monitors));
    entityManager.flush();
    return savedMonitors;
  }
}
