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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;

import com.rackspace.salus.monitor_management.config.DatabaseConfig;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.repositories.BoundMonitorRepository;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import com.rackspace.salus.test.EnableTestContainersDatabase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@EnableTestContainersDatabase
@DataJpaTest(showSql = false)
public class ZoneAllocationResolverTest {

  /**
   * Provides minimal app context config mainly to avoid picking up Spring Boot configs like
   * TelemetryCoreEtcdModule.
   */
  @Configuration
  @Import({
      DatabaseConfig.class,
      ZoneAllocationResolver.class,
  })
  public static class TestConfig { }

  @MockBean
  ZoneStorage zoneStorage;

  @Autowired
  ZoneAllocationResolver zoneAllocationResolver;

  @Autowired
  MonitorRepository monitorRepository;

  @Autowired
  BoundMonitorRepository boundMonitorRepository;

  @After
  public void tearDown() throws Exception {
    boundMonitorRepository.deleteAll();
    monitorRepository.deleteAll();
  }

  @Test
  public void testFindLeastLoadedEnvoy_noneActive_noneBound() {
    final String tenantId = randomAlphanumeric(10);
    final ResolvedZone zone = ResolvedZone.resolveZone(tenantId, randomAlphanumeric(5));

    when(zoneStorage.getActivePollerResourceIdsInZone(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    when(zoneStorage.getResourceIdToEnvoyIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Map.of()));

    // EXECUTE

    final Optional<EnvoyResourcePair> result = zoneAllocationResolver
        .findLeastLoadedEnvoy(zone);

    // VERIFY

    assertThat(result).isEmpty();

    verify(zoneStorage).getActivePollerResourceIdsInZone(zone);
    verify(zoneStorage).getResourceIdToEnvoyIdMap(zone);

    verifyNoMoreInteractions(zoneStorage);
  }

  @Test
  public void testFindLeastLoadedEnvoy_oneEmpty_noneBound() {
    final String tenantId = randomAlphanumeric(10);
    final ResolvedZone zone = ResolvedZone.resolveZone(tenantId, randomAlphanumeric(5));

    when(zoneStorage.getActivePollerResourceIdsInZone(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of("poller-1")));

    when(zoneStorage.getResourceIdToEnvoyIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Map.of("poller-1", "e-1")));

    // EXECUTE

    final Optional<EnvoyResourcePair> result = zoneAllocationResolver.findLeastLoadedEnvoy(zone);

    // VERIFY

    assertThat(result).isNotEmpty();
    assertThat(result).get()
        .isEqualTo(new EnvoyResourcePair().setResourceId("poller-1").setEnvoyId("e-1"));

    verify(zoneStorage).getActivePollerResourceIdsInZone(zone);
    verify(zoneStorage).getResourceIdToEnvoyIdMap(zone);

    verifyNoMoreInteractions(zoneStorage);
  }

  @Test
  public void testFindLeastLoadedEnvoy_someEmpty_someBound() {
    final String tenantId = randomAlphanumeric(10);
    final ResolvedZone zone = ResolvedZone.resolveZone(tenantId, randomAlphanumeric(5));

    when(zoneStorage.getActivePollerResourceIdsInZone(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of("poller-1", "poller-2", "poller-3")));

    when(zoneStorage.getResourceIdToEnvoyIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Map.of(
            "poller-1", "e-1",
            "poller-2", "e-2",
            "poller-3", "e-3"
        )));

    final Monitor monitor = persistNewMonitor(tenantId, Map.of(), List.of(zone.getName()));
    persistBoundMonitors(2, zone.getName(), "e-1", "poller-1", monitor);
    // poller-2 will be the empty one
    persistBoundMonitors(1, zone.getName(), "e-3", "poller-3", monitor);

    // EXECUTE

    final Optional<EnvoyResourcePair> result = zoneAllocationResolver.findLeastLoadedEnvoy(zone);

