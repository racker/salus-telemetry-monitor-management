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

import static com.google.common.collect.Collections2.transform;
import static com.rackspace.salus.telemetry.entities.Resource.REGION_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.config.DatabaseConfig;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.entities.Resource;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.repositories.BoundMonitorRepository;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import com.rackspace.salus.telemetry.repositories.ResourceRepository;
import com.rackspace.salus.test.EnableTestContainersDatabase;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
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


@SuppressWarnings("SameParameterValue")
@RunWith(SpringRunner.class)
@EnableTestContainersDatabase
@DataJpaTest(showSql = false)
@Import({ServicesProperties.class, ObjectMapper.class, MonitorManagement.class,
    MonitorContentRenderer.class,
    MonitorContentProperties.class,
    MetadataUtils.class,
    DatabaseConfig.class})
public class MonitorManagementTest_UpdateMonitor {

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

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @MockBean
  MonitorConversionService monitorConversionService;

  @MockBean
  MonitorEventProducer monitorEventProducer;

  @MockBean
  EnvoyResourceManagement envoyResourceManagement;

  @MockBean
  ZoneStorage zoneStorage;

  @MockBean
  BoundMonitorRepository boundMonitorRepository;

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

  @Autowired
  ObjectMapper objectMapper;
  @Autowired
  MonitorRepository monitorRepository;
  @Autowired
  EntityManager entityManager;
  @Autowired
  JdbcTemplate jdbcTemplate;
  @Autowired
  MetadataUtils metadataUtils;

  @Autowired
  private MonitorManagement monitorManagement;

  private PodamFactory podamFactory = new PodamFactoryImpl();

  @Captor
  private ArgumentCaptor<List<BoundMonitor>> captorOfDeletedBoundMonitors;

  @Captor
  private ArgumentCaptor<List<BoundMonitor>> captorOfSavedBoundMonitors;

  private Monitor persistNewRemoteMonitor(
      String tenantId, List<String> zones) {
    return monitorRepository.save(
        new Monitor()
            .setTenantId(tenantId)
            .setAgentType(AgentType.TELEGRAF)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setLabelSelector(Collections.emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.ping)
            .setZones(zones)
            .setInterval(Duration.ofSeconds(60))
            .setContent("{}")
    );
  }

