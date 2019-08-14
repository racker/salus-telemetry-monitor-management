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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import com.rackspace.salus.telemetry.repositories.BoundMonitorRepository;
import com.rackspace.salus.telemetry.repositories.MonitorPolicyRepository;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@DataJpaTest(showSql = false)
@Import({ServicesProperties.class, ObjectMapper.class, MonitorManagement.class,
    MonitorContentRenderer.class,
    MonitorContentProperties.class,
    DatabaseConfig.class})
public class MonitorManagementPolicyTest {

  private static final String DEFAULT_RESOURCE_ID = "os:LINUX";

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
  ZoneManagement zoneManagement;

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
        .setLabelSelector(Collections.singletonMap("os", "LINUX"))
        .setContent("content1")
        .setAgentType(AgentType.TELEGRAF);
    currentMonitor = monitorRepository.save(monitor);

    List<ResourceDTO> resourceList = new ArrayList<>();
    resourceList.add(new ResourceDTO()
        .setResourceId(DEFAULT_RESOURCE_ID)
        .setLabels(Collections.singletonMap("os", "LINUX"))
        .setAssociatedWithEnvoy(true)
    );

    when(resourceApi.getResourcesWithLabels(any(), any()))
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
  public void testCreatePolicyMonitor() {
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(null);

    Monitor returned = monitorManagement.createPolicyMonitor(create);

    assertThat(returned.getTenantId(), equalTo(POLICY_TENANT));
    assertThat(returned.getId(), notNullValue());
    assertThat(returned.getMonitorName(), equalTo(create.getMonitorName()));
    assertThat(returned.getContent(), equalTo(create.getContent()));
    assertThat(returned.getAgentType(), equalTo(create.getAgentType()));

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
  public void testRemovePolicyMonitor() {
    final Monitor monitor =
        monitorRepository.save(new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{}")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("z-1"))
            .setLabelSelector(Collections.singletonMap("os", "linux")));

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
            .setLabelSelector(Collections.singletonMap("os", "linux")),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("content1")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
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
    when(resourceApi.getResourcesWithLabels(any(), any()))
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
            .setLabelSelector(Collections.singletonMap("os", "linux")),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("content1")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
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
    when(resourceApi.getResourcesWithLabels(any(), any()))
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
            .setLabelSelector(Collections.singletonMap("os", "linux")),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("content1")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
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
    when(resourceApi.getResourcesWithLabels(any(), any()))
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
            .setLabelSelector(Collections.singletonMap("os", "linux")),
        new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("content1")
            .setTenantId(POLICY_TENANT)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Collections.singletonList("public/z-1"))
            .setLabelSelector(Collections.emptyMap())
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
    when(resourceApi.getResourcesWithLabels(any(), any()))
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
        tenantId, new HashSet<UUID>(Collections.singletonList(savedMonitors.get(1).getId())));

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
}