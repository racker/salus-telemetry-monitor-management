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

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.rackspace.salus.monitor_management.config.DatabaseConfig;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.errors.InvalidTemplateException;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.entities.Resource;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import com.rackspace.salus.telemetry.repositories.BoundMonitorRepository;
import com.rackspace.salus.test.EnableTestContainersDatabase;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
    DatabaseConfig.class,
    MonitorManagement.class,
    ZonesProperties.class,
    ZoneAllocationResolverFactory.class,
})
@EnableTestContainersDatabase
@AutoConfigureDataJpa
@AutoConfigureTestEntityManager
@Transactional
@Import({MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class})
public class MonitorManagementExcludeResourceIdsTest {

  @Autowired
  MonitorManagement monitorManagement;

  @Autowired
  BoundMonitorRepository boundMonitorRepository;

  @Autowired
  TestEntityManager entityManager;

  @MockBean
  EnvoyResourceManagement envoyResourceManagement;

  @MockBean
  ZoneStorage zoneStorage;

  @MockBean
  ZoneManagement zoneManagement;

  @MockBean
  MonitorEventProducer monitorEventProducer;

  @MockBean
  MonitorContentRenderer monitorContentRenderer;

  @MockBean
  MonitorConversionService monitorConversionService;

  @MockBean
  MetadataUtils metadataUtils;

  @MockBean
  PolicyApi policyApi;

  @MockBean
  ResourceApi resourceApi;

  @MockBean
  ZoneAllocationResolver zoneAllocationResolver;

  @Test
  public void testCreate_noResources() {
    final Monitor monitor = monitorManagement.createMonitor(
        "t-1",
        new MonitorCU()
            .setExcludedResourceIds(Set.of("r-3", "r-5"))
            .setMonitorType(MonitorType.cpu)
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{}")
            .setInterval(Duration.ofSeconds(60))
    );

    assertThat(monitor).isNotNull();

    final Monitor retrieved = entityManager.find(Monitor.class, monitor.getId());
    assertThat(retrieved.getExcludedResourceIds()).containsExactlyInAnyOrder("r-3", "r-5");
  }

  @Test
  public void testCreate_withResources() {
    when(resourceApi.getResourcesWithLabels(any(), any(), any()))
        .thenReturn(List.of(
            new ResourceDTO()
                .setResourceId("r-1")
                .setTenantId("t-1")
                .setAssociatedWithEnvoy(true),
            new ResourceDTO()
                .setResourceId("r-3")
                .setTenantId("t-1")
                .setAssociatedWithEnvoy(true)
        ));

    setupEnvoyResourceManagement();

    // EXECUTE

    final Monitor monitor = monitorManagement.createMonitor(
        "t-1",
        new MonitorCU()
            .setExcludedResourceIds(Set.of("r-3", "r-5"))
            .setLabelSelector(emptyMap())
            .setMonitorType(MonitorType.cpu)
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{}")
            .setInterval(Duration.ofSeconds(60))
    );

    // VERIFY

    assertThat(monitor).isNotNull();

    final Monitor retrieved = entityManager.find(Monitor.class, monitor.getId());
    assertThat(retrieved.getExcludedResourceIds()).containsExactlyInAnyOrder("r-3", "r-5");

    final Page<BoundMonitor> bound = boundMonitorRepository
        .findAllByMonitor_IdAndMonitor_TenantId(monitor.getId(), "t-1", Pageable.unpaged());

    assertThat(bound).hasSize(1);
    assertThat(bound.getContent().get(0).getResourceId()).isEqualTo("r-1");

    verify(resourceApi).getResourcesWithLabels("t-1", emptyMap(), LabelSelectorMethod.AND);

    // but won't query r-3 since it's excluded
    verify(envoyResourceManagement, never()).getOne("t-1", "r-3");

    verifyNoMoreInteractions(resourceApi, envoyResourceManagement);
  }

