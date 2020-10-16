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
import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.PUBLIC_PREFIX;
import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.createPrivateZone;
import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.createPublicZone;
import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.resolveZone;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import brave.Tracer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.config.DatabaseConfig;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.ZoneAssignmentCount;
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
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import com.rackspace.salus.telemetry.repositories.BoundMonitorRepository;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import com.rackspace.salus.telemetry.repositories.ResourceRepository;
import com.rackspace.salus.test.EnableTestContainersDatabase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.persistence.EntityManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

/**
 * These unit tests focus on {@link MonitorManagement} operations that interact with the zone
 * binding logic. Unlike {@link MonitorManagementTest} the {@link BoundMonitorRepository} is <b>not
 * mocked</b> in order to verify the named query interactions.
 */
@RunWith(SpringRunner.class)
@EnableTestContainersDatabase
@DataJpaTest(showSql = false)
@TestPropertySource(properties = {
    "salus.services.resourceManagementUrl=http://this-is-a-non-null-value",
    "salus.services.policyManagementUrl=http://this-is-a-non-null-value"
})
@Import({
    ObjectMapper.class,
    MonitorManagement.class,
    MonitorContentRenderer.class,
    MonitorContentProperties.class,
    MetadataUtils.class,
    DatabaseConfig.class,
    ServicesProperties.class,
    ZonesProperties.class,
    SimpleMeterRegistry.class,
    ZoneAllocationResolverFactory.class,
})
public class MonitorManagement_ZoneBindingsTest {

  @MockBean
  MonitorConversionService monitorConversionService;
  @MockBean
  MonitorEventProducer monitorEventProducer;
  @MockBean
  EnvoyResourceManagement envoyResourceManagement;
  @MockBean
  ZoneStorage zoneStorage;
  @MockBean
  ResourceRepository resourceRepository;
  @MockBean
  ResourceApi resourceApi;
  @MockBean
  PolicyApi policyApi;
  @MockBean
  ZoneManagement zoneManagement;
  @MockBean
  PatchHelper patchHelper;
  @MockBean
  ZoneAllocationResolver zoneAllocationResolver;

  @Autowired
  ObjectMapper objectMapper;
  @Autowired
  MonitorRepository monitorRepository;
  @Autowired
  BoundMonitorRepository boundMonitorRepository;
  @Autowired
  EntityManager entityManager;
  @Autowired
  JdbcTemplate jdbcTemplate;
  @Autowired
  MetadataUtils metadataUtils;
  @Autowired
  ZonesProperties zonesProperties;
  @MockBean
  Tracer tracer;

  @Autowired
  private MonitorManagement monitorManagement;
  private final PodamFactory podamFactory = new PodamFactoryImpl();

  @After
  public void tearDown() throws Exception {
    boundMonitorRepository.deleteAll();
    monitorRepository.deleteAll();
  }