    // VERIFY

    assertThat(result).isNotEmpty();
    assertThat(result).get()
        .isEqualTo(new EnvoyResourcePair().setResourceId("poller-2").setEnvoyId("e-2"));

    verify(zoneStorage).getActivePollerResourceIdsInZone(zone);
    verify(zoneStorage).getResourceIdToEnvoyIdMap(zone);

    verifyNoMoreInteractions(zoneStorage);
  }

  @Test
  public void testFindLeastLoadedEnvoy_noneEmpty_someBound() {
    final String tenantId = randomAlphanumeric(10);
    final ResolvedZone zone = ResolvedZone.resolveZone(tenantId, randomAlphanumeric(5));

    when(zoneStorage.getActivePollerResourceIdsInZone(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of("poller-1", "poller-2", "poller-3")));

    when(zoneStorage.getResourceIdToEnvoyIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Map.of(
            "poller-1", "e-1",
            "poller-2", "e-2",
            "poller-3", "e-3"
        )));

    final Monitor monitor = persistNewMonitor(tenantId, Map.of(), List.of(zone.getName()));
    persistBoundMonitors(2, zone.getName(), "e-1", "poller-1", monitor);
    persistBoundMonitors(3, zone.getName(), "e-2", "poller-2", monitor);
    persistBoundMonitors(1, zone.getName(), "e-3", "poller-3", monitor);

    // EXECUTE

    final Optional<EnvoyResourcePair> result = zoneAllocationResolver.findLeastLoadedEnvoy(zone);

    // VERIFY

    assertThat(result).isNotEmpty();
    assertThat(result).get()
        .isEqualTo(new EnvoyResourcePair().setResourceId("poller-3").setEnvoyId("e-3"));

    verify(zoneStorage).getActivePollerResourceIdsInZone(zone);
    verify(zoneStorage).getResourceIdToEnvoyIdMap(zone);

    verifyNoMoreInteractions(zoneStorage);
  }

  @Test
  public void testFindLeastLoadedEnvoy_tracksRepeatedCalls() {
    final String tenantId = randomAlphanumeric(10);
    final ResolvedZone zone = ResolvedZone.resolveZone(tenantId, randomAlphanumeric(5));

    when(zoneStorage.getActivePollerResourceIdsInZone(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of("poller-1", "poller-2", "poller-3")));

    when(zoneStorage.getResourceIdToEnvoyIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Map.of(
            "poller-1", "e-1",
            "poller-2", "e-2",
            "poller-3", "e-3"
        )));

    final Monitor monitor = persistNewMonitor(tenantId, Map.of(), List.of(zone.getName()));
    persistBoundMonitors(2, zone.getName(), "e-1", "poller-1", monitor);
    // poller-2 will start out as empty one
    persistBoundMonitors(1, zone.getName(), "e-3", "poller-3", monitor);

    // EXECUTE/ASSERT

    /*
    Expected pollerResourceCounts progression:
    {poller-1=2, poller-2=0, poller-3=1}
    {poller-1=2, poller-2=1, poller-3=1}
    {poller-1=2, poller-2=2, poller-3=1}
    {poller-1=2, poller-2=2, poller-3=2}
     */

    // scope variables for each sub-case
    {
      final Optional<EnvoyResourcePair> result = zoneAllocationResolver.findLeastLoadedEnvoy(zone);
      assertThat(result).isNotEmpty();
      assertThat(result).get()
          .isEqualTo(new EnvoyResourcePair().setResourceId("poller-2").setEnvoyId("e-2"));
    }
    {
      final Optional<EnvoyResourcePair> result = zoneAllocationResolver.findLeastLoadedEnvoy(zone);
      assertThat(result).isNotEmpty();
      assertThat(result).get()
          .isEqualTo(new EnvoyResourcePair().setResourceId("poller-2").setEnvoyId("e-2"));
    }
    {
      final Optional<EnvoyResourcePair> result = zoneAllocationResolver.findLeastLoadedEnvoy(zone);
      assertThat(result).isNotEmpty();
      assertThat(result).get()
          .isEqualTo(new EnvoyResourcePair().setResourceId("poller-3").setEnvoyId("e-3"));
    }
    {
      final Optional<EnvoyResourcePair> result = zoneAllocationResolver.findLeastLoadedEnvoy(zone);
      assertThat(result).isNotEmpty();
      assertThat(result).get()
          .isEqualTo(new EnvoyResourcePair().setResourceId("poller-1").setEnvoyId("e-1"));
    }

    // but these are still only called once
    verify(zoneStorage).getActivePollerResourceIdsInZone(zone);
    verify(zoneStorage).getResourceIdToEnvoyIdMap(zone);

    verifyNoMoreInteractions(zoneStorage);
  }

  @Test
  public void testFindLeastLoadedEnvoy_twoZones() {
    final String tenantId = randomAlphanumeric(10);
    final ResolvedZone zone1 = ResolvedZone.resolveZone(tenantId, randomAlphanumeric(5));
    final ResolvedZone zone2 = ResolvedZone.resolveZone(tenantId, randomAlphanumeric(5));

    when(zoneStorage.getActivePollerResourceIdsInZone(zone1))
        .thenReturn(CompletableFuture.completedFuture(List.of("poller-1-1", "poller-1-2")));
    when(zoneStorage.getActivePollerResourceIdsInZone(zone2))
        .thenReturn(CompletableFuture.completedFuture(List.of("poller-2-1", "poller-2-2")));

    when(zoneStorage.getResourceIdToEnvoyIdMap(zone1))
        .thenReturn(CompletableFuture.completedFuture(Map.of(
            "poller-1-1", "e-1-1",
            "poller-1-2", "e-1-2"
        )));
    when(zoneStorage.getResourceIdToEnvoyIdMap(zone2))
        .thenReturn(CompletableFuture.completedFuture(Map.of(
            "poller-2-1", "e-2-1",
            "poller-2-2", "e-2-2"
        )));

    final Monitor monitor = persistNewMonitor(tenantId, Map.of(),
        List.of(zone1.getName(), zone2.getName()));
    persistBoundMonitors(2, zone1.getName(), "e-1-1", "poller-1-1", monitor);
    // zone1, poller-2 will start out as empty one

    // zone2 ,poller-1 will start out as empty one
    persistBoundMonitors(2, zone2.getName(), "e-2-2", "poller-2-2", monitor);

    // EXECUTE

    // scope variables for each sub-case
    { // zone1
      final Optional<EnvoyResourcePair> result = zoneAllocationResolver.findLeastLoadedEnvoy(zone1);
      assertThat(result).isNotEmpty();
      assertThat(result).get()
          .isEqualTo(new EnvoyResourcePair().setResourceId("poller-1-2").setEnvoyId("e-1-2"));
    }
    { // zone2
      final Optional<EnvoyResourcePair> result = zoneAllocationResolver.findLeastLoadedEnvoy(zone2);
      assertThat(result).isNotEmpty();
      assertThat(result).get()
          .isEqualTo(new EnvoyResourcePair().setResourceId("poller-2-1").setEnvoyId("e-2-1"));
    }

    // VERIFY

    // once for each zone
    verify(zoneStorage).getActivePollerResourceIdsInZone(zone1);
    verify(zoneStorage).getActivePollerResourceIdsInZone(zone2);
    verify(zoneStorage).getResourceIdToEnvoyIdMap(zone1);
    verify(zoneStorage).getResourceIdToEnvoyIdMap(zone2);

    verifyNoMoreInteractions(zoneStorage);
  }

  @Test
  public void testGetZoneBindingCounts() {
    final String tenantId = randomAlphanumeric(10);
    final ResolvedZone zone = ResolvedZone.resolveZone(tenantId, randomAlphanumeric(5));

    when(zoneStorage.getActivePollerResourceIdsInZone(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of("poller-1", "poller-2", "poller-3")));

    when(zoneStorage.getResourceIdToEnvoyIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Map.of(
            "poller-1", "e-1",
            "poller-2", "e-2",
            "poller-3", "e-3"
        )));

    final Monitor monitor = persistNewMonitor(tenantId, Map.of(), List.of(zone.getName()));
    persistBoundMonitors(2, zone.getName(), "e-1", "poller-1", monitor);
    // poller-2 is empty one
    persistBoundMonitors(1, zone.getName(), "e-3", "poller-3", monitor);

    // EXECUTE

    final Map<EnvoyResourcePair, Integer> result = zoneAllocationResolver
        .getActiveZoneBindingCounts(zone)
        .join();

    // VERIFY

    assertThat(result).isEqualTo(Map.of(
       new EnvoyResourcePair().setEnvoyId("e-1").setResourceId("poller-1"), 2,
       new EnvoyResourcePair().setEnvoyId("e-2").setResourceId("poller-2"), 0,
       new EnvoyResourcePair().setEnvoyId("e-3").setResourceId("poller-3"), 1
    ));

    verify(zoneStorage).getActivePollerResourceIdsInZone(zone);
    verify(zoneStorage).getResourceIdToEnvoyIdMap(zone);

    verifyNoMoreInteractions(zoneStorage);
  }


  private void persistBoundMonitors(int count, String zoneName,
                                    String pollerEnvoyId,
                                    String pollerResourceId,
                                    Monitor monitor) {
    IntStream.range(0, count)
        .mapToObj(i ->
            boundMonitorRepository.save(
                new BoundMonitor()
                    .setMonitor(monitor)
                    .setTenantId(monitor.getTenantId())
                    .setEnvoyId(pollerEnvoyId)
                    .setPollerResourceId(pollerResourceId)
                    .setZoneName(zoneName)
                    .setResourceId(randomAlphanumeric(5))
            )
        )
        .collect(Collectors.toList());
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


  @Test
  public void testGetExpiringZoneBindingCounts() {
    final String tenantId = randomAlphanumeric(10);
    final ResolvedZone zone = ResolvedZone.resolveZone(tenantId, randomAlphanumeric(5));

    when(zoneStorage.getExpiringPollerResourceIdsInZone(any()))
        .thenReturn(CompletableFuture.completedFuture(List.of("poller-1", "poller-2", "poller-3")));

    when(zoneStorage.getResourceIdToEnvoyIdMap(any()))
        .thenReturn(CompletableFuture.completedFuture(Map.of(
            "poller-1", "e-1",
            "poller-2", "e-2",
            "poller-3", "e-3"
        )));

    final Monitor monitor = persistNewMonitor(tenantId, Map.of(), List.of(zone.getName()));
    persistBoundMonitors(2, zone.getName(), "e-1", "poller-1", monitor);
    // poller-2 is empty one
    persistBoundMonitors(1, zone.getName(), "e-3", "poller-3", monitor);

    // EXECUTE

    final Map<EnvoyResourcePair, Integer> result = zoneAllocationResolver
        .getExpiringZoneBindingCounts(zone)
        .join();

    // VERIFY

    assertThat(result).isEqualTo(Map.of(
        new EnvoyResourcePair().setEnvoyId("e-1").setResourceId("poller-1"), 2,
        new EnvoyResourcePair().setEnvoyId("e-2").setResourceId("poller-2"), 0,
        new EnvoyResourcePair().setEnvoyId("e-3").setResourceId("poller-3"), 1
    ));

    verify(zoneStorage).getExpiringPollerResourceIdsInZone(zone);
    verify(zoneStorage).getResourceIdToEnvoyIdMap(zone);

    verifyNoMoreInteractions(zoneStorage);
  }
}