  /**
   * Tests the case where a remote monitor was previously configured with an empty list of
   * zones and is now updated to have the zones explictly set.
   *
   * When a monitor is created with no zones, they will be discovered when binding to each resource
   * as they may vary for each resource.
   *
   * In this case there are two resources each using different zones.
   * The updating of the monitor to a completely different zone will lead to the previous
   * bound monitors being removed from the old zones and two new ones being created on the
   * single zone specified on the monitor.
   */
  @Test
  public void testHandleZoneChangePerResource_oldZonesEmpty() {
    String tenantId = RandomStringUtils.randomAlphanumeric(5);
    // when the old zones are empty the bound monitor zone is based on the policy values.
    // for this test we use this one zone to represent that.
    String originalZoneForResource = "public/originalZone";

    String oldEnvoy1 = RandomStringUtils.randomAlphabetic(5);
    String oldEnvoy2 = RandomStringUtils.randomAlphabetic(5);
    String newEnvoy = RandomStringUtils.randomAlphabetic(5);

    // the updated monitor has zones explicitly set
    Monitor monitor = persistNewRemoteMonitor(tenantId, List.of("public/newZone"));

    final List<Resource> resources = List.of(
        podamFactory.manufacturePojo(Resource.class)
            .setResourceId("r-1")
            .setTenantId(tenantId),
        podamFactory.manufacturePojo(Resource.class)
            .setResourceId("r-2")
            .setTenantId(tenantId));

    List<BoundMonitor> originalBoundMonitors = List.of(
        new BoundMonitor()
            .setResourceId(resources.get(0).getResourceId())
            .setZoneName(originalZoneForResource)
            .setEnvoyId(oldEnvoy1)
            .setMonitor(monitor),
        new BoundMonitor()
            .setResourceId(resources.get(1).getResourceId())
            .setZoneName(originalZoneForResource)
            .setEnvoyId(oldEnvoy2)
            .setMonitor(monitor)
    );

    // DISCOVERY OPERATIONS

    when(boundMonitorRepository.findAllByMonitor_Id(any()))
        .thenReturn(originalBoundMonitors);

    when(resourceRepository.findByTenantIdAndResourceId(anyString(), anyString()))
        .thenReturn(Optional.of(resources.get(0)))
        .thenReturn(Optional.of(resources.get(1)));

    // UNBIND OPERATIONS

    when(boundMonitorRepository.findAllByMonitor_IdAndResourceIdAndZoneNameIn(any(), anyString(), any()))
        .thenReturn(List.of(originalBoundMonitors.get(0)))
        .thenReturn(List.of(originalBoundMonitors.get(1)));

    when(zoneStorage.getEnvoyIdToResourceIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap(oldEnvoy1, "resourceValueDoesntMatter")))
        .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap(oldEnvoy2, "resourceValueDoesntMatter")));

    // BIND OPERATIONS

    when(zoneStorage.findLeastLoadedEnvoy(any()))
        .thenReturn(CompletableFuture.completedFuture(
            Optional.of(
                new EnvoyResourcePair().setEnvoyId(newEnvoy).setResourceId("new-envoy-resource"))));

    when(zoneStorage.incrementBoundCount(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(1));


    // EXECUTE

    Set<String> affectedEnvoys = monitorManagement.handleZoneChangePerResource(monitor, Collections.emptyList());

    // VERIFY

    // the old monitors were allocated an envoy each, the new ones were added to the sole new envoy
    assertThat(affectedEnvoys).containsOnlyOnce(oldEnvoy1, oldEnvoy2, newEnvoy);

    // verify discovery operations
    verify(boundMonitorRepository).findAllByMonitor_Id(monitor.getId());
    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resources.get(0).getResourceId());
    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resources.get(1).getResourceId());

    // verify unbind operations / each operation is performed once per resource
    verify(boundMonitorRepository, times(2)).deleteAll(captorOfDeletedBoundMonitors.capture());
    List<BoundMonitor> allUnbound = captorOfDeletedBoundMonitors.getAllValues().stream().flatMap(List::stream).collect(Collectors.toList());
    assertThat(allUnbound).hasSize(2);
    assertThat(allUnbound).extracting(BoundMonitor::getZoneName).containsOnly("public/originalZone");
    assertThat(allUnbound).extracting(BoundMonitor::getEnvoyId).containsOnly(oldEnvoy1, oldEnvoy2);
    assertThat(allUnbound).extracting(BoundMonitor::getResourceId).containsExactlyElementsOf(transform(resources, Resource::getResourceId));

    // verify bind operations / each operation is performed once per resource
    ResolvedZone newResolvedZone = ResolvedZone.createPublicZone("public/newZone");
    verify(boundMonitorRepository, times(2)).saveAll(captorOfSavedBoundMonitors.capture());
    verify(zoneStorage, times(2)).findLeastLoadedEnvoy(newResolvedZone);
    verify(zoneStorage, times(2)).incrementBoundCount(newResolvedZone, "new-envoy-resource");

    List<BoundMonitor> allBound = captorOfSavedBoundMonitors.getAllValues().stream().flatMap(List::stream).collect(Collectors.toList());
    assertThat(allBound).hasSize(2);
    assertThat(allBound).extracting(BoundMonitor::getZoneName).containsOnly("public/newZone");
    assertThat(allBound).extracting(BoundMonitor::getEnvoyId).containsOnly(newEnvoy);
    assertThat(allBound).extracting(BoundMonitor::getResourceId).containsExactlyElementsOf(transform(resources, Resource::getResourceId));
  }

  /**
   * Tests the case where a remote monitor was previously configured with explicitly set
   * zones and is now updated to have an empty list.
   *
   * When a monitor is updated to use an empty list of zones, the zones will instead be discovered
   * when binding to each resource as they may vary depending on the region the resource is in.
   *
   * In this case there are two resources initially using the same one zone.
   * The updating of the monitor to an empty list will lead to the previous bound monitors
   * being removed from the old zone and new monitors bound to two zones per resource.
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
    Monitor monitor = persistNewRemoteMonitor(tenantId, Collections.emptyList());

    final List<Resource> resources = List.of(
        podamFactory.manufacturePojo(Resource.class)
            .setResourceId("r-1")
            .setTenantId(tenantId)
            .setMetadata(Map.of("region", "testRegion1")),
        podamFactory.manufacturePojo(Resource.class)
            .setResourceId("r-2")
            .setTenantId(tenantId)
            .setMetadata(Map.of("region", "testRegion2")));

    List<BoundMonitor> originalBoundMonitors = List.of(
        new BoundMonitor()
            .setResourceId(resources.get(0).getResourceId())
            .setZoneName(originalZones.get(0))
            .setEnvoyId(oldEnvoy1)
            .setMonitor(monitor),
        new BoundMonitor()
            .setResourceId(resources.get(1).getResourceId())
            .setZoneName(originalZones.get(0))
            .setEnvoyId(oldEnvoy2)
            .setMonitor(monitor)
    );

    // DISCOVERY OPERATIONS

    when(boundMonitorRepository.findAllByMonitor_Id(any()))
        .thenReturn(originalBoundMonitors);

    when(resourceRepository.findByTenantIdAndResourceId(anyString(), anyString()))
        .thenReturn(Optional.of(resources.get(0)))
        .thenReturn(Optional.of(resources.get(1)));

    when(policyApi.getDefaultMonitoringZones(resources.get(0).getMetadata().get(REGION_METADATA), true))
        .thenReturn(newZones1);
    when(policyApi.getDefaultMonitoringZones(resources.get(1).getMetadata().get(REGION_METADATA), true))
        .thenReturn(newZones2);

    // UNBIND OPERATIONS

    when(boundMonitorRepository.findAllByMonitor_IdAndResourceIdAndZoneNameIn(any(), anyString(), any()))
        .thenReturn(List.of(originalBoundMonitors.get(0)))
        .thenReturn(List.of(originalBoundMonitors.get(1)));

    when(zoneStorage.getEnvoyIdToResourceIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap(oldEnvoy1, "resourceValueDoesntMatter")))
        .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap(oldEnvoy2, "resourceValueDoesntMatter")));

    // BIND OPERATIONS

    when(zoneStorage.findLeastLoadedEnvoy(any()))
        .thenReturn(CompletableFuture.completedFuture(
            Optional.of(
                new EnvoyResourcePair().setEnvoyId(newEnvoy).setResourceId("new-envoy-resource"))));

    when(zoneStorage.incrementBoundCount(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(1));


    // EXECUTE

    Set<String> affectedEnvoys = monitorManagement.handleZoneChangePerResource(monitor, originalZones);

    // VERIFY

    // the old monitors were allocated an envoy each, the new ones were added to the sole new envoy
    assertThat(affectedEnvoys).containsOnlyOnce(oldEnvoy1, oldEnvoy2, newEnvoy);

    // verify discovery operations
    verify(boundMonitorRepository).findAllByMonitor_Id(monitor.getId());
    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resources.get(0).getResourceId());
    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resources.get(1).getResourceId());
    verify(policyApi).getDefaultMonitoringZones("testRegion1", true);
    verify(policyApi).getDefaultMonitoringZones("testRegion2", true);

    // verify unbind operations / each operation is performed once per resource
    verify(boundMonitorRepository, times(2)).deleteAll(captorOfDeletedBoundMonitors.capture());
    List<BoundMonitor> allUnbound = captorOfDeletedBoundMonitors.getAllValues().stream().flatMap(List::stream).collect(Collectors.toList());
    assertThat(allUnbound).hasSize(2);
    assertThat(allUnbound).extracting(BoundMonitor::getZoneName).containsOnly("public/originalZone");
    assertThat(allUnbound).extracting(BoundMonitor::getEnvoyId).containsOnly(oldEnvoy1, oldEnvoy2);
    assertThat(allUnbound).extracting(BoundMonitor::getResourceId).containsOnlyElementsOf(transform(resources, Resource::getResourceId));

    // verify bind operations / each operation is performed twice per zone per resource
    verify(boundMonitorRepository, times(2)).saveAll(captorOfSavedBoundMonitors.capture());
    verify(zoneStorage, times(4)).findLeastLoadedEnvoy(any());
    verify(zoneStorage, times(4)).incrementBoundCount(any(), anyString());

    Set<String> allNewZones = new HashSet<>();
    allNewZones.addAll(newZones1);
    allNewZones.addAll(newZones2);

    List<BoundMonitor> allBound = captorOfSavedBoundMonitors.getAllValues().stream().flatMap(List::stream).collect(Collectors.toList());
    assertThat(allBound).hasSize(4);
    assertThat(allBound).extracting(BoundMonitor::getZoneName).containsOnlyElementsOf(allNewZones);
    assertThat(allBound).extracting(BoundMonitor::getEnvoyId).containsOnly(newEnvoy);
    assertThat(allBound).extracting(BoundMonitor::getResourceId).containsOnlyElementsOf(transform(resources, Resource::getResourceId));
  }

  /**
   * This tests verifies that if a bound monitor is found for a resource that no longer exists
   * that the orphaned bound monitor will be removed.
   */
  @Test
  public void testHandleZoneChangePerResource_orphanedBoundMonitors() {
    String tenantId = RandomStringUtils.randomAlphanumeric(5);
    String resourceId = RandomStringUtils.randomAlphabetic(5);

    Monitor monitor = persistNewRemoteMonitor(tenantId, List.of("public/z-1"));

    BoundMonitor orphanedBoundMonitor = new BoundMonitor()
        .setResourceId(resourceId)
        .setZoneName("public/z-1")
        .setEnvoyId("e-1")
        .setMonitor(monitor);

    // return no resource for an orphaned bound monitor
    when(resourceRepository.findByTenantIdAndResourceId(anyString(), anyString()))
        .thenReturn(Optional.empty());

    when(boundMonitorRepository.findAllByMonitor_Id(any()))
        .thenReturn(List.of(orphanedBoundMonitor));

    when(boundMonitorRepository.findAllByMonitor_IdAndResourceIdIn(any(), any()))
        .thenReturn(List.of(orphanedBoundMonitor));

    when(zoneStorage.getEnvoyIdToResourceIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap("e-1", "resourceValueDoesntMatter")));

    // EXECUTE

    Set<String> affectedEnvoys = monitorManagement.handleZoneChangePerResource(monitor, Collections.emptyList());

    // VERIFY

    assertThat(affectedEnvoys).containsOnly("e-1");

    verify(boundMonitorRepository).findAllByMonitor_Id(monitor.getId());
    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceIdIn(monitor.getId(), List.of(resourceId));
    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resourceId);
    verify(zoneStorage).getEnvoyIdToResourceIdMap(ResolvedZone.createPublicZone("public/z-1"));

    verify(boundMonitorRepository).deleteAll(captorOfDeletedBoundMonitors.capture());
    List<BoundMonitor> allUnbound = captorOfDeletedBoundMonitors.getAllValues().stream().flatMap(List::stream).collect(Collectors.toList());
    assertThat(allUnbound).containsExactly(orphanedBoundMonitor);
  }
}
