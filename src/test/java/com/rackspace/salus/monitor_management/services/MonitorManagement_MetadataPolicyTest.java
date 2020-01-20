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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.config.DatabaseConfig;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.services.MonitorManagement_MetadataPolicyTest.TestConfig;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.policy.manage.web.model.MonitorMetadataPolicyDTO;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.entities.Zone;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.messaging.MetadataPolicyEvent;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
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
import javax.persistence.RollbackException;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.util.Lists;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.TransactionSystemException;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureDataJpa
@ContextConfiguration(classes = {
    ObjectMapper.class, MonitorManagement.class,
    MonitorContentRenderer.class,
    MonitorContentProperties.class,
    MonitorConversionService.class,
    MetadataUtils.class,
    DatabaseConfig.class,
    TestConfig.class
})
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

  @MockBean
  PatchHelper patchHelper;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  MonitorRepository monitorRepository;

  @Autowired
  private MonitorManagement monitorManagement;

  @Autowired
  MetadataUtils metadataUtils;

  private PodamFactory podamFactory = new PodamFactoryImpl();

  @Captor
  private ArgumentCaptor<List<BoundMonitor>> captorOfBoundMonitorList;

  @Before
  public void setUp() {
    monitorManagement = Mockito.spy(monitorManagement);

    List<ResourceDTO> resourceList = new ArrayList<>();
    resourceList.add(new ResourceDTO()
        .setTenantId("abcde")
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

    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
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
                .setValue("PT60S")
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

    // RollbackException wraps the ConstraintViolations
    exceptionRule.expect(TransactionSystemException.class);
    exceptionRule.expectCause(IsInstanceOf.instanceOf(RollbackException.class));
    monitorManagement.createMonitor(tenantId, create);
  }

  @Test
  public void testPatchExistingMonitor_allNullValues() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any()))
        .thenReturn(Map.of("zones",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("zones")
                .setValue("public/defaultZone1,public/defaultZone2")
                .setValueType(MetadataValueType.STRING_LIST),
            "interval",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("interval")
                .setValue("PT42S")
                .setValueType(MetadataValueType.DURATION)));

    final Monitor monitor = saveAssortmentOfPingMonitors(tenantId).get(0);

    final BoundMonitor bound1 = new BoundMonitor()
        .setMonitor(monitor)
        .setTenantId(tenantId)
        .setResourceId("r-1")
        .setEnvoyId("e-new")
        .setZoneName("z-1")
        .setRenderedContent("address=something_else");

    when(boundMonitorRepository.findAllByMonitor_Id(monitor.getId()))
        .thenReturn(List.of(bound1));

    // EXECUTE

    // update will contain all null values
    final MonitorCU update = new MonitorCU();
    final Monitor updatedMonitor = monitorManagement.updateMonitor(
        tenantId, monitor.getId(), update, true);

    // VERIFY

    // verify returned entity's field
    assertThat(updatedMonitor.getInterval(), equalTo(Duration.ofSeconds(42)));
    assertThat(updatedMonitor.getMonitorName(), nullValue());
    assertThat(updatedMonitor.getLabelSelectorMethod(), equalTo(LabelSelectorMethod.AND));
    assertThat(updatedMonitor.getZones(), hasSize(2));
    assertThat(updatedMonitor.getZones(), containsInAnyOrder("public/defaultZone1", "public/defaultZone2"));
    // null gets returned when we store the value, but {} gets retrieved when we do a get (as shown farther below)
    // the null does not get exposed via the api
    assertThat(updatedMonitor.getLabelSelector(), nullValue());

    // and verify the stored entity
    Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, updatedMonitor.getId());
    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getInterval(), equalTo(Duration.ofSeconds(42)));
    assertThat(retrieved.get().getZones(), containsInAnyOrder("public/defaultZone1", "public/defaultZone2"));
    // Retrieving nothing/null from an element collection leads to an empty collection being returned
    assertThat(retrieved.get().getLabelSelector(), equalTo(Collections.emptyMap()));

    verify(boundMonitorRepository).findAllByMonitor_Id(monitor.getId());
    // one save when changing zones and one for label selector change
    verify(boundMonitorRepository, times(2)).saveAll(any());

    // only one event is sent
    // since the same envoy is used in the existing bound monitor
    // and in the new binding, it alone will be told to update its config.
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-new")
    );

    verifyNoMoreInteractions(monitorEventProducer);
  }

  @Test
  public void testPatchExistingMonitor_someValueSetSomeValueNull() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any()))
        .thenReturn(Map.of("zones",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("zones")
                .setValue("public/defaultZone1,public/defaultZone2")
                .setValueType(MetadataValueType.STRING_LIST),
            "interval",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("interval")
                .setValue("42")
                .setValueType(MetadataValueType.DURATION)));

    final Monitor monitor = saveAssortmentOfPingMonitors(tenantId).get(0);

    final BoundMonitor bound1 = new BoundMonitor()
        .setMonitor(monitor)
        .setTenantId(tenantId)
        .setResourceId("r-1")
        .setEnvoyId("e-new")
        .setZoneName("z-1")
        .setRenderedContent("address=something_else");

    when(boundMonitorRepository.findAllByMonitor_Id(monitor.getId()))
        .thenReturn(List.of(bound1));

    // EXECUTE

    // update will contain null zones but a new interval
    final MonitorCU update = getBaseMonitorCUFromMonitor(monitor);

    final Duration newInterval = monitor.getInterval().plusSeconds(120L);
    update.setInterval(newInterval);
    update.setZones(null);

    final Monitor updatedMonitor = monitorManagement.updateMonitor(
        tenantId, monitor.getId(), update, true);

    // VERIFY

    // new interval is set using provided value, not metadata
    assertThat(updatedMonitor.getInterval(), equalTo(newInterval));
    assertThat(updatedMonitor.getMonitorName(), nullValue());
    assertThat(updatedMonitor.getLabelSelectorMethod(), equalTo(LabelSelectorMethod.AND));
    assertThat(updatedMonitor.getZones(), hasSize(2));
    // zones are set with metadata policy info
    assertThat(updatedMonitor.getZones(), containsInAnyOrder("public/defaultZone1", "public/defaultZone2"));

    // and verify the stored entity
    Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, updatedMonitor.getId());
    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getInterval(), equalTo(newInterval));
    assertThat(retrieved.get().getZones(), containsInAnyOrder("public/defaultZone1", "public/defaultZone2"));

    verify(boundMonitorRepository).findAllByMonitor_Id(monitor.getId());
    verify(boundMonitorRepository, times(1)).saveAll(any());

    // only one event is sent
    // since the same envoy is used in the existing bound monitor
    // and in the new binding, it alone will be told to update its config.
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-new")
    );

    verifyNoMoreInteractions(monitorEventProducer);
  }

  @Test
  public void testPatchExistingMonitor_allValuesSet() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    // ensure the provided zone is available to this tenant
    when(zoneManagement.getAvailableZonesForTenant(any(), any()))
        .thenReturn(new PageImpl<>(List.of(new Zone().setName("public/newZone1")), Pageable.unpaged(), 1));

    when(policyApi.getEffectiveMonitorMetadataMap(anyString(), any(), any()))
        .thenReturn(Map.of("zones",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("zones")
                .setValue("public/defaultZone1,public/defaultZone2")
                .setValueType(MetadataValueType.STRING_LIST),
            "interval",
            (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
                .setKey("interval")
                .setValue("42")
                .setValueType(MetadataValueType.DURATION)));

    final Monitor monitor = saveAssortmentOfPingMonitors(tenantId).get(0);

    final BoundMonitor bound1 = new BoundMonitor()
        .setMonitor(monitor)
        .setTenantId(tenantId)
        .setResourceId("r-1")
        .setEnvoyId("e-new")
        .setZoneName("z-1")
        .setRenderedContent("address=something_else");

    when(boundMonitorRepository.findAllByMonitor_Id(monitor.getId()))
        .thenReturn(List.of(bound1));

    // EXECUTE

    // update will contain new zones and a new interval
    final MonitorCU update = getBaseMonitorCUFromMonitor(monitor);

    final Duration newInterval = monitor.getInterval().plusSeconds(120L);
    update.setInterval(newInterval);
    update.setZones(List.of("public/newZone1"));

    final Monitor updatedMonitor = monitorManagement.updateMonitor(
        tenantId, monitor.getId(), update, true);

    // VERIFY

    // new interval is set using provided value, not metadata
    assertThat(updatedMonitor.getInterval(), equalTo(newInterval));
    assertThat(updatedMonitor.getMonitorName(), nullValue());
    assertThat(updatedMonitor.getLabelSelectorMethod(), equalTo(LabelSelectorMethod.AND));
    assertThat(updatedMonitor.getZones(), hasSize(1));
    // new zones are set with provided value, not metadata
    assertThat(updatedMonitor.getZones(), contains("public/newZone1"));

    // and verify the stored entity
    Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, updatedMonitor.getId());
    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getInterval(), equalTo(newInterval));
    assertThat(retrieved.get().getZones(), contains("public/newZone1"));

    verify(boundMonitorRepository).findAllByMonitor_Id(monitor.getId());
    verify(boundMonitorRepository, times(1)).saveAll(any());

    // only one event is sent
    // since the same envoy is used in the existing bound monitor
    // and in the new binding, it alone will be told to update its config.
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-new")
    );

    verify(zoneManagement).getAvailableZonesForTenant(eq(tenantId), any());

    verifyNoMoreInteractions(monitorEventProducer, zoneManagement);
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

    String expectedPolicyUpdateContent = "{\"type\":\"ping\",\"target\":\"localhost\",\"count\":" + UPDATED_COUNT_VALUE
        + ",\"pingInterval\":null,\"timeout\":null,\"deadline\":null}";
    // due to content being stored as a string and the details of unmodified monitors are not regenerated,
    // for the purpose of this test all the `null` values seen in te updated monitor are excluded here.
    String expectedOriginalContent = "{\"type\":\"ping\",\"target\":\"localhost\",\"count\":72}";

    // monitor 1 had a custom count value, but count was in its pluginMetadata so it should now be
    // using the policy metadata value
    assertThat(updatedMonitors.get(0).getContent(), equalTo(expectedPolicyUpdateContent));
    assertThat(updatedMonitors.get(0).getInterval(), equalTo(Duration.ofSeconds(0)));
    // monitor 2 had a custom count with no pluginMetadata so should still have that value
    assertThat(updatedMonitors.get(1).getContent(), equalTo(expectedOriginalContent));
    assertThat(updatedMonitors.get(1).getInterval(), equalTo(Duration.ofSeconds(60)));
    // monitor 3 had the count set to the metadata value and count was in its pluginMetadata
    // so it should still have that value
    assertThat(updatedMonitors.get(2).getContent(), equalTo(expectedPolicyUpdateContent));
    assertThat(updatedMonitors.get(2).getInterval(), equalTo(UPDATED_DURATION_VALUE));

    // verify the unbind / bind process happened
    verify(monitorManagement).unbindByTenantAndMonitorId(tenantId, monitorIds);
    verify(monitorManagement, times(monitorsThatUseMetadata.size())).bindNewMonitor(eq(tenantId), argThat(m -> {
      assertThat(m.getId(), isOneOf(monitorIds.toArray()));
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
                .setValue(UPDATED_DURATION_VALUE.toString())
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
    assertThat(updatedMonitors.get(1).getInterval(), equalTo(Duration.ofSeconds(60)));
    assertThat(updatedMonitors.get(2).getInterval(), equalTo(UPDATED_DURATION_VALUE));

    // verify the unbind / bind process happened
    verify(monitorManagement).unbindByTenantAndMonitorId(tenantId, monitorIds);
    verify(monitorManagement, times(monitorsThatUseMetadata.size())).bindNewMonitor(eq(tenantId), argThat(m -> {
      assertThat(m.getId(), isOneOf(monitorIds.toArray()));
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

  private MonitorCU getBaseMonitorCUFromMonitor(Monitor monitor) {
    return new MonitorCU()
        .setResourceId(monitor.getResourceId())
        .setMonitorName(monitor.getMonitorName())
        .setMonitorType(monitor.getMonitorType())
        .setLabelSelector(monitor.getLabelSelector())
        .setLabelSelectorMethod(monitor.getLabelSelectorMethod())
        .setContent(monitor.getContent())
        .setInterval(monitor.getInterval())
        .setAgentType(monitor.getAgentType())
        .setSelectorScope(monitor.getSelectorScope())
        .setZones(monitor.getZones())
        .setPluginMetadataFields(monitor.getPluginMetadataFields());
  }

  private List<Monitor> saveAssortmentOfPingMonitors(String tenantId) {
    List<Monitor> monitors = Arrays.asList(
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("{\"type\": \"ping\", \"target\": \"localhost\"}")
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
            .setContent("{\"type\":\"ping\",\"target\":\"localhost\",\"count\":72}")
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
            .setContent("{\"type\":\"ping\",\"target\":\"localhost\",\"count\":" + UPDATED_COUNT_VALUE + "}")
            .setTenantId(tenantId)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(UPDATED_DURATION_VALUE)
            .setPluginMetadataFields(List.of("count"))
            .setMonitorMetadataFields(List.of("interval"))
    );

    return Lists.newArrayList(monitorRepository.saveAll(monitors));
  }
}
