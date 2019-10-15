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

import static com.rackspace.salus.telemetry.entities.Monitor.POLICY_TENANT;
import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.createPublicZone;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.rackspace.salus.monitor_management.config.DatabaseConfig;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.entities.Resource;
import com.rackspace.salus.telemetry.entities.Zone;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.PolicyMonitorUpdateEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.NotFoundException;
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
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.validation.ConstraintViolationException;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
public class MonitorManagementPolicyTest {

  private static final String DEFAULT_RESOURCE_ID = "os:LINUX";

  @TestConfiguration
  static class TestConfig {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

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
  PatchHelper patchHelper;

  @MockBean
  ZoneManagement zoneManagement;

  @MockBean
  MetadataUtils metadataUtils;

  @Autowired
  EntityManager entityManager;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  MonitorRepository monitorRepository;

  @Autowired
  private MonitorManagement monitorManagement;

  private PodamFactory podamFactory = new PodamFactoryImpl();

  private Monitor currentMonitor;

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
    Monitor monitor = new Monitor()
        .setTenantId(POLICY_TENANT)
        .setMonitorName("policy_mon1")
        .setMonitorType(MonitorType.ping)
        .setLabelSelector(Collections.singletonMap("os", "LINUX"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setContent("content1")
        .setAgentType(AgentType.TELEGRAF)
        .setInterval(Duration.ofSeconds(60));
    currentMonitor = monitorRepository.save(monitor);

    List<ResourceDTO> resourceList = new ArrayList<>();
    resourceList.add(new ResourceDTO()
        .setResourceId(DEFAULT_RESOURCE_ID)
        .setLabels(Collections.singletonMap("os", "LINUX"))
        .setAssociatedWithEnvoy(true)
    );

    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);

    EnvoyResourcePair pair = new EnvoyResourcePair().setEnvoyId("e-new").setResourceId("r-new-1");

    when(zoneStorage.findLeastLoadedEnvoy(any()))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(pair)));
    when(zoneStorage.incrementBoundCount(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(1));

    when(zoneStorage.getEnvoyIdToResourceIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap("e-new", "r-new-1")));

    when(zoneStorage.decrementBoundCount(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(1));
  }