  @Test
  public void testDistributeNewMonitor_remote() {
    final ResolvedZone zone1 = createPrivateZone("t-1", "zone1");
    final ResolvedZone zoneWest = createPublicZone("public/west");

    when(zoneAllocationResolver.findLeastLoadedEnvoy(zone1))
        .thenReturn(
            Optional.of(
                new EnvoyResourcePair().setEnvoyId("zone1-e-1").setResourceId("r-e-1"))
        );
    when(zoneAllocationResolver.findLeastLoadedEnvoy(zoneWest))
        .thenReturn(
            Optional.of(new EnvoyResourcePair().setEnvoyId("zoneWest-e-2").setResourceId("r-e-2"))
        );

    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(List.of(
            new ResourceDTO().setResourceId("r-1")
                .setTenantId("t-1")
                .setLabels(Collections.singletonMap("os", "LINUX"))
                .setMetadata(Collections.singletonMap("public_ip", "151.1.1.1")),
            new ResourceDTO().setResourceId("r-2")
                .setTenantId("t-1")
                .setLabels(Collections.singletonMap("os", "LINUX"))
                .setMetadata(Collections.singletonMap("public_ip", "151.2.2.2"))
        ));

    final Monitor monitor = monitorRepository.save(
        new Monitor()
            .setTenantId("t-1")
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.http)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setInterval(Duration.ofSeconds(60))
            .setLabelSelector(Collections.singletonMap("os", "LINUX"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setZones(Arrays.asList("zone1", "public/west"))
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{\"type\": \"ping\", \"urls\": [\"${resource.metadata.public_ip}\"]}")
    );

    // EXECUTE
    final Set<String> affectedEnvoys = monitorManagement
        .bindMonitor("t-1", monitor, monitor.getZones());

    // VERIFY
    assertBindings(
        monitor.getId(),
        new BoundMonitorMatcher(monitor, "zone1", "r-1", "zone1-e-1", "r-e-1"),
        new BoundMonitorMatcher(monitor, "public/west", "r-1", "zoneWest-e-2", "r-e-2"),
        new BoundMonitorMatcher(monitor, "zone1", "r-2", "zone1-e-1", "r-e-1"),
        new BoundMonitorMatcher(monitor, "public/west", "r-2", "zoneWest-e-2", "r-e-2")
    );

    assertThat(affectedEnvoys, containsInAnyOrder("zone1-e-1", "zoneWest-e-2"));

    verify(zoneAllocationResolver, times(2)).findLeastLoadedEnvoy(zone1);
    verify(zoneAllocationResolver, times(2)).findLeastLoadedEnvoy(zoneWest);

    verifyNoMoreInteractions(zoneStorage, monitorEventProducer, zoneAllocationResolver);
  }

  @Test
  public void testDistributeNewMonitor_remote_emptyZone() {
    final String tenantId = RandomStringUtils.randomAlphanumeric(10);
    final ResolvedZone zone1 = createPrivateZone(tenantId, "zone1");

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(
            Optional.empty()
        );

    Monitor monitor = persistNewMonitor(tenantId, "zone1");

    ResourceInfo resourceInfo = new ResourceInfo()
        .setTenantId("abcde")
        .setResourceId("r-1")
        .setLabels(Collections.singletonMap("os", "LINUX"))
        .setEnvoyId("e-1");

    when(envoyResourceManagement.getOne(anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(resourceInfo));

    List<ResourceDTO> resourceList = new ArrayList<>();
    resourceList.add(new ResourceDTO()
        .setResourceId("r-1")
        .setLabels(resourceInfo.getLabels())
        .setAssociatedWithEnvoy(true)
        .setTenantId(tenantId)
        .setEnvoyId("e-1")
    );

    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);

    // EXECUTE
    final Set<String> affectedEnvoys = monitorManagement
        .bindMonitor(tenantId, monitor, monitor.getZones());

    // VERIFY
    verify(zoneAllocationResolver).findLeastLoadedEnvoy(zone1);

    // Verify the envoy ID was NOT be set for this
    assertBindings(
        monitor.getId(),
        new BoundMonitorMatcher(monitor, "zone1", "r-1", null, null)
    );

    assertThat(affectedEnvoys, hasSize(0));

    // ...and no MonitorBoundEvent was sent
    verifyNoMoreInteractions(zoneStorage, monitorEventProducer, zoneAllocationResolver);
  }

  @Test
  public void testHandleNewEnvoyInZone_privateZone() {
    final String tenantId = RandomStringUtils.randomAlphanumeric(10);
    final Monitor monitor = persistNewMonitor(tenantId, "z-1");
    // simulate that three in zone are needing envoys
    persistBoundMonitor("r-1", "z-1", null, "poller-0", monitor);
    persistBoundMonitor("r-2", "z-1", null, "poller-0", monitor);
    persistBoundMonitor("r-3", "z-1", null, "poller-0", monitor);

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(
            Optional.of(new EnvoyResourcePair().setEnvoyId("e-1").setResourceId("r-e-1")));

    // EXECUTE

    monitorManagement.handleNewEnvoyInZone(tenantId, "z-1");

    // VERIFY

    verify(zoneAllocationResolver, times(3)).findLeastLoadedEnvoy(
        createPrivateZone(tenantId, "z-1")
    );

    // two assignments to same envoy, but verify only one event
    verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
        .setEnvoyId("e-1"));

    assertBindings(
        monitor.getId(),
        new BoundMonitorMatcher(monitor, "z-1", "r-1", "e-1", "r-e-1"),
        new BoundMonitorMatcher(monitor, "z-1", "r-2", "e-1", "r-e-1"),
        new BoundMonitorMatcher(monitor, "z-1", "r-3", "e-1", "r-e-1")
    );

    verifyNoMoreInteractions(zoneStorage, monitorEventProducer, zoneAllocationResolver);
  }

  @Test
  public void testHandleNewEnvoyInZone_publicZone() {
    final String tenantId = RandomStringUtils.randomAlphanumeric(10);
    final Monitor monitor = persistNewMonitor(
        tenantId, "public/west");
    // simulate that three in zone are needing envoys
    persistBoundMonitor("r-1", "public/west", null, "poller-0", monitor);
    persistBoundMonitor("r-2", "public/west", null, "poller-0", monitor);
    persistBoundMonitor("r-3", "public/west", null, "poller-0", monitor);

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(
            Optional.of(new EnvoyResourcePair().setEnvoyId("e-1").setResourceId("r-e-1")));

    // EXECUTE

    // Main difference from testHandleNewEnvoyInZone_privateZone is that the
    // tenantId is null from the event

    monitorManagement.handleNewEnvoyInZone(null, "public/west");

    // VERIFY

    verify(zoneAllocationResolver, times(3)).findLeastLoadedEnvoy(
        createPublicZone("public/west")
    );

    // two assignments to same envoy, but verify only one event
    verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
        .setEnvoyId("e-1"));

    assertBindings(
        monitor.getId(),
        new BoundMonitorMatcher(monitor, "public/west", "r-1", "e-1", "r-e-1"),
        new BoundMonitorMatcher(monitor, "public/west", "r-2", "e-1", "r-e-1"),
        new BoundMonitorMatcher(monitor, "public/west", "r-3", "e-1", "r-e-1")
    );