  @Test
  public void testUpdate_noResources() {
    // JPA needs a mutable collection
    final Set<String> originalExcludes = new HashSet<>(Set.of("r-3", "r-5"));
    final UUID existingId = entityManager.persistAndGetId(
        new Monitor()
            .setExcludedResourceIds(originalExcludes)
            .setTenantId("t-1")
            .setMonitorType(MonitorType.cpu)
            .setAgentType(AgentType.TELEGRAF)
            .setInterval(Duration.ofSeconds(60))
            .setContent("{}"),
        UUID.class
    );

    // EXECUTE

    final Monitor monitor = monitorManagement.updateMonitor(
        "t-1",
        existingId,
        new MonitorCU()
            .setExcludedResourceIds(Set.of("r-7", "r-9"))
            .setMonitorType(MonitorType.cpu)
            .setAgentType(AgentType.TELEGRAF)
    );

    // VERIFY

    assertThat(monitor).isNotNull();

    final Monitor retrieved = entityManager.find(Monitor.class, existingId);
    assertThat(retrieved.getExcludedResourceIds()).containsExactlyInAnyOrder("r-7", "r-9");
  }

  @Test
  public void testUpdate_withResources() throws InvalidTemplateException {
    /*
    Test cases with the four resources
    r-exclude-include : excluded at first, but not after update
    r-include-include : included before and after
    r-include-exclude : included at first, but excluded after
    r-exclude-exclude : excluded at first and after
     */

    when(resourceApi.getResourcesWithLabels(any(), any(), any()))
        .thenReturn(List.of(
            new ResourceDTO()
                .setResourceId("r-exclude-include")
                .setTenantId("t-1")
                .setAssociatedWithEnvoy(true)
                .setEnvoyId("envoy-r-exclude-include"),
            new ResourceDTO()
                .setResourceId("r-include-include")
                .setTenantId("t-1")
                .setAssociatedWithEnvoy(true)
                .setEnvoyId("envoy-r-include-include"),
            new ResourceDTO()
                .setResourceId("r-include-exclude")
                .setTenantId("t-1")
                .setAssociatedWithEnvoy(true)
                .setEnvoyId("envoy-r-include-exclude"),
            new ResourceDTO()
                .setResourceId("r-exclude-exclude")
                .setTenantId("t-1")
                .setAssociatedWithEnvoy(true)
                .setEnvoyId("envoy-r-exclude-exclude")
        ));

    setupEnvoyResourceManagement();

    setupMonitorContentRenderer();

    // use a regular create to save the original monitor and perform bindings
    final Monitor original = monitorManagement.createMonitor(
        "t-1",
        new MonitorCU()
            .setExcludedResourceIds(Set.of("r-exclude-include", "r-exclude-exclude"))
            .setLabelSelector(emptyMap())
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setMonitorType(MonitorType.cpu)
            .setAgentType(AgentType.TELEGRAF)
            .setInterval(Duration.ofSeconds(60))
            .setContent("{}")
    );

    // sanity check bindings
    final Page<BoundMonitor> origBound = boundMonitorRepository
        .findAllByMonitor_IdAndMonitor_TenantId(original.getId(), "t-1", Pageable.unpaged());
    assertThat(origBound).extracting(BoundMonitor::getResourceId)
        .containsExactlyInAnyOrder("r-include-include", "r-include-exclude");

    // and verify up to this point
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("envoy-r-include-include")
    );

    // EXECUTE

    monitorManagement.updateMonitor(
        "t-1",
        original.getId(),
        new MonitorCU()
            .setExcludedResourceIds(Set.of("r-include-exclude", "r-exclude-exclude"))
            .setMonitorType(MonitorType.cpu)
            .setAgentType(AgentType.TELEGRAF)
    );

    // VERIFY

    final Page<BoundMonitor> updatedBound = boundMonitorRepository
        .findAllByMonitor_IdAndMonitor_TenantId(original.getId(), "t-1", Pageable.unpaged());
    assertThat(updatedBound).extracting(BoundMonitor::getResourceId)
        .containsExactlyInAnyOrder("r-include-include", "r-exclude-include");