  @Test
  public void testGetPolicyMonitor() {
    Optional<Monitor> m = monitorManagement.getPolicyMonitor(currentMonitor.getId());

    assertTrue(m.isPresent());
    assertThat(m.get().getId(), notNullValue());
    assertThat(m.get().getLabelSelector(), hasEntry("os", "LINUX"));
    assertThat(m.get().getContent(), equalTo(currentMonitor.getContent()));
    assertThat(m.get().getAgentType(), equalTo(currentMonitor.getAgentType()));

    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, monitorEventProducer);
  }

  @Test
  public void testGetAllPolicyMonitor() {
    Random random = new Random();
    int totalMonitors = random.nextInt(150 - 50) + 50;
    int pageSize = 10;

    Pageable page = PageRequest.of(0, pageSize);
    Page<Monitor> result = monitorManagement.getAllPolicyMonitors(page);

    // There is already one monitor created as default
    assertThat(result.getTotalElements(), equalTo(1L));

    // Create a bunch of policy monitors (one less to account for the default one)
    createMonitorsForTenant(totalMonitors - 1, POLICY_TENANT);
    // and a few more account level monitors
    createMonitors(10);

    page = PageRequest.of(0, 10);
    result = monitorManagement.getAllPolicyMonitors(page);

    assertThat(result.getTotalElements(), equalTo((long) totalMonitors));
    assertThat(result.getTotalPages(), equalTo((totalMonitors + pageSize - 1) / pageSize));
  }

  @Test
  public void testGetPolicyMonitorForTenant() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    Monitor monitor = monitorRepository.save(
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("content0")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60)));

    // Use the two saved monitors
    when(policyApi.getEffectivePolicyMonitorIdsForTenant(tenantId))
        .thenReturn(List.of(monitor.getId()));

    Monitor returned = monitorManagement.getPolicyMonitorForTenant(tenantId, monitor.getId());

    assertThat(returned, notNullValue());
    assertThat(returned, equalTo(monitor));

    verify(policyApi).getEffectivePolicyMonitorIdsForTenant(tenantId);
    verifyNoMoreInteractions(policyApi);
  }

  @Test
  public void testGetPolicyMonitorForTenant_doesntExist() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    Monitor monitor = monitorRepository.save(
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("content0")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60)));

    exceptionRule.expect(NotFoundException.class);
    exceptionRule.expectMessage(
        String.format("No policy monitor found for %s on tenant %s", monitor.getId(), tenantId));

    monitorManagement.getPolicyMonitorForTenant(tenantId, monitor.getId());

    verifyNoMoreInteractions(policyApi);
  }

  @Test
  public void testGetPolicyMonitorForTenant_notApplicableToTenant() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    UUID monitorId = UUID.randomUUID();

    // Use the two saved monitors
    when(policyApi.getEffectivePolicyMonitorIdsForTenant(tenantId))
        .thenReturn(Collections.emptyList());

    exceptionRule.expect(NotFoundException.class);
    exceptionRule.expectMessage(
        String.format("No policy monitor found for %s on tenant %s", monitorId, tenantId));

    monitorManagement.getPolicyMonitorForTenant(tenantId, monitorId);

    verifyNoMoreInteractions(policyApi);
  }

  @Test
  public void testGetAllPolicyMonitorsForTenant() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    // save one monitor that isn't used by the tenant
    monitorRepository.save(
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("content0")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60)));

    // save two monitors that are in use by the tenant
    List<Monitor> monitors = Arrays.asList(
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("content0")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60)),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("content1")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60))
    );

    List<Monitor> savedMonitors = Lists.newArrayList(monitorRepository.saveAll(monitors));
    List<UUID> monitorIds = savedMonitors.stream()
        .map(Monitor::getId).collect(Collectors.toList());

    // Use two of the three saved monitors
    when(policyApi.getEffectivePolicyMonitorIdsForTenant(tenantId))
        .thenReturn(monitorIds);

    Page<Monitor> results = monitorManagement.getAllPolicyMonitorsForTenant(tenantId,
        PageRequest.of(0, 10));

    assertThat(results, notNullValue());
    assertThat(results.getTotalElements(), equalTo(2L));
    assertThat(results.getContent(), containsInAnyOrder(monitors.toArray()));

    verify(policyApi).getEffectivePolicyMonitorIdsForTenant(tenantId);
    verifyNoMoreInteractions(policyApi);
  }

  @Test
  public void testCreatePolicyMonitor() {
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setMonitorType(MonitorType.cpu);
    create.setLabelSelectorMethod(LabelSelectorMethod.AND);
    create.setZones(null);
    create.setResourceId(null);

    Monitor returned = monitorManagement.createPolicyMonitor(create);

    assertThat(returned.getTenantId(), equalTo(POLICY_TENANT));
    assertThat(returned.getId(), notNullValue());
    assertThat(returned.getMonitorName(), equalTo(create.getMonitorName()));
    assertThat(returned.getContent(), equalTo(create.getContent()));
    assertThat(returned.getAgentType(), equalTo(create.getAgentType()));
    assertThat(returned.getLabelSelectorMethod(), equalTo(create.getLabelSelectorMethod()));

    assertThat(returned.getLabelSelector().size(), greaterThan(0));
    assertTrue(Maps.difference(create.getLabelSelector(), returned.getLabelSelector()).areEqual());

    Optional<Monitor> retrieved = monitorManagement.getPolicyMonitor(returned.getId());

    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getMonitorName(), equalTo(returned.getMonitorName()));
    assertTrue(Maps.difference(returned.getLabelSelector(), retrieved.get().getLabelSelector())
        .areEqual());

    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, monitorEventProducer);
  }

  @Test
  public void testCreatePolicyMonitor_setResourceId() {
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setLabelSelectorMethod(LabelSelectorMethod.AND);
    create.setZones(null);
    create.setResourceId(RandomStringUtils.randomAlphabetic(10));

    exceptionRule.expect(IllegalArgumentException.class);
    exceptionRule.expectMessage("Policy Monitors must use label selectors and not a resourceId");
    monitorManagement.createPolicyMonitor(create);
  }

  @Test
  public void testUpdatePolicyMonitor() {
    final Monitor monitor =
        monitorRepository.save(new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("original content")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.OR)
            .setInterval(Duration.ofSeconds(60))
            .setMonitorMetadataFields(Collections.emptyList())
            .setPluginMetadataFields(Collections.emptyList()));

    MonitorCU update = new MonitorCU()
        .setContent("new content")
        .setZones(Collections.singletonList("z-2"))
        .setInterval(Duration.ofSeconds(20));

    // make sure the zones we're setting are allowed to be used
    List<Zone> zones = Arrays.asList(
        new Zone().setName("z-1"),
        new Zone().setName("z-2")
    );
    when(zoneManagement.getAvailableZonesForTenant(anyString(), any()))
        .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

    // EXECUTE

    final Monitor updatedMonitor = monitorManagement.updatePolicyMonitor(monitor.getId(), update);

    // VERIFY

    // The returned monitor should match the original with zone and content fields changed
    org.assertj.core.api.Assertions.assertThat(Collections.singleton(updatedMonitor))
        .usingElementComparatorIgnoringFields("createdTimestamp", "updatedTimestamp",
            "monitorMetadataFields", "pluginMetadataFields")
        .containsExactly(
            new Monitor()
                .setId(monitor.getId())
                .setAgentType(AgentType.TELEGRAF)
                .setMonitorType(MonitorType.ping)
                .setContent("new content")
                .setTenantId(POLICY_TENANT)
                .setSelectorScope(ConfigSelectorScope.REMOTE)
                .setLabelSelector(monitor.getLabelSelector())
                .setZones(Collections.singletonList("z-2"))
                .setLabelSelectorMethod(LabelSelectorMethod.OR)
                .setInterval(Duration.ofSeconds(20)));

    // these are returned as PersistentBags so cannot be compared to other lists.
    // rather than including them in the above assert, we just verify they are empty here.
    assertThat(updatedMonitor.getMonitorMetadataFields(), hasSize(0));
    assertThat(updatedMonitor.getPluginMetadataFields(), hasSize(0));

    // Event is sent with no tenant set (to be consumed by policy mgmt)
    verify(monitorEventProducer).sendPolicyMonitorUpdateEvent(
        new PolicyMonitorUpdateEvent()
        .setMonitorId(monitor.getId())
        .setTenantId(null)
    );

    // specified zones were verified
    verify(zoneManagement).getAvailableZonesForTenant(POLICY_TENANT, Pageable.unpaged());

    // No bound monitors will be altered yet.
    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, monitorEventProducer);
  }

  @Test
  public void testUpdatePolicyMonitor_setResourceId() {
    final Monitor monitor =
        monitorRepository.save(new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("original content")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND));

    MonitorCU update = new MonitorCU()
        .setContent("new content")
        .setZones(Collections.singletonList("z-2"))
        .setResourceId(RandomStringUtils.randomAlphabetic(10));

    exceptionRule.expect(IllegalArgumentException.class);
    exceptionRule.expectMessage("Policy Monitors must use label selectors and not a resourceId");
    monitorManagement.updatePolicyMonitor(monitor.getId(), update);
  }

  @Test
  public void testPatchPolicyMonitor_withMetadata() {
    // make sure the zone we're setting is allowed to be used by this tenant
    List<Zone> zones = Collections.singletonList(new Zone().setName("public/z-1"));
    when(zoneManagement.getAvailableZonesForTenant(anyString(), any()))
        .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

    final Monitor monitor =
        monitorRepository.save(new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{\"type\": \"ping\", \"urls\": [\"localhost\"]}")
            .setMonitorType(MonitorType.ping)
            .setTenantId(POLICY_TENANT)
            .setInterval(Duration.ofSeconds(60))
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND));

    // this has to be populated with all fields otherwise they will be considered as null
    MonitorCU update = new MonitorCU()
        .setZones(null)
        .setInterval(null) // setting this should trigger a failure on update
        .setLabelSelectorMethod(monitor.getLabelSelectorMethod())
        .setAgentType(monitor.getAgentType())
        .setMonitorType(monitor.getMonitorType())
        .setContent(monitor.getContent())
        .setLabelSelector(monitor.getLabelSelector())
        .setMonitorName(monitor.getMonitorName())
        .setResourceId(monitor.getResourceId())
        .setPluginMetadataFields(monitor.getPluginMetadataFields());

    // Policy Monitors do not use policy metadata so this should fail validation
    exceptionRule.expect(ConstraintViolationException.class);
    monitorManagement.updatePolicyMonitor(monitor.getId(), update, true);
    entityManager.flush(); // must flush for entity constraint violations to trigger
  }

  @Test
  public void testPatchPolicyMonitor_success() {
    // make sure the zone we're setting is allowed to be used by this tenant
    List<Zone> zones = List.of(new Zone().setName("public/z-1"),
                               new Zone().setName("public/z-2"));
    when(zoneManagement.getAvailableZonesForTenant(anyString(), any()))
        .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

    final Monitor monitor =
        monitorRepository.save(new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{\"type\": \"ping\", \"urls\": [\"localhost\"]}")
            .setMonitorType(MonitorType.ping)
            .setTenantId(POLICY_TENANT)
            .setInterval(Duration.ofSeconds(60))
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND));

    // this has to be populated with all fields otherwise they will be considered as null
    MonitorCU update = new MonitorCU()
        .setZones(List.of("public/z-1", "public/z-2"))
        .setInterval(Duration.ofSeconds(100)) // setting this should trigger a failure on update
        .setLabelSelectorMethod(monitor.getLabelSelectorMethod())
        .setAgentType(monitor.getAgentType())
        .setMonitorType(monitor.getMonitorType())
        .setContent(monitor.getContent())
        .setLabelSelector(monitor.getLabelSelector())
        .setMonitorName(null)
        .setResourceId(monitor.getResourceId())
        .setPluginMetadataFields(monitor.getPluginMetadataFields());

    final Monitor updatedMonitor = monitorManagement.updatePolicyMonitor(monitor.getId(), update, true);
    entityManager.flush(); // must flush for entity constraint violations to trigger

    // new values
    assertThat(updatedMonitor.getZones(), hasSize(2));
    assertThat(updatedMonitor.getZones(), containsInAnyOrder("public/z-1", "public/z-2"));
    assertThat(updatedMonitor.getInterval(), equalTo(Duration.ofSeconds(100)));
    assertThat(updatedMonitor.getMonitorName(), nullValue());

    // no changes
    assertThat(updatedMonitor.getLabelSelector(), equalTo(monitor.getLabelSelector()));
    assertThat(updatedMonitor.getLabelSelectorMethod(), equalTo(monitor.getLabelSelectorMethod()));
    assertThat(updatedMonitor.getContent(), equalTo(monitor.getContent()));
  }

  @Test
  public void testRemovePolicyMonitor() {
    final Monitor monitor =
        monitorRepository.save(new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("{}")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60)));

    // EXECUTE

    monitorManagement.removePolicyMonitor(monitor.getId());

    // VERIFY

    final Optional<Monitor> retrieved = monitorManagement.getPolicyMonitor(monitor.getId());
    assertThat(retrieved.isPresent(), equalTo(false));

    verify(monitorPolicyRepository).existsByMonitorId(monitor.getId());

    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, monitorEventProducer);
  }

  /**
   * This test simulates the adding of two new monitor policies to a single account
   * that did not have any existing bound monitors.
   * For this test, the tenant has two resources relevant to both of those policies.
   */
  @Test
  public void testRefreshBoundPolicyMonitorsForTenant_noExistingBoundMonitors() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    List<Monitor> monitors = Arrays.asList(
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("content0")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("content1")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
    );

    List<Monitor> savedMonitors = Lists.newArrayList(monitorRepository.saveAll(monitors));
    List<UUID> monitorIds = savedMonitors.stream()
        .map(Monitor::getId).collect(Collectors.toList());

    // Use the two saved monitors
    when(policyApi.getEffectivePolicyMonitorIdsForTenant(tenantId))
        .thenReturn(monitorIds);

    // No prior monitors exist so return an empty list
    when(boundMonitorRepository.findAllByTenantIdAndMonitor_TenantId(tenantId, POLICY_TENANT))
        .thenReturn(Collections.emptyList());

    // Define the resources the policy will be applied to
    List<ResourceDTO> resourceList = new ArrayList<>();
    resourceList.add(new ResourceDTO()
        .setTenantId(tenantId)
        .setResourceId(RandomStringUtils.randomAlphabetic(10))
        .setLabels(Collections.singletonMap("os", "linux"))
        .setAssociatedWithEnvoy(true)
    );
    resourceList.add(new ResourceDTO()
        .setTenantId(tenantId)
        .setResourceId(RandomStringUtils.randomAlphabetic(10))
        .setLabels(Collections.emptyMap())
        .setAssociatedWithEnvoy(true)
    );

    // both resources are relevant to the policy
    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);

    // EXECUTE

    monitorManagement.refreshBoundPolicyMonitorsForTenant(tenantId);

    // VERIFY
    List<BoundMonitor> expectedBound = new ArrayList<>();
    for (Monitor monitor : savedMonitors) {
      for (ResourceDTO resource: resourceList) {
        expectedBound.add(
            new BoundMonitor()
                .setZoneName("public/z-1")
                .setMonitor(monitor)
                .setTenantId(tenantId)
                .setResourceId(resource.getResourceId())
                .setEnvoyId("e-new")
                .setRenderedContent(monitor.getContent())
        );
      }
    }

    verify(boundMonitorRepository).findAllByTenantIdAndMonitor_TenantId(tenantId, POLICY_TENANT);
    verify(boundMonitorRepository, times(2)).saveAll(captorOfBoundMonitorList.capture());

    // 2 bound monitors are present in each saveAll request. convert this to a single list for easier asserting.
    List<BoundMonitor> found = captorOfBoundMonitorList.getAllValues().stream()
        .flatMap(List::stream).collect(Collectors.toList());

    org.assertj.core.api.Assertions.assertThat(found)
        .containsExactlyInAnyOrderElementsOf(expectedBound);

    verify(policyApi).getEffectivePolicyMonitorIdsForTenant(tenantId);

    // Verify events were sent out (to be consumed by ambassador)
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-new") // uses default poller envoy defined in setup
    );

    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, monitorEventProducer);
  }

  /**
   * This test simulates the refreshing of monitor policies on a single account
   * that already had those policies in place.
   * For this test, the tenant has two resources relevant to both of those policies.
   */
  @Test
  public void testRefreshBoundPolicyMonitorsForTenant_policyAlreadyConfigured() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    List<Monitor> monitors = Arrays.asList(
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("content0")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("content1")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
    );

    List<Monitor> savedMonitors = Lists.newArrayList(monitorRepository.saveAll(monitors));
    List<UUID> monitorIds = savedMonitors.stream()
        .map(Monitor::getId).collect(Collectors.toList());

    // Use the two saved monitors
    when(policyApi.getEffectivePolicyMonitorIdsForTenant(tenantId))
        .thenReturn(monitorIds);

    // Define the resources the policy will be applied to
    List<ResourceDTO> resourceList = new ArrayList<>();
    resourceList.add(new ResourceDTO()
        .setTenantId(tenantId)
        .setResourceId(RandomStringUtils.randomAlphabetic(10))
        .setLabels(Collections.singletonMap("os", "linux"))
        .setAssociatedWithEnvoy(true)
    );
    resourceList.add(new ResourceDTO()
        .setTenantId(tenantId)
        .setResourceId(RandomStringUtils.randomAlphabetic(10))
        .setLabels(Collections.emptyMap())
        .setAssociatedWithEnvoy(true)
    );

    // both resources are relevant to the policy
    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);

    // define the bound monitors
    List<BoundMonitor> existingBound = new ArrayList<>();
    for (Monitor monitor : savedMonitors) {
      for (ResourceDTO resource: resourceList) {
        existingBound.add(
            new BoundMonitor()
                .setZoneName("public/z-1")
                .setMonitor(monitor)
                .setTenantId(tenantId)
                .setResourceId(resource.getResourceId())
                .setEnvoyId("e-new")
                .setRenderedContent(monitor.getContent())
        );
      }
    }

    // policies are already configured so all bound monitors should be seen
    when(boundMonitorRepository.findAllByTenantIdAndMonitor_TenantId(tenantId, POLICY_TENANT))
        .thenReturn(existingBound);

    // EXECUTE

    monitorManagement.refreshBoundPolicyMonitorsForTenant(tenantId);

    // VERIFY

    verify(boundMonitorRepository).findAllByTenantIdAndMonitor_TenantId(tenantId, POLICY_TENANT);
    verify(policyApi).getEffectivePolicyMonitorIdsForTenant(tenantId);

    // no bound monitor saves or event producer sends should happen.
    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, monitorEventProducer);
  }

  /**
   * This test simulates the adding of one new monitor policies to a single account
   * that already had one other policy applied.
   * For this test, the tenant has two resources relevant to both of those policies.
   */
  @Test
  public void testRefreshBoundPolicyMonitorsForTenant_partiallyExistingBoundMonitors() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    List<Monitor> monitors = Arrays.asList(
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("content0")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("content1")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
    );

    List<Monitor> savedMonitors = Lists.newArrayList(monitorRepository.saveAll(monitors));
    List<UUID> monitorIds = savedMonitors.stream()
        .map(Monitor::getId).collect(Collectors.toList());

    // Use the two saved monitors
    when(policyApi.getEffectivePolicyMonitorIdsForTenant(tenantId))
        .thenReturn(monitorIds);

    // Define the resources the policy will be applied to
    List<ResourceDTO> resourceList = new ArrayList<>();
    resourceList.add(new ResourceDTO()
        .setTenantId(tenantId)
        .setResourceId(RandomStringUtils.randomAlphabetic(10))
        .setLabels(Collections.singletonMap("os", "linux"))
        .setAssociatedWithEnvoy(true)
    );
    resourceList.add(new ResourceDTO()
        .setTenantId(tenantId)
        .setResourceId(RandomStringUtils.randomAlphabetic(10))
        .setLabels(Collections.emptyMap())
        .setAssociatedWithEnvoy(true)
    );
    // both resources are relevant to the policy
    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);

    List<BoundMonitor> existingBound = new ArrayList<>();
    for (ResourceDTO resource : resourceList) {
      existingBound.add(
          new BoundMonitor()
              .setZoneName("public/z-1")
              .setMonitor(savedMonitors.get(0))
              .setTenantId(tenantId)
              .setResourceId(resource.getResourceId())
              .setEnvoyId("e-new")
              .setRenderedContent(savedMonitors.get(0).getContent()));
    }

  List<BoundMonitor> newBound = new ArrayList<>();
    for (ResourceDTO resource : resourceList) {
      newBound.add(
        new BoundMonitor()
            .setZoneName("public/z-1")
            .setMonitor(savedMonitors.get(1))
            .setTenantId(tenantId)
            .setResourceId(resource.getResourceId())
            .setEnvoyId("e-new")
            .setRenderedContent(savedMonitors.get(1).getContent()));
    }

    // No prior monitors exist so return an empty list
    when(boundMonitorRepository.findAllByTenantIdAndMonitor_TenantId(tenantId, POLICY_TENANT))
        .thenReturn(existingBound);

    // EXECUTE

    monitorManagement.refreshBoundPolicyMonitorsForTenant(tenantId);

    // VERIFY

    verify(boundMonitorRepository).findAllByTenantIdAndMonitor_TenantId(tenantId, POLICY_TENANT);
    verify(boundMonitorRepository).saveAll(captorOfBoundMonitorList.capture());

    org.assertj.core.api.Assertions.assertThat(captorOfBoundMonitorList.getValue())
        .containsExactlyInAnyOrderElementsOf(newBound);

    verify(policyApi).getEffectivePolicyMonitorIdsForTenant(tenantId);

    // Verify events were sent out (to be consumed by ambassador)
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-new") // uses default poller envoy defined in setup
    );

    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, monitorEventProducer);
  }

  /**
   * This test simulates the removing of a monitor policy on a single account
   * that already has two policies in place.
   * For this test, the tenant has two resources relevant to both of those policies.
   */
  @Test
  public void testRefreshBoundPolicyMonitorsForTenant_removePolicy() {
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    List<Monitor> monitors = Arrays.asList(
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("content0")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("content1")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
    );

    List<Monitor> savedMonitors = Lists.newArrayList(monitorRepository.saveAll(monitors));

    // Use the first monitor, which means the policy for the second was removed.
    when(policyApi.getEffectivePolicyMonitorIdsForTenant(tenantId))
        .thenReturn(Collections.singletonList(savedMonitors.get(0).getId()));

    // Define the resources the policy will be applied to
    List<ResourceDTO> resourceList = new ArrayList<>();
    resourceList.add(new ResourceDTO()
        .setTenantId(tenantId)
        .setResourceId(RandomStringUtils.randomAlphabetic(10))
        .setLabels(Collections.singletonMap("os", "linux"))
        .setAssociatedWithEnvoy(true)
    );
    resourceList.add(new ResourceDTO()
        .setTenantId(tenantId)
        .setResourceId(RandomStringUtils.randomAlphabetic(10))
        .setLabels(Collections.emptyMap())
        .setAssociatedWithEnvoy(true)
    );
    // both resources are relevant to the policy
    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);

    List<BoundMonitor> existingBound = new ArrayList<>();
    List<BoundMonitor> removedBound = new ArrayList<>();
    for (Monitor monitor : savedMonitors) {
      for (ResourceDTO resource: resourceList) {
        BoundMonitor bound = new BoundMonitor()
            .setZoneName("public/z-1")
            .setMonitor(monitor)
            .setTenantId(tenantId)
            .setResourceId(resource.getResourceId())
            .setEnvoyId("e-new")
            .setRenderedContent(monitor.getContent());

        // all bound monitors exist before the policy removal
        existingBound.add(bound);
        if (!monitor.getId().equals(savedMonitors.get(0).getId())) {
          // but these bound monitors will be removed once it is completed
          removedBound.add(bound);
        }
      }
    }

    // No prior monitors exist so return an empty list
    when(boundMonitorRepository.findAllByTenantIdAndMonitor_TenantId(tenantId, POLICY_TENANT))
        .thenReturn(existingBound);

    //
    when(boundMonitorRepository.findAllByTenantIdAndMonitor_IdIn(anyString(), any()))
        .thenReturn(removedBound);

    // EXECUTE

    monitorManagement.refreshBoundPolicyMonitorsForTenant(tenantId);

    // VERIFY

    verify(boundMonitorRepository).findAllByTenantIdAndMonitor_TenantId(tenantId, POLICY_TENANT);
    verify(boundMonitorRepository).findAllByTenantIdAndMonitor_IdIn(
        tenantId, Set.of(savedMonitors.get(1).getId()));

    verify(policyApi).getEffectivePolicyMonitorIdsForTenant(tenantId);

    verify(boundMonitorRepository).deleteAll(captorOfBoundMonitorList.capture());
    org.assertj.core.api.Assertions.assertThat(captorOfBoundMonitorList.getValue())
        .containsExactlyInAnyOrderElementsOf(removedBound);

    // Verify events were sent out (to be consumed by ambassador)
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-new") // uses default poller envoy defined in setup
    );

    // no bound monitor saves or event producer sends should happen.
    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, monitorEventProducer);
  }

  /**
   * This test ensures that when we process a policy monitor update for an individual tenant
   * that we unbind all the existing monitors related to it, and then rebind them for
   * any resources matching the label selector.
   */
  @Test
  public void testProcessPolicyMonitorUpdate() {
    final String tenantId = RandomStringUtils.randomAlphabetic(10);

    // store the updated monitor in the db
    final Monitor monitor =
        monitorRepository.save(new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("original content")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND));

    // make sure the zone we're setting is allowed to be used by this tenant
    List<Zone> zones = Collections.singletonList(new Zone().setName("public/z-1"));
    when(zoneManagement.getAvailableZonesForTenant(anyString(), any()))
        .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

    // Define the resources the policy will be applied to
    List<ResourceDTO> resourceList = new ArrayList<>();
    resourceList.add(new ResourceDTO()
        .setTenantId(tenantId)
        .setResourceId(RandomStringUtils.randomAlphabetic(10))
        .setLabels(Collections.singletonMap("os", "linux"))
        .setAssociatedWithEnvoy(true)
    );
    resourceList.add(new ResourceDTO()
        .setTenantId(tenantId)
        .setResourceId(RandomStringUtils.randomAlphabetic(10))
        .setLabels(Collections.singletonMap("os", "linux"))
        .setAssociatedWithEnvoy(true)
    );
    // both resources are relevant to the policy
    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);

    // Define the bound monitors that will be removed
    List<BoundMonitor> existingBound = new ArrayList<>();
    for (ResourceDTO resource: resourceList) {
      BoundMonitor bound = new BoundMonitor()
          .setZoneName("public/z-1")
          .setMonitor(monitor)
          .setTenantId(tenantId)
          .setResourceId(resource.getResourceId())
          .setEnvoyId("e-1")
          .setRenderedContent(monitor.getContent());

      existingBound.add(bound);
    }

    // All bound monitors will be found during the update
    when(boundMonitorRepository.findAllByTenantIdAndMonitor_IdIn(anyString(), any()))
        .thenReturn(existingBound);

    // All resources will be returned when rebinding
    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);

    // Various calls involved in finding the envoys to detach/attach to.
    when(zoneStorage.getEnvoyIdToResourceIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap("e-1", "r-1")));
    EnvoyResourcePair pair = new EnvoyResourcePair().setEnvoyId("e-new").setResourceId("r-new-1");
    when(zoneStorage.findLeastLoadedEnvoy(any()))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(pair)));
    when(zoneStorage.incrementBoundCount(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(1));
    when(zoneStorage.decrementBoundCount(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(1));

    // EXECUTE

    monitorManagement.processPolicyMonitorUpdate(tenantId, monitor.getId());

    // VERIFY

    // get all existing bound monitors relatin to the update
    verify(boundMonitorRepository).findAllByTenantIdAndMonitor_IdIn(tenantId,
        Set.of(monitor.getId()));

    // both existing bound monitors were removed
    verify(boundMonitorRepository).deleteAll(captorOfBoundMonitorList.capture());
    org.assertj.core.api.Assertions.assertThat(captorOfBoundMonitorList.getValue())
        .containsExactlyInAnyOrderElementsOf(existingBound);

    // get the envoys/resources for the provided zone and lower their bound count
    verify(zoneStorage).getEnvoyIdToResourceIdMap(createPublicZone(zones.get(0).getName()));
    verify(zoneStorage, times(2)).decrementBoundCount(
        createPublicZone("public/z-1"), "r-1");

    // two new bound monitors should be created using the details of the least loaded envoy
    List<BoundMonitor> expectedBound = new ArrayList<>();
    for (ResourceDTO resource: resourceList) {
      expectedBound.add(
          new BoundMonitor()
              .setZoneName("public/z-1")
              .setMonitor(monitor)
              .setTenantId(tenantId)
              .setResourceId(resource.getResourceId())
              .setEnvoyId("e-new")
              .setRenderedContent(monitor.getContent())
      );
    }

    // operations involved in binding the monitor to the two relevant resources
    verify(resourceApi).getResourcesWithLabels(tenantId, monitor.getLabelSelector(), monitor.getLabelSelectorMethod());
    verify(zoneManagement).getAvailableZonesForTenant(tenantId, Pageable.unpaged());
    verify(boundMonitorRepository).saveAll(captorOfBoundMonitorList.capture());
    org.assertj.core.api.Assertions.assertThat(captorOfBoundMonitorList.getValue())
        .containsExactlyInAnyOrderElementsOf(expectedBound);

    verify(zoneStorage, times(2)).findLeastLoadedEnvoy(createPublicZone("public/z-1"));
    verify(zoneStorage, times(2)).incrementBoundCount(
        createPublicZone("public/z-1"), "r-new-1");

    // Verify events were sent out (to be consumed by ambassador)
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-1")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-new")
    );

    verifyNoMoreInteractions(boundMonitorRepository, monitorPolicyRepository, monitorEventProducer,
        resourceApi, zoneStorage, zoneManagement);
  }

  @Test
  public void testGetPolicyMonitorsForResource_usingAndLabelSelector() {
    List<Monitor> monitors = Lists.newArrayList(monitorRepository.saveAll(List.of(
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("content0")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60)),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("content1")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Map.of("os", "linux", "env", "dev"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60)),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("content2")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Map.of("os", "linux", "env", "dev", "key3", "value3"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60)),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("content3")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Map.of("not", "applicable"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60))
    )));
    Resource resource = new Resource()
        .setTenantId(RandomStringUtils.randomAlphabetic(10))
        .setLabels(Map.of("os", "linux", "env", "dev"));

    when(policyApi.getEffectivePolicyMonitorIdsForTenant(anyString()))
        .thenReturn(monitors
            .stream()
            .map(Monitor::getId)
            .collect(Collectors.toList()));

    List<Monitor> effectiveMonitors = monitorManagement.getPolicyMonitorsForResource(resource);

    // With an AND label selector, we should not apply a monitor that has any labels
    // that do not exist on the resource.
    assertThat(effectiveMonitors, hasSize(2));
    assertThat(effectiveMonitors, containsInAnyOrder(monitors.subList(0,2).toArray()));

    verify(policyApi).getEffectivePolicyMonitorIdsForTenant(resource.getTenantId());
    verifyNoMoreInteractions(policyApi);
  }

  @Test
  public void testGetPolicyMonitorsForResource_usingOrLabelSelector() {
    List<Monitor> monitors = Lists.newArrayList(monitorRepository.saveAll(List.of(
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("content0")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.OR)
            .setInterval(Duration.ofSeconds(60)),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("content1")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Map.of("os", "linux", "env", "dev"))
            .setLabelSelectorMethod(LabelSelectorMethod.OR)
            .setInterval(Duration.ofSeconds(60)),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("content2")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Map.of("os", "linux", "env", "dev", "key3", "value3"))
            .setLabelSelectorMethod(LabelSelectorMethod.OR)
            .setInterval(Duration.ofSeconds(60)),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setContent("content3")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Map.of("not", "applicable"))
            .setLabelSelectorMethod(LabelSelectorMethod.OR)
            .setInterval(Duration.ofSeconds(60))
    )));
    Resource resource = new Resource()
        .setTenantId(RandomStringUtils.randomAlphabetic(10))
        .setLabels(Map.of("os", "linux", "env", "dev"));

    when(policyApi.getEffectivePolicyMonitorIdsForTenant(anyString()))
        .thenReturn(monitors
            .stream()
            .map(Monitor::getId)
            .collect(Collectors.toList()));

    List<Monitor> effectiveMonitors = monitorManagement.getPolicyMonitorsForResource(resource);

    // With an OR label selector, we should not apply a monitor that only contains labels
    // that do not exist on the resource.
    assertThat(effectiveMonitors, hasSize(3));
    assertThat(effectiveMonitors, containsInAnyOrder(monitors.subList(0,3).toArray()));

    verify(policyApi).getEffectivePolicyMonitorIdsForTenant(resource.getTenantId());
    verifyNoMoreInteractions(policyApi);
  }

  private void createMonitors(int count) {
    for (int i = 0; i < count; i++) {
      String tenantId = RandomStringUtils.randomAlphanumeric(10);
      MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
      // limit to local/agent monitors only
      create.setSelectorScope(ConfigSelectorScope.LOCAL);
      create.setZones(Collections.emptyList());
      create.setMonitorType(MonitorType.cpu);
      monitorManagement.createMonitor(tenantId, create);
    }
  }

  private void createMonitorsForTenant(int count, String tenantId) {
    for (int i = 0; i < count; i++) {
      MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
      create.setSelectorScope(ConfigSelectorScope.LOCAL);
      create.setLabelSelectorMethod(LabelSelectorMethod.AND);
      create.setZones(Collections.emptyList());
      create.setMonitorType(MonitorType.cpu);
      monitorManagement.createMonitor(tenantId, create);
    }
  }
}