    verifyNoMoreInteractions(zoneStorage, monitorEventProducer, zoneAllocationResolver);
  }

  @Test
  public void testHandleExpiredEnvoy_privateZone() {
    final String tenantId = RandomStringUtils.randomAlphanumeric(10);
    final Monitor monitor = persistNewMonitor(tenantId, "z-1");
    // simulate that three in zone are needing envoys
    persistBoundMonitor("r-3", "z-1", "e-old", "poller-old", monitor);
    persistBoundMonitor("r-1", "z-1", "e-old", "poller-old", monitor);
    persistBoundMonitor("r-2", "z-1", "e-old", "poller-old", monitor);

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(
            Optional.of(new EnvoyResourcePair().setEnvoyId("e-new").setResourceId("poller-new")));

    // EXECUTE

    monitorManagement.handleExpiredEnvoy(tenantId, "z-1", "e-old");

    // VERIFY

    verify(zoneAllocationResolver, times(3)).findLeastLoadedEnvoy(
        createPrivateZone(tenantId, "z-1")
    );

    // two assignments to same envoy, but verify only one event
    verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
        .setEnvoyId("e-new"));

    assertBindings(
        monitor.getId(),
        new BoundMonitorMatcher(monitor, "z-1", "r-1", "e-new", "poller-new"),
        new BoundMonitorMatcher(monitor, "z-1", "r-2", "e-new", "poller-new"),
        new BoundMonitorMatcher(monitor, "z-1", "r-3", "e-new", "poller-new")
    );

    verifyNoMoreInteractions(zoneStorage, monitorEventProducer, zoneAllocationResolver);
  }

  @Test
  public void testHandleExpiredEnvoy_publicZone() {
    final String tenantId = RandomStringUtils.randomAlphanumeric(10);
    final Monitor monitor = persistNewMonitor(
        tenantId, "public/west");
    // simulate that three in zone are needing envoys
    persistBoundMonitor("r-1", "public/west", "e-old", "poller-old", monitor);
    persistBoundMonitor("r-2", "public/west", "e-old", "poller-old", monitor);
    persistBoundMonitor("r-3", "public/west", "e-old", "poller-old", monitor);

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(
            // new envoy, but same resourceId assigned
            Optional.of(new EnvoyResourcePair().setEnvoyId("e-new").setResourceId("poller-new")));

    // EXECUTE

    // Main difference from testHandleNewEnvoyInZone_privateZone is that the
    // tenantId is null from the event

    monitorManagement.handleExpiredEnvoy(null, "public/west", "e-old");

    // VERIFY

    verify(zoneAllocationResolver, times(3)).findLeastLoadedEnvoy(
        createPublicZone("public/west")
    );

    // two assignments to same envoy, but verify only one event
    verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
        .setEnvoyId("e-new"));

    assertBindings(
        monitor.getId(),
        new BoundMonitorMatcher(monitor, "public/west", "r-1", "e-new", "poller-new"),
        new BoundMonitorMatcher(monitor, "public/west", "r-2", "e-new", "poller-new"),
        new BoundMonitorMatcher(monitor, "public/west", "r-3", "e-new", "poller-new")
    );

    verifyNoMoreInteractions(zoneStorage, monitorEventProducer, zoneAllocationResolver);
  }

  @Test
  public void testHandleZoneResourceChanged_privateZone() {
    final String tenantId = RandomStringUtils.randomAlphanumeric(10);
    final Monitor monitor = persistNewMonitor(tenantId, "z-1");
    persistBoundMonitor("r-1", "z-1", "e-1", "poller-0", monitor);
    persistBoundMonitor("r-2", "z-1", "e-1", "poller-0", monitor);
    persistBoundMonitor("r-3", "z-1", "e-1", "poller-0", monitor);

    // EXECUTE

    monitorManagement.handleEnvoyResourceChangedInZone(
        tenantId, "z-1", "poller-new", "e-1", "e-new");

    // VERIFY

    assertBindings(
        monitor.getId(),
        new BoundMonitorMatcher(monitor, "z-1", "r-1", "e-new", "poller-new"),
        new BoundMonitorMatcher(monitor, "z-1", "r-2", "e-new", "poller-new"),
        new BoundMonitorMatcher(monitor, "z-1", "r-3", "e-new", "poller-new")
    );

    verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
        .setEnvoyId("e-new"));
    // NOTE: e-1 doesn't get notified since handleEnvoyResourceChangedInZone knows/assumes that one went away

    verifyNoMoreInteractions(zoneStorage, monitorEventProducer, zoneAllocationResolver);
  }

  @Test
  public void testHandleZoneResourceChanged_publicZone() {
    final String tenantId = RandomStringUtils.randomAlphanumeric(10);
    final Monitor monitor = persistNewMonitor(tenantId, "public/1");
    persistBoundMonitor("r-1", "public/1", "e-1", "poller-0", monitor);
    persistBoundMonitor("r-2", "public/1", "e-1", "poller-0", monitor);
    persistBoundMonitor("r-3", "public/1", "e-1", "poller-0", monitor);

    // EXECUTE

    // The main thing being tested is that a null zone tenant ID...
    monitorManagement.handleEnvoyResourceChangedInZone(
        null, "public/1", "poller-1", "e-1", "e-2");

    // VERIFY

    verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
        .setEnvoyId("e-2"));

    assertBindings(
        monitor.getId(),
        new BoundMonitorMatcher(
            monitor, "public/1", "r-1", "e-2", "poller-1"),
        new BoundMonitorMatcher(
            monitor, "public/1", "r-2", "e-2", "poller-1"),
        new BoundMonitorMatcher(
            monitor, "public/1", "r-3", "e-2", "poller-1")
    );

    verifyNoMoreInteractions(zoneStorage, monitorEventProducer, zoneAllocationResolver);
  }

  /**
   * This does the same as above except also verifies bound monitors not assigned to any envoy will
   * get picked up by a pre-existing envoy reconnecting.
   */
  @Test
  public void testHandleZoneResourceChanged_publicZone_unassignedMonitors() {
    final String tenantId = RandomStringUtils.randomAlphanumeric(10);
    final Monitor monitor = persistNewMonitor(tenantId, "public/1");
    // set up bound monitors that were previously bound to this envoy
    persistBoundMonitor("r-1", "public/1", "e-1", "poller-0", monitor);
    persistBoundMonitor("r-2", "public/1", "e-1", "poller-0", monitor);
    persistBoundMonitor("r-3", "public/1", "e-1", "poller-0", monitor);

    // set up bound monitors that have never been assigned to an envoy
    // using 4 to make clear where the numbers in the asserts below came from
    persistBoundMonitor("r-4", "public/1", null, null, monitor);
    persistBoundMonitor("r-5", "public/1", null, null, monitor);
    persistBoundMonitor("r-6", "public/1", null, null, monitor);
    persistBoundMonitor("r-7", "public/1", null, null, monitor);

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(
            Optional.of(new EnvoyResourcePair()
                .setEnvoyId("e-2")
                .setResourceId("poller-1")));

    // EXECUTE

    monitorManagement.handleEnvoyResourceChangedInZone(
        null, "public/1", "poller-1", "e-1", "e-2");

    // VERIFY

    assertBindings(
        monitor.getId(),
        new BoundMonitorMatcher(
            monitor, "public/1", "r-1", "e-2", "poller-1"),
        new BoundMonitorMatcher(
            monitor, "public/1", "r-2", "e-2", "poller-1"),
        new BoundMonitorMatcher(
            monitor, "public/1", "r-3", "e-2", "poller-1"),
        new BoundMonitorMatcher(
            monitor, "public/1", "r-4", "e-2", "poller-1"),
        new BoundMonitorMatcher(
            monitor, "public/1", "r-5", "e-2", "poller-1"),
        new BoundMonitorMatcher(
            monitor, "public/1", "r-6", "e-2", "poller-1"),
        new BoundMonitorMatcher(
            monitor, "public/1", "r-7", "e-2", "poller-1")
    );

    // then verify that it picks up the other unbound monitors
    verify(zoneAllocationResolver, times(4)).findLeastLoadedEnvoy(
        createPublicZone("public/1"));

    // one event for preexisting and one for unassigned monitors
    verify(monitorEventProducer, times(2)).sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId("e-2")
    );

    verifyNoMoreInteractions(zoneStorage, monitorEventProducer, zoneAllocationResolver);
  }

  @Test
  public void testGetZoneAssignmentCounts() {
    Map<EnvoyResourcePair, Integer> rawCounts = new HashMap<>();
    rawCounts.put(new EnvoyResourcePair().setResourceId("r-1").setEnvoyId("e-1"), 5);
    rawCounts.put(new EnvoyResourcePair().setResourceId("r-2").setEnvoyId("e-2"), 6);

    when(zoneAllocationResolver.getZoneBindingCounts(any()))
        .thenReturn(CompletableFuture.completedFuture(rawCounts));

    // EXECUTE

    final List<ZoneAssignmentCount> counts =
        monitorManagement.getZoneAssignmentCounts("t-1", "z-1").join();

    // VERIFY

    assertThat(counts, containsInAnyOrder(
        new ZoneAssignmentCount().setResourceId("r-1").setEnvoyId("e-1").setAssignments(5),
        new ZoneAssignmentCount().setResourceId("r-2").setEnvoyId("e-2").setAssignments(6)

    ));

    verify(zoneAllocationResolver).getZoneBindingCounts(
        ResolvedZone.createPrivateZone("t-1", "z-1")
    );

    verifyNoMoreInteractions(envoyResourceManagement,
        zoneStorage, monitorEventProducer, resourceApi, zoneAllocationResolver
    );
  }

  @Test
  public void testRebalanceZone_privateZone() {
    zonesProperties.setRebalanceStandardDeviations(1);
    zonesProperties.setRebalanceEvaluateZeroes(false);

    final String tenantId = RandomStringUtils.randomAlphanumeric(10);
    final Monitor monitor = persistNewMonitor(tenantId, "z-1");
    Map<EnvoyResourcePair, Integer> counts = persistBindingsToRebalance(monitor, "z-1");

    when(zoneAllocationResolver.getZoneBindingCounts(any()))
        .thenReturn(CompletableFuture.completedFuture(counts));

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(Optional.of(
            new EnvoyResourcePair().setResourceId("r-least").setEnvoyId("e-least")
        ));

    // EXECUTE

    monitorManagement.rebalanceZone(tenantId, "z-1").join();

    // VERIFY

    final ResolvedZone zone = createPrivateZone(tenantId, "z-1");
    verify(zoneAllocationResolver).getZoneBindingCounts(zone);

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-3")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-least")
    );

    verify(zoneAllocationResolver, times(2)).findLeastLoadedEnvoy(zone);

    verifyNoMoreInteractions(envoyResourceManagement,
        zoneStorage, monitorEventProducer, resourceApi, zoneAllocationResolver
    );
  }

  @Test
  public void testRebalanceZone_publicZone() {
    zonesProperties.setRebalanceStandardDeviations(1);
    zonesProperties.setRebalanceEvaluateZeroes(false);

    final Monitor monitor = persistNewMonitor(
        RandomStringUtils.randomAlphanumeric(10), "public/west");
    Map<EnvoyResourcePair, Integer> counts = persistBindingsToRebalance(
        monitor, "public/west");

    when(zoneAllocationResolver.getZoneBindingCounts(any()))
        .thenReturn(CompletableFuture.completedFuture(counts));

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(Optional.of(
            new EnvoyResourcePair().setResourceId("r-least").setEnvoyId("e-least")
        ));

    // EXECUTE

    final Integer reassigned =
        monitorManagement.rebalanceZone(null, "public/west").join();

    // VERIFY

    assertThat(reassigned, equalTo(2));

    final ResolvedZone zone = createPublicZone("public/west");
    verify(zoneAllocationResolver).getZoneBindingCounts(zone);

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-3")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-least")
    );

    verify(zoneAllocationResolver, times(2)).findLeastLoadedEnvoy(zone);

    verifyNoMoreInteractions(envoyResourceManagement,
        zoneStorage, monitorEventProducer, resourceApi, zoneAllocationResolver
    );
  }

  @Test
  public void testRebalanceZone_includeZeroes() {
    zonesProperties.setRebalanceStandardDeviations(1);
    // INCLUDE zero-count assignments
    zonesProperties.setRebalanceEvaluateZeroes(true);

    final Monitor monitor = persistNewMonitor(
        RandomStringUtils.randomAlphanumeric(10), "public/west");
    final Map<EnvoyResourcePair, Integer> counts = persistBindingsToRebalance(
        monitor, "public/west");

    when(zoneAllocationResolver.getZoneBindingCounts(any()))
        .thenReturn(CompletableFuture.completedFuture(counts));

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(Optional.of(
            new EnvoyResourcePair().setResourceId("r-least").setEnvoyId("e-least")
        ));

    // EXECUTE

    final Integer reassigned =
        monitorManagement.rebalanceZone(null, "public/west").join();

    // VERIFY

    assertThat(reassigned, equalTo(3));

    final ResolvedZone zone = createPublicZone("public/west");
    verify(zoneAllocationResolver).getZoneBindingCounts(zone);

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-3")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-least")
    );

    verify(zoneAllocationResolver, times(3)).findLeastLoadedEnvoy(zone);

    verifyNoMoreInteractions(
        envoyResourceManagement, zoneStorage, monitorEventProducer, resourceApi,
        zoneAllocationResolver);
  }

  @Test
  public void testRebalanceZone_emptyZone() {
    when(zoneAllocationResolver.getZoneBindingCounts(any()))
        .thenReturn(CompletableFuture.completedFuture(
            Collections.emptyMap()
        ));

    // EXECUTE

    final Integer reassigned =
        monitorManagement.rebalanceZone("t-1", "z-1").join();

    // VERIFY

    assertThat(reassigned, equalTo(0));

    verify(zoneAllocationResolver).getZoneBindingCounts(ResolvedZone.createPrivateZone("t-1", "z-1"));

    verifyNoMoreInteractions(envoyResourceManagement,
        zoneStorage, monitorEventProducer, resourceApi, zoneAllocationResolver
    );
  }

  @Test
  public void testRemoveMonitor_privateZone() {
    final String tenantId = RandomStringUtils.randomAlphanumeric(10);
    final Monitor monitor = persistNewMonitor(tenantId, "z-1");

    persistBoundMonitor("r-1", "z-1", "e-goner", "poller-0", monitor);

    // EXECUTE

    monitorManagement.removeMonitor(tenantId, monitor.getId());

    // VERIFY

    final Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, monitor.getId());
    assertThat(retrieved.isPresent(), equalTo(false));

    // assert no bindings remain for given monitor
    assertBindings(monitor.getId());

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId("e-goner")
    );

    verifyNoMoreInteractions(zoneStorage, monitorEventProducer, zoneAllocationResolver);
  }

  @Test
  public void testRemoveMonitor_publicZone() {
    String zoneName = PUBLIC_PREFIX + "z-1";
    final String tenantId = RandomStringUtils.randomAlphanumeric(10);
    final Monitor monitor = persistNewMonitor(tenantId, zoneName);

    persistBoundMonitor("r-1", zoneName, "e-goner", "poller-0", monitor);

    // EXECUTE

    monitorManagement.removeMonitor(tenantId, monitor.getId());

    // VERIFY

    final Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, monitor.getId());
    assertThat(retrieved.isPresent(), equalTo(false));

    // assert no bindings remain for given monitor
    assertBindings(monitor.getId());

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId("e-goner")
    );

    verifyNoMoreInteractions(zoneStorage, monitorEventProducer, zoneAllocationResolver);
  }

  @Test
  public void testUnbindByMonitorId_remote() {
    final String tenantId = RandomStringUtils.randomAlphanumeric(10);
    final Monitor monitor1 = persistNewMonitor(tenantId, "z-1");
    final Monitor monitor2 = persistNewMonitor(tenantId, "z-1");

    persistBoundMonitor("r-0", "z-1", "e-1", "poller-0", monitor1);
    persistBoundMonitor("r-0", "z-1", "e-2", "poller-0", monitor2);

    // EXECUTE

    final Set<String> affectedEnvoys = monitorManagement
        .unbindByTenantAndMonitorId(tenantId, Collections.singletonList(monitor1.getId()));

    // VERIFY

    assertThat(affectedEnvoys, contains("e-1"));

    assertBindings(
        monitor2.getId(),
        new BoundMonitorMatcher(monitor2, "z-1", "r-0", "e-2", "poller-0")
    );

    verifyNoMoreInteractions(zoneStorage, zoneAllocationResolver);
  }

  @Test
  public void testUpdateExistingMonitor_zonesChanged() {
    final String tenantId = RandomStringUtils.randomAlphanumeric(10);
    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(Collections.singletonList(
            new ResourceDTO()
                .setTenantId(tenantId)
                .setResourceId("r-1")
        ));

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(Optional.of(
            new EnvoyResourcePair().setEnvoyId("e-new").setResourceId("poller-new")
        ));

    final Monitor monitor = persistNewMonitor(
        tenantId, Map.of("os", "linux"), List.of("z-1", "z-2"));

    persistBoundMonitor("r-1", "z-1", "e-z-1-existing", "poller-z-1-0", monitor);
    persistBoundMonitor("r-1", "z-2", "e-z-2-existing", "poller-z-2-0", monitor);

    List<Zone> zones = List.of(
        new Zone().setName("z-1"),
        new Zone().setName("z-2"),
        new Zone().setName("z-3")
    );

    when(zoneManagement.getAvailableZonesForTenant(any(), any()))
        .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

    // EXECUTE

    final MonitorCU update = new MonitorCU()
        .setZones(List.of("z-2", "z-3"));

    final Monitor updatedMonitor = monitorManagement
        .updateMonitor(tenantId, monitor.getId(), update);

    // VERIFY

    assertThat(
        List.copyOf(updatedMonitor.getZones()), // persistent bag -> list
        equalTo(Arrays.asList("z-2", "z-3"))
    );

    verify(resourceApi).getResourcesWithLabels(tenantId, Collections.singletonMap("os", "linux"),
        LabelSelectorMethod.AND
    );

    final ResolvedZone resolvedZ3 = createPrivateZone(tenantId, "z-3");
    verify(zoneAllocationResolver).findLeastLoadedEnvoy(resolvedZ3);

    assertBindings(
        monitor.getId(),
        new BoundMonitorMatcher(monitor, "z-2", "r-1", "e-z-2-existing", "poller-z-2-0"),
        new BoundMonitorMatcher(monitor, "z-3", "r-1", "e-new", "poller-new")
    );

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-z-1-existing")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-new")
    );

    verify(zoneManagement).getAvailableZonesForTenant(tenantId, Pageable.unpaged());

    verifyNoMoreInteractions(envoyResourceManagement, resourceApi,
        zoneStorage, monitorEventProducer, zoneManagement, zoneAllocationResolver
    );
  }

  @Test
  public void testUpdateExistingMonitor_zonesOnlyChangedOrder() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    final Monitor monitor = persistNewMonitor(
        tenantId, Map.of("os", "linux"), List.of("z-1", "z-2"));

    List<Zone> zones = List.of(
        new Zone().setName("z-1"),
        new Zone().setName("z-2")
    );
    when(zoneManagement.getAvailableZonesForTenant(any(), any()))
        .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

    // EXECUTE

    final MonitorCU update = new MonitorCU()
        .setZones(List.of("z-2", "z-1"));

    final Monitor updatedMonitor = monitorManagement
        .updateMonitor(tenantId, monitor.getId(), update);

    // VERIFY

    assertThat(
        List.copyOf(updatedMonitor.getZones()), // persistent bag -> list
        equalTo(Arrays.asList("z-1", "z-2"))
    );

    verify(zoneManagement).getAvailableZonesForTenant(tenantId, Pageable.unpaged());

    verifyNoMoreInteractions(envoyResourceManagement, resourceApi,
        zoneStorage, monitorEventProducer, zoneManagement, zoneAllocationResolver
    );
  }

  /**
   * Tests the case where a remote monitor was previously configured with an empty list of zones and
   * is now updated to have the zones explictly set.
   *
   * When a monitor is created with no zones, they will be discovered when binding to each resource
   * as they may vary for each resource.
   *
   * In this case there are two resources each using different zones. The updating of the monitor to
   * a completely different zone will lead to the previous bound monitors being removed from the old
   * zones and two new ones being created on the single zone specified on the monitor.
   */
  @Test
  public void testHandleZoneChangePerResource_oldZonesEmpty() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    // when the old zones are empty the bound monitor zone is based on the policy values.
    // for this test we use this one zone to represent that.
    String originalZoneForResource = "public/originalZone";

    String oldEnvoy1 = RandomStringUtils.randomAlphabetic(5);
    String oldEnvoy2 = RandomStringUtils.randomAlphabetic(5);
    String newEnvoy = RandomStringUtils.randomAlphabetic(5);

    // the updated monitor has zones explicitly set
    Monitor monitor = persistNewMonitor(tenantId, "public/newZone");

    final List<Resource> resources = List.of(
        podamFactory.manufacturePojo(Resource.class)
            .setResourceId("r-1")
            .setTenantId(tenantId),
        podamFactory.manufacturePojo(Resource.class)
            .setResourceId("r-2")
            .setTenantId(tenantId)
    );

    final String resourceId0 = resources.get(0).getResourceId();
    final String resourceId1 = resources.get(1).getResourceId();

    persistBoundMonitor(resourceId0, originalZoneForResource, oldEnvoy1, "poller-0", monitor);
    persistBoundMonitor(resourceId1, originalZoneForResource, oldEnvoy2, "poller-0", monitor);

    // DISCOVERY OPERATIONS

    when(resourceRepository.findByTenantIdAndResourceId(anyString(), anyString()))
        .thenReturn(Optional.of(resources.get(0)))
        .thenReturn(Optional.of(resources.get(1)));

    // BIND OPERATIONS

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(
            Optional.of(
                new EnvoyResourcePair().setEnvoyId(newEnvoy).setResourceId("new-envoy-resource")));

    ResourceInfo info = new ResourceInfo();
    when(envoyResourceManagement.getOne(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(info));

    // EXECUTE

    Set<String> affectedEnvoys = monitorManagement
        .handleZoneChangePerResource(monitor, Collections.emptyList());

    // VERIFY

    // the old monitors were allocated an envoy each, the new ones were added to the sole new envoy
    Assertions.assertThat(affectedEnvoys).containsOnlyOnce(oldEnvoy1, oldEnvoy2, newEnvoy);

    // verify discovery operations
    verify(resourceRepository)
        .findByTenantIdAndResourceId(tenantId, resourceId0);
    verify(resourceRepository)
        .findByTenantIdAndResourceId(tenantId, resourceId1);

    // verify bind operations / each operation is performed once per resource
    ResolvedZone newResolvedZone = ResolvedZone.createPublicZone("public/newZone");
    verify(zoneAllocationResolver, times(2)).findLeastLoadedEnvoy(newResolvedZone);

    assertBindings(
        monitor.getId(),
        new BoundMonitorMatcher(monitor, "public/newZone", resourceId0, newEnvoy, "new-envoy-resource"),
        new BoundMonitorMatcher(monitor, "public/newZone", resourceId1, newEnvoy, "new-envoy-resource")
    );

    verifyNoMoreInteractions(resourceRepository, zoneAllocationResolver);
  }

  /**
   * Tests the case where a remote monitor was previously configured with explicitly set zones and
   * is now updated to have an empty list.
   *
   * When a monitor is updated to use an empty list of zones, the zones will instead be discovered
   * when binding to each resource as they may vary depending on the region the resource is in.
   *
   * In this case there are two resources initially using the same one zone. The updating of the
   * monitor to an empty list will lead to the previous bound monitors being removed from the old
   * zone and new monitors bound to two zones per resource.
   */
  @Test
  public void testHandleZoneChangePerResource_newZonesEmpty() {
    String tenantId = RandomStringUtils.randomAlphanumeric(5);
    // when the old zones are empty the bound monitor zone is based on the policy values.
    // for this test we use this one zone to represent that.
    List<String> originalZones = List.of("public/originalZone");
    List<String> newZones1 = List.of("public/mz-r1-1", "public/mz-r1-2");
    List<String> newZones2 = List.of("public/mz-r2-1", "public/mz-r2-2");

    String oldEnvoy1 = RandomStringUtils.randomAlphabetic(5);
    String oldEnvoy2 = RandomStringUtils.randomAlphabetic(5);
    String newEnvoy = RandomStringUtils.randomAlphabetic(5);

    // the updated monitor has no zones
    Monitor monitor = persistNewMonitor(tenantId, Map.of(), Collections.emptyList());

    final List<Resource> resources = List.of(
        podamFactory.manufacturePojo(Resource.class)
            .setResourceId("r-1")
            .setTenantId(tenantId)
            .setMetadata(Map.of("region", "testRegion1")),
        podamFactory.manufacturePojo(Resource.class)
            .setResourceId("r-2")
            .setTenantId(tenantId)
            .setMetadata(Map.of("region", "testRegion2"))
    );

    final String resourceId0 = resources.get(0).getResourceId();
    final String resourceId1 = resources.get(1).getResourceId();
    List<BoundMonitor> originalBoundMonitors = List.of(
        persistBoundMonitor(
            resourceId0, originalZones.get(0), oldEnvoy1, "poller-0", monitor),
        persistBoundMonitor(
            resourceId1, originalZones.get(0), oldEnvoy2, "poller-0", monitor)
    );

    // DISCOVERY OPERATIONS

    when(resourceRepository.findByTenantIdAndResourceId(anyString(), anyString()))
        .thenReturn(Optional.of(resources.get(0)))
        .thenReturn(Optional.of(resources.get(1)));

    when(policyApi
        .getDefaultMonitoringZones(resources.get(0).getMetadata().get(REGION_METADATA), true))
        .thenReturn(newZones1);
    when(policyApi
        .getDefaultMonitoringZones(resources.get(1).getMetadata().get(REGION_METADATA), true))
        .thenReturn(newZones2);

    // BIND OPERATIONS

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(
            Optional.of(
                new EnvoyResourcePair().setEnvoyId(newEnvoy).setResourceId("new-envoy-resource")));

    ResourceInfo info = new ResourceInfo();
    when(envoyResourceManagement.getOne(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(info));

    // EXECUTE

    Set<String> affectedEnvoys = monitorManagement
        .handleZoneChangePerResource(monitor, originalZones);

    // VERIFY

    // the old monitors were allocated an envoy each, the new ones were added to the sole new envoy
    Assertions.assertThat(affectedEnvoys).containsOnlyOnce(oldEnvoy1, oldEnvoy2, newEnvoy);

    // verify discovery operations
    verify(resourceRepository)
        .findByTenantIdAndResourceId(tenantId, resourceId0);
    verify(resourceRepository)
        .findByTenantIdAndResourceId(tenantId, resourceId1);
    verify(policyApi).getDefaultMonitoringZones("testRegion1", true);
    verify(policyApi).getDefaultMonitoringZones("testRegion2", true);

    verify(zoneAllocationResolver).findLeastLoadedEnvoy(resolveZone(null, newZones1.get(0)));
    verify(zoneAllocationResolver).findLeastLoadedEnvoy(resolveZone(null, newZones1.get(1)));
    verify(zoneAllocationResolver).findLeastLoadedEnvoy(resolveZone(null, newZones2.get(0)));
    verify(zoneAllocationResolver).findLeastLoadedEnvoy(resolveZone(null, newZones2.get(1)));

    Set<String> allNewZones = new HashSet<>();
    allNewZones.addAll(newZones1);
    allNewZones.addAll(newZones2);
    
    assertBindings(
        monitor.getId(),
        new BoundMonitorMatcher(monitor, newZones1.get(0), resourceId0, newEnvoy, "new-envoy-resource"),
        new BoundMonitorMatcher(monitor, newZones1.get(1), resourceId0, newEnvoy, "new-envoy-resource"),
        new BoundMonitorMatcher(monitor, newZones2.get(0), resourceId1, newEnvoy, "new-envoy-resource"),
        new BoundMonitorMatcher(monitor, newZones2.get(1), resourceId1, newEnvoy, "new-envoy-resource")
    );

    verifyNoMoreInteractions(envoyResourceManagement, resourceApi,
        monitorEventProducer, zoneManagement, zoneAllocationResolver
    );
  }

  /**
   * This tests verifies that if a bound monitor is found for a resource that no longer exists that
   * the orphaned bound monitor will be removed.
   */
  @Test
  public void testHandleZoneChangePerResource_orphanedBoundMonitors() {
    String tenantId = RandomStringUtils.randomAlphanumeric(5);
    String resourceId = RandomStringUtils.randomAlphabetic(5);

    Monitor monitor = persistNewMonitor(tenantId, "public/z-1");

    BoundMonitor orphanedBoundMonitor =
        persistBoundMonitor(resourceId, "public/z-1", "e-1", "poller-0", monitor);

    // return no resource for an orphaned bound monitor
    when(resourceRepository.findByTenantIdAndResourceId(anyString(), anyString()))
        .thenReturn(Optional.empty());

    // EXECUTE

    Set<String> affectedEnvoys = monitorManagement
        .handleZoneChangePerResource(monitor, Collections.emptyList());

    // VERIFY

    Assertions.assertThat(affectedEnvoys).containsOnly("e-1");

    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resourceId);

    final ResolvedZone resolvedZone = createPublicZone("public/z-1");

    // assert no bindings remain
    assertBindings(monitor.getId());

    verifyNoMoreInteractions(zoneStorage, resourceRepository, zoneAllocationResolver);
  }

  private Monitor persistNewMonitor(String tenantId, String zoneName) {
    return persistNewMonitor(tenantId, Map.of(), zoneName);
  }

  private Monitor persistNewMonitor(
      String tenantId, Map<String, String> labelSelector, String zoneName) {
    return persistNewMonitor(tenantId, labelSelector, List.of(zoneName));
  }

  private Monitor persistNewMonitor(
      String tenantId, Map<String, String> labelSelector, List<String> zones) {
    return monitorRepository.save(
        new Monitor()
            .setTenantId(tenantId)
            .setAgentType(AgentType.TELEGRAF)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setLabelSelector(labelSelector)
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setZones(zones)
            .setInterval(Duration.ofSeconds(60))
            .setContent("{}")
    );
  }

  private List<BoundMonitor> persistBoundMonitors(int count, String zoneName,
                                                  String pollerEnvoyId,
                                                  String pollerResourceId,
                                                  Monitor monitor) {
    return IntStream.range(0, count)
        .mapToObj(i ->
            boundMonitorRepository.save(
                new BoundMonitor()
                    .setMonitor(monitor)
                    .setTenantId(monitor.getTenantId())
                    .setEnvoyId(pollerEnvoyId)
                    .setPollerResourceId(pollerResourceId)
                    .setZoneName(zoneName)
                    .setResourceId(String.format("r-%d", i))
            )
        )
        .collect(Collectors.toList());
  }

  private BoundMonitor persistBoundMonitor(String resourceId, String zoneName,
                                           String pollerEnvoyId,
                                           String pollerResourceId, Monitor monitor) {
    return boundMonitorRepository.save(
        new BoundMonitor()
            .setTenantId(monitor.getTenantId())
            .setResourceId(resourceId)
            .setZoneName(zoneName)
            .setEnvoyId(pollerEnvoyId)
            .setPollerResourceId(pollerResourceId)
            .setMonitor(monitor)
            .setRenderedContent("{}")
    );
  }

  private Map<EnvoyResourcePair, Integer> persistBindingsToRebalance(Monitor monitor,
                                                                     String zoneName) {
    Map<EnvoyResourcePair, Integer> counts = new HashMap<>();
    counts.put(new EnvoyResourcePair().setResourceId("poller-least").setEnvoyId("e-least"), 0);

    counts.put(new EnvoyResourcePair().setResourceId("poller-1").setEnvoyId("e-1"), 2);
    persistBoundMonitors(2, zoneName, "e-1", "poller-1", monitor);

    counts.put(new EnvoyResourcePair().setResourceId("poller-2").setEnvoyId("e-2"), 3);
    persistBoundMonitors(3, zoneName, "e-2", "poller-2", monitor);

    counts.put(new EnvoyResourcePair().setResourceId("poller-3").setEnvoyId("e-3"), 6);
    persistBoundMonitors(6, zoneName, "e-3", "poller-3", monitor);

    counts.put(new EnvoyResourcePair().setResourceId("poller-4").setEnvoyId("e-4"), 2);
    persistBoundMonitors(2, zoneName, "e-4", "poller-4", monitor);
    return counts;
  }

  private void assertBindings(UUID monitorId, BoundMonitorMatcher... matchers) {
    final List<BoundMonitor> results = boundMonitorRepository
        .findAllByMonitor_Id(monitorId);

    assertThat(results, containsInAnyOrder(matchers));
  }

}