    // 1x from create and 1x from update (unbinding)
    verify(monitorEventProducer, times(2)).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("envoy-r-include-exclude")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("envoy-r-exclude-include")
    );

    // 1x from create and 1x from update
    verify(resourceApi, times(2))
        .getResourcesWithLabels("t-1", emptyMap(), LabelSelectorMethod.AND);

    verify(envoyResourceManagement).getOne("t-1", "r-exclude-include");
    verify(envoyResourceManagement, never()).getOne("t-1", "r-exclude-exclude");

    verifyNoMoreInteractions(resourceApi, envoyResourceManagement, monitorEventProducer);
  }

  @Test
  public void testUpdate_labelChangeSelectsExcluded()
      throws InvalidTemplateException {

    when(resourceApi
        .getResourcesWithLabels("t-1", Map.of("stage", "before"), LabelSelectorMethod.AND))
        .thenReturn(List.of(
            new ResourceDTO()
                // a "control" resource that'll be bound in both cases
                .setResourceId("r-include-include")
                .setTenantId("t-1")
                .setAssociatedWithEnvoy(true)
                .setEnvoyId("envoy-r-include-include")
        ));
    when(resourceApi
        .getResourcesWithLabels("t-1", Map.of("stage", "after"), LabelSelectorMethod.AND))
        .thenReturn(List.of(
            new ResourceDTO()
                .setResourceId("r-include-include")
                .setTenantId("t-1")
                .setAssociatedWithEnvoy(true)
                .setEnvoyId("envoy-r-include-include"),
            new ResourceDTO()
                // a resource that is newly selected and newly binds since it is not excluded
                .setResourceId("r-absent-include")
                .setTenantId("t-1")
                .setAssociatedWithEnvoy(true)
                .setEnvoyId("envoy-r-absent-include"),
            new ResourceDTO()
                // a resource that is newly selected, but should be excluded by the monitor still
                .setResourceId("r-absent-exclude")
                .setTenantId("t-1")
                .setAssociatedWithEnvoy(true)
                .setEnvoyId("envoy-r-absent-exclude")
        ));

    setupEnvoyResourceManagement();

    setupMonitorContentRenderer();

    // use a regular create to save the original monitor and perform bindings
    final Monitor original = monitorManagement.createMonitor(
        "t-1",
        new MonitorCU()
            // setup the exclusion that comes into play after labels get changed
            .setExcludedResourceIds(Set.of("r-absent-Exclude"))
            .setLabelSelector(Map.of("stage", "before"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setMonitorType(MonitorType.cpu)
            .setAgentType(AgentType.TELEGRAF)
            .setInterval(Duration.ofSeconds(60))
            .setContent("{}")
    );

    // sanity check bindings
    final Page<BoundMonitor> origBound = boundMonitorRepository
        .findAllByMonitor_IdAndMonitor_TenantId(original.getId(), "t-1", Pageable.unpaged());
    assertThat(origBound).extracting(BoundMonitor::getResourceId)
        .containsExactlyInAnyOrder("r-include-include");

    // and verify up to this point
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("envoy-r-include-include")
    );

    // EXECUTE

    monitorManagement.updateMonitor(
        "t-1",
        original.getId(),
        new MonitorCU()
            .setLabelSelector(Map.of("stage", "after"))
            .setMonitorType(MonitorType.cpu)
            .setAgentType(AgentType.TELEGRAF)
    );

    // VERIFY

    final Page<BoundMonitor> updatedBound = boundMonitorRepository
        .findAllByMonitor_IdAndMonitor_TenantId(original.getId(), "t-1", Pageable.unpaged());
    assertThat(updatedBound).extracting(BoundMonitor::getResourceId)
        .containsExactlyInAnyOrder("r-include-include", "r-absent-include");

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("envoy-r-include-include")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("envoy-r-absent-include")
    );
    // and never for the absent-exclude one
    verify(monitorEventProducer, never()).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("envoy-r-absent-exclude")
    );

    verify(resourceApi)
        .getResourcesWithLabels("t-1", Map.of("stage", "before"), LabelSelectorMethod.AND);
    verify(resourceApi)
        .getResourcesWithLabels("t-1", Map.of("stage", "after"), LabelSelectorMethod.AND);

    verify(envoyResourceManagement).getOne("t-1", "r-absent-include");
    verify(envoyResourceManagement, never()).getOne("t-1", "r-absent-exclude");

    verifyNoMoreInteractions(resourceApi, envoyResourceManagement, monitorEventProducer);
  }

  @Test
  public void testResourceEvent() throws InvalidTemplateException {
    final UUID excludingMonitorId = entityManager.persistAndGetId(
        new Monitor()
            // JPA needs a mutable collection
            .setExcludedResourceIds(new HashSet<>(Set.of("r-NEW")))
            .setTenantId("t-1")
            .setMonitorType(MonitorType.cpu)
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setAgentType(AgentType.TELEGRAF)
            .setInterval(Duration.ofSeconds(60))
            .setLabelSelector(new HashMap<>(Map.of("testing", "true")))
            .setContent("{}"),
        UUID.class
    );
    final UUID inclusiveMonitorId = entityManager.persistAndGetId(
        new Monitor()
            .setTenantId("t-1")
            .setMonitorType(MonitorType.cpu)
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setAgentType(AgentType.TELEGRAF)
            .setInterval(Duration.ofSeconds(60))
            .setLabelSelector(new HashMap<>(Map.of("testing", "true")))
            .setContent("{}"),
        UUID.class
    );

    entityManager.persist(
        new Resource()
            .setResourceId("r-new")
            .setAssociatedWithEnvoy(true)
            .setTenantId("t-1")
            // Hibernate needs a mutable map
            .setLabels(new HashMap<>(Map.of("testing", "true")))
            .setPresenceMonitoringEnabled(false)
    );

    setupEnvoyResourceManagement();
    setupMonitorContentRenderer();

    // EXECUTE

    monitorManagement.handleResourceChangeEvent(
        new ResourceEvent()
            .setTenantId("t-1")
            .setResourceId("r-new")
    );

    // VERIFY

    final List<BoundMonitor> bound = boundMonitorRepository
        .findAllByMonitor_TenantIdAndMonitor_IdIn(
            "t-1", List.of(excludingMonitorId, inclusiveMonitorId));

    assertThat(bound).hasSize(1);
    assertThat(bound).extracting(boundMonitor -> boundMonitor.getMonitor().getId())
        .containsExactlyInAnyOrder(inclusiveMonitorId);
  }

  @Test
  public void testUpdate_disallowExclusionAfterResourceId() {
    final UUID monitorId = entityManager.persistAndGetId(
        new Monitor()
            .setTenantId("t-1")
            .setResourceId("r-SPECIFIC")
            .setMonitorType(MonitorType.cpu)
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setAgentType(AgentType.TELEGRAF)
            .setInterval(Duration.ofSeconds(60))
            .setContent("{}"),
        UUID.class
    );

    assertThatThrownBy(() -> {
      monitorManagement.updateMonitor("t-1", monitorId,
          new MonitorCU()
          .setExcludedResourceIds(Set.of("r-excluded"))
      );
    }).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testUpdate_disallowResourceIdAfterExclusion() {
    final UUID monitorId = entityManager.persistAndGetId(
        new Monitor()
            .setTenantId("t-1")
            .setExcludedResourceIds(new HashSet<>(Set.of("r-EXCLUDED")))
            .setMonitorType(MonitorType.cpu)
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setAgentType(AgentType.TELEGRAF)
            .setInterval(Duration.ofSeconds(60))
            .setContent("{}"),
        UUID.class
    );

    assertThatThrownBy(() -> {
      monitorManagement.updateMonitor("t-1", monitorId,
          new MonitorCU()
              .setResourceId("r-specific")
      );
    }).isInstanceOf(IllegalArgumentException.class);
  }

  private void setupMonitorContentRenderer() throws InvalidTemplateException {
    when(monitorContentRenderer.render(any(), any()))
        // just return the given content
        .then(invocationOnMock -> invocationOnMock.getArgument(0));
  }

  private void setupEnvoyResourceManagement() {
    when(envoyResourceManagement.getOne(any(), any()))
        .then(invocationOnMock -> CompletableFuture.completedFuture(
            new ResourceInfo()
                .setTenantId(invocationOnMock.getArgument(0))
                .setResourceId(invocationOnMock.getArgument(1))
                .setEnvoyId("envoy-" + invocationOnMock.getArgument(1))
        ));
  }
}
