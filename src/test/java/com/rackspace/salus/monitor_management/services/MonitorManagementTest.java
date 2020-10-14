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

import static com.rackspace.salus.common.util.SpringResourceUtils.readContent;
import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.createPrivateZone;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.rackspace.salus.monitor_management.config.DatabaseConfig;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.errors.InvalidTemplateException;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.RenderedMonitorTemplate;
import com.rackspace.salus.monitor_management.web.validator.ValidUpdateMonitor;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.MetadataPolicy;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.entities.Resource;
import com.rackspace.salus.telemetry.entities.Zone;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import com.rackspace.salus.telemetry.repositories.BoundMonitorRepository;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import com.rackspace.salus.telemetry.repositories.ResourceRepository;
import com.rackspace.salus.test.EnableTestContainersDatabase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.EntityManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MultiValueMap;
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
    DatabaseConfig.class,
    ZoneAllocationResolverFactory.class,
    ZonesProperties.class,
    SimpleMeterRegistry.class,
})
@TestPropertySource(properties = {
    "salus.services.resourceManagementUrl=http://this-is-a-non-null-value",
    "salus.services.policyManagementUrl=http://this-is-a-non-null-value"
})
public class MonitorManagementTest {

  private static final String DEFAULT_ENVOY_ID = "env1";
  private static final String DEFAULT_RESOURCE_ID = "os:LINUX";
  // A timestamp to be used in tests that translates to "1970-01-02T03:46:40Z"
  private static final Instant DEFAULT_TIMESTAMP = Instant.ofEpochSecond(100000);

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

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

  @MockBean
  ZoneAllocationResolver zoneAllocationResolver;

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

  private Monitor currentMonitor;

  @Captor
  private ArgumentCaptor<List<BoundMonitor>> captorOfBoundMonitorList;

  @Before
  public void setUp() {
    Monitor monitor = new Monitor()
        .setTenantId("abcde")
        .setMonitorName("mon1")
        .setMonitorType(MonitorType.cpu)
        .setLabelSelector(Collections.singletonMap("os", "LINUX"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setContent("content1")
        .setAgentType(AgentType.FILEBEAT)
        .setInterval(Duration.ofSeconds(60));
    monitorRepository.save(monitor);
    currentMonitor = monitor;

    ResourceEvent resourceEvent = new ResourceEvent()
        .setTenantId("abcde")
        .setResourceId(DEFAULT_RESOURCE_ID);

    ResourceInfo resourceInfo = new ResourceInfo()
        .setTenantId("abcde")
        .setResourceId(DEFAULT_RESOURCE_ID)
        .setLabels(Collections.singletonMap("os", "LINUX"))
        .setEnvoyId(DEFAULT_ENVOY_ID);

    when(envoyResourceManagement.getOne(anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(resourceInfo));

    List<ResourceDTO> resourceList = new ArrayList<>();
    resourceList.add(new ResourceDTO()
        .setResourceId(resourceEvent.getResourceId())
        .setLabels(resourceInfo.getLabels())
        .setAssociatedWithEnvoy(true)
        .setTenantId("t-1")
        .setEnvoyId(DEFAULT_ENVOY_ID)
    );

    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);
  }

  @After
  public void tearDown() throws Exception {
    // transactional rollback should take care of purging test data, but do a deleteAll to be sure
    monitorRepository.deleteAll();
    entityManager.flush();
  }

  private void createMonitors(int count) {
    for (int i = 0; i < count; i++) {
      String tenantId = RandomStringUtils.randomAlphanumeric(10);
      MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
      // limit to local/agent monitors only
      create.setSelectorScope(ConfigSelectorScope.LOCAL);
      create.setZones(Collections.emptyList());
      create.setLabelSelectorMethod(LabelSelectorMethod.AND);
      create.setMonitorType(MonitorType.cpu);
      create.setInterval(Duration.ofSeconds(60));
      monitorManagement.createMonitor(tenantId, create);
    }
  }

  private void createMonitors(int count, String resourceId) {
    for (int i = 0; i < count; i++) {
      String tenantId = RandomStringUtils.randomAlphanumeric(10);
      MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
      // limit to local/agent monitors only
      create.setSelectorScope(ConfigSelectorScope.LOCAL);
      create.setZones(Collections.emptyList());
      create.setLabelSelectorMethod(LabelSelectorMethod.AND);
      create.setResourceId(resourceId);
      create.setMonitorType(MonitorType.cpu);
      create.setInterval(Duration.ofSeconds(60));
      monitorManagement.createMonitor(tenantId, create);
    }
  }

  private void createMonitorsForTenant(int count, String tenantId) {
    for (int i = 0; i < count; i++) {
      MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
      create.setSelectorScope(ConfigSelectorScope.LOCAL);
      create.setZones(Collections.emptyList());
      create.setLabelSelectorMethod(LabelSelectorMethod.AND);
      create.setMonitorType(MonitorType.cpu);
      create.setInterval(Duration.ofSeconds(60));
      create.setResourceId(null);
      monitorManagement.createMonitor(tenantId, create);
    }
  }

  private void createMonitorsForTenant(int count, String tenantId, Map<String, String> labels) {
    for (int i = 0; i < count; i++) {
      MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
      create.setSelectorScope(ConfigSelectorScope.LOCAL);
      create.setZones(Collections.emptyList());
      create.setLabelSelectorMethod(LabelSelectorMethod.AND);
      create.setLabelSelector(labels);
      create.setMonitorType(MonitorType.cpu);
      create.setInterval(Duration.ofSeconds(60));
      create.setResourceId(null);
      monitorManagement.createMonitor(tenantId, create);
    }
  }

  private Monitor persistNewMonitor(
      String tenantId, Map<String, String> labelSelector) {
    return monitorRepository.save(
        new Monitor()
            .setTenantId(tenantId)
            .setAgentType(AgentType.TELEGRAF)
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setLabelSelector(labelSelector)
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.cpu)
            .setInterval(Duration.ofSeconds(60))
            .setContent("{}")
    );
  }

  @Test
  public void testGetMonitor() {
    Optional<Monitor> m = monitorManagement.getMonitor("abcde", currentMonitor.getId());

    assertTrue(m.isPresent());
    assertThat(m.get().getId(), notNullValue());
    assertThat(m.get().getLabelSelector(), hasEntry("os", "LINUX"));
    assertThat(m.get().getContent(), equalTo(currentMonitor.getContent()));
    assertThat(m.get().getAgentType(), equalTo(currentMonitor.getAgentType()));
  }

  @Test
  public void testGetMonitorForUnauthorizedTenant() {
    String unauthorizedTenantId = RandomStringUtils.randomAlphanumeric(10);
    Optional<Monitor> monitor = monitorManagement.getMonitor(unauthorizedTenantId, currentMonitor.getId());
    assertFalse(monitor.isPresent());
  }

  @Test
  public void testCreateNewMonitor_usingLabelSelector() {
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setResourceId(null);
    create.setLabelSelectorMethod(LabelSelectorMethod.AND);
    create.setInterval(Duration.ofSeconds(60));

    String tenantId = RandomStringUtils.randomAlphanumeric(10);

    final ResourceDTO resource = podamFactory.manufacturePojo(ResourceDTO.class);
    when(resourceApi.getResourcesWithLabels(anyString(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(List.of(resource));
    when(envoyResourceManagement.getOne(anyString(), anyString()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ResourceInfo()
                    .setResourceId(resource.getResourceId())
                    .setEnvoyId(resource.getEnvoyId())));

    Monitor returned = monitorManagement.createMonitor(tenantId, create);

    assertThat(returned.getId(), notNullValue());
    assertThat(returned.getMonitorName(), equalTo(create.getMonitorName()));
    assertThat(returned.getContent(), equalTo(create.getContent()));
    assertThat(returned.getAgentType(), equalTo(create.getAgentType()));

    assertThat(returned.getLabelSelector().size(), greaterThan(0));
    assertTrue(Maps.difference(create.getLabelSelector(), returned.getLabelSelector()).areEqual());

    Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, returned.getId());

    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getMonitorName(), equalTo(returned.getMonitorName()));
    assertTrue(Maps.difference(returned.getLabelSelector(), retrieved.get().getLabelSelector()).areEqual());
    assertThat(retrieved.get().getResourceId(), nullValue());


    verify(resourceApi).getResourcesWithLabels(tenantId, create.getLabelSelector(), create.getLabelSelectorMethod());
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId(resource.getEnvoyId())
    );

    verifyNoMoreInteractions(monitorEventProducer, envoyResourceManagement,
        resourceApi, resourceRepository);
  }

  @Test
  public void testCreateNewMonitor_usingResourceId() {
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setLabelSelector(null);
    create.setInterval(Duration.ofSeconds(60));

    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    String resourceId = RandomStringUtils.randomAlphanumeric(10);
    create.setResourceId(resourceId);
    final Resource resource = podamFactory.manufacturePojo(Resource.class);
    resource.setResourceId(resourceId);
    resource.setTenantId(tenantId);
    when(resourceRepository.findByTenantIdAndResourceId(anyString(), any()))
        .thenReturn(Optional.of(resource));
    when(envoyResourceManagement.getOne(tenantId, resource.getResourceId()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ResourceInfo()
                    .setResourceId(resource.getResourceId())
                    .setEnvoyId("e-1")));

    Monitor returned = monitorManagement.createMonitor(tenantId, create);

    assertThat(returned.getId(), notNullValue());
    assertThat(returned.getMonitorName(), equalTo(create.getMonitorName()));
    assertThat(returned.getContent(), equalTo(create.getContent()));
    assertThat(returned.getAgentType(), equalTo(create.getAgentType()));
    assertThat(returned.getResourceId(), equalTo(create.getResourceId()));
    assertThat(returned.getLabelSelector(), equalTo(create.getLabelSelector()));

    Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, returned.getId());

    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getMonitorName(), equalTo(returned.getMonitorName()));
    assertThat(retrieved.get().getLabelSelector(), nullValue());
    assertThat(retrieved.get().getResourceId(), equalTo(returned.getResourceId()));

    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, create.getResourceId());
    verify(envoyResourceManagement).getOne(tenantId, resource.getResourceId());
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-1")
    );

    verifyNoMoreInteractions(monitorEventProducer, envoyResourceManagement,
        resourceApi, resourceRepository);
  }

  @Test
  public void testCreateNewMonitor_interval() {
    final Duration interval = Duration.ofSeconds(12);

    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setLabelSelector(null);
    create.setInterval(interval);

    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    String resourceId = RandomStringUtils.randomAlphanumeric(10);

    final Resource resource = podamFactory.manufacturePojo(Resource.class);
    resource.setResourceId(resourceId);
    when(resourceRepository.findByTenantIdAndResourceId(anyString(), any()))
        .thenReturn(Optional.of(resource));
    when(envoyResourceManagement.getOne(anyString(), anyString()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ResourceInfo()
                    .setResourceId(resource.getResourceId())
                    .setEnvoyId("e-1")));

    Monitor returned = monitorManagement.createMonitor(tenantId, create);

    assertThat(returned.getInterval(), equalTo(interval));

    Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, returned.getId());

    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getInterval(), equalTo(interval));
  }

  @Test
  public void testCreateNewMonitor_EmptyLabelSelector() {
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setLabelSelector(Collections.emptyMap());
    create.setInterval(Duration.ofSeconds(60));

    String tenantId = RandomStringUtils.randomAlphanumeric(10);

    Monitor returned = monitorManagement.createMonitor(tenantId, create);

    assertThat(returned.getId(), notNullValue());
    assertThat(returned.getMonitorName(), equalTo(create.getMonitorName()));
    assertThat(returned.getContent(), equalTo(create.getContent()));
    assertThat(returned.getAgentType(), equalTo(create.getAgentType()));

    assertThat(returned.getLabelSelector(), notNullValue());
    assertThat(returned.getLabelSelector().size(), equalTo(0));
    assertTrue(Maps.difference(create.getLabelSelector(), returned.getLabelSelector()).areEqual());

    Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, returned.getId());

    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getMonitorName(), equalTo(returned.getMonitorName()));
    assertTrue(Maps.difference(returned.getLabelSelector(), retrieved.get().getLabelSelector()).areEqual());
  }

  @Test
  public void testCreateNewMonitor_nullLabelSelectorMethod() {
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setLabelSelector(Collections.emptyMap());
    create.setLabelSelectorMethod(null);
    create.setInterval(Duration.ofSeconds(60));

    String tenantId = RandomStringUtils.randomAlphanumeric(10);

    Monitor returned = monitorManagement.createMonitor(tenantId, create);

    assertThat(returned.getId(), notNullValue());
    assertThat(returned.getMonitorName(), equalTo(create.getMonitorName()));
    assertThat(returned.getContent(), equalTo(create.getContent()));
    assertThat(returned.getAgentType(), equalTo(create.getAgentType()));

    // The default value should be set
    assertThat(returned.getLabelSelectorMethod(), equalTo(LabelSelectorMethod.AND));
    assertThat(returned.getLabelSelector(), notNullValue());
    assertThat(returned.getLabelSelector().size(), equalTo(0));
    assertTrue(Maps.difference(create.getLabelSelector(), returned.getLabelSelector()).areEqual());

    Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, returned.getId());

    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getMonitorName(), equalTo(returned.getMonitorName()));
    assertTrue(Maps.difference(returned.getLabelSelector(), retrieved.get().getLabelSelector()).areEqual());
  }

  @Test
  public void testCreateNewMonitor_LocalWithZones() {
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    // zones gets populated by podam
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    String tenantId = RandomStringUtils.randomAlphanumeric(10);

    exceptionRule.expect(IllegalArgumentException.class);
    exceptionRule.expectMessage("Local monitors cannot have zones");
    monitorManagement.createMonitor(tenantId, create);

    verifyNoMoreInteractions(envoyResourceManagement, resourceApi, boundMonitorRepository);
  }

  @Test
  public void testCreateNewMonitor_InvalidTemplate_Local() {
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setContent("value=${does_not_exist}");
    create.setResourceId("");
    create.setInterval(Duration.ofSeconds(60));
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    create.setLabelSelectorMethod(LabelSelectorMethod.AND);

    Monitor returned = monitorManagement.createMonitor(tenantId, create);
    assertThat(returned.getId(), notNullValue());

    Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, returned.getId());
    assertTrue(retrieved.isPresent());

    verify(resourceApi).getResourcesWithLabels(tenantId, create.getLabelSelector(), create.getLabelSelectorMethod());

    // ...but no bindings created, verified by no interaction with boundMonitorRepository
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi,
        zoneManagement);
  }

  @Test
  public void testCreateNewMonitor_InvalidTemplate_Remote() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);

    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.REMOTE);
    create.setResourceId("");
    create.setInterval(Duration.ofSeconds(60));
    create.setContent("value=${does_not_exist}");

    //noinspection unchecked
    List<Zone> zones = podamFactory.manufacturePojo(ArrayList.class, Zone.class);
    create.setZones(zones.stream().map(Zone::getName).distinct().filter(Objects::nonNull).collect(Collectors.toList()));
    create.setLabelSelector(Collections.emptyMap());
    create.setLabelSelectorMethod(LabelSelectorMethod.AND);
    create.setInterval(Duration.ofSeconds(60));

    when(zoneManagement.getAvailableZonesForTenant(any(), any()))
        .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

    Monitor returned = monitorManagement.createMonitor(tenantId, create);
    assertThat(returned.getId(), notNullValue());

    Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, returned.getId());
    assertTrue(retrieved.isPresent());

    verify(resourceApi).getResourcesWithLabels(tenantId, create.getLabelSelector(), create.getLabelSelectorMethod());

    verify(zoneManagement).getAvailableZonesForTenant(eq(tenantId), any());

    // ...but no bindings created, verified by no interaction with boundMonitorRepository
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi,
        zoneManagement, zoneStorage);
  }

  @Test
  public void testCreateNewMonitorInvalidZone() {
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.REMOTE);
    String tenantId = RandomStringUtils.randomAlphanumeric(10);

    assertThat(create.getZones().size(), greaterThan(0));

    when(zoneManagement.getAvailableZonesForTenant(any(), any()))
        .thenReturn(Page.empty());

    exceptionRule.expect(IllegalArgumentException.class);
    exceptionRule.expectMessage("Invalid zone(s) provided:");
    monitorManagement.createMonitor(tenantId, create);
  }

  @Test
  public void testCreateNewMonitor_validZones() {
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    List<Zone> zones = podamFactory.manufacturePojo(ArrayList.class, Zone.class);
    List<String> zoneIds = zones.stream().map(Zone::getName).distinct().filter(Objects::nonNull).collect(Collectors.toList());
    create.setZones(zoneIds);
    create.setLabelSelector(Collections.emptyMap());
    create.setSelectorScope(ConfigSelectorScope.REMOTE);
    create.setInterval(Duration.ofSeconds(60));

    String tenantId = RandomStringUtils.randomAlphanumeric(10);

    when(zoneManagement.getAvailableZonesForTenant(any(), any()))
        .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

    Monitor monitor = monitorManagement.createMonitor(tenantId, create);

    assertThat(monitor.getZones(), hasSize(5));
    Assertions.assertThat(monitor.getZones()).containsExactlyInAnyOrderElementsOf(zoneIds);
  }

  @Test
  public void testGetAll() {
    Random random = new Random();
    int totalMonitors = random.nextInt(150 - 50) + 50;
    int pageSize = 10;

    Pageable page = PageRequest.of(0, pageSize);
    Page<Monitor> result = monitorManagement.getAllMonitors(page);

    assertThat(result.getTotalElements(), equalTo(1L));

    // There is already one monitor created as default
    createMonitors(totalMonitors - 1);

    page = PageRequest.of(0, 10);
    result = monitorManagement.getAllMonitors(page);

    assertThat(result.getTotalElements(), equalTo((long) totalMonitors));
    assertThat(result.getTotalPages(), equalTo((totalMonitors + pageSize - 1) / pageSize));
  }

  @Test
  public void testGetAllForTenant_paged() {
    Random random = new Random();
    int totalMonitors = random.nextInt(150 - 50) + 50;
    int pageSize = 10;
    String tenantId = RandomStringUtils.randomAlphanumeric(10);

    Pageable page = PageRequest.of(0, pageSize);
    Page<Monitor> result = monitorManagement.getAllMonitors(page);

    assertThat(result.getTotalElements(), equalTo(1L));

    createMonitorsForTenant(totalMonitors, tenantId);

    page = PageRequest.of(0, 10);
    result = monitorManagement.getMonitors(tenantId, page);

    assertThat(result.getTotalElements(), equalTo((long) totalMonitors));
    assertThat(result.getTotalPages(), equalTo((totalMonitors + pageSize - 1) / pageSize));
  }

  @Test
  public void testGetMonitorsAsStream() {
    int totalMonitors = 100;

    // There is already one monitor created as default
    createMonitors(totalMonitors - 1);

    Stream s = monitorManagement.getMonitorsAsStream();
    assertThat(s.count(), equalTo((long) totalMonitors));
  }

  @Test
  public void testUpdateExistingMonitor_labelsChanged() {
    reset(envoyResourceManagement, resourceApi);

    // r2 will be the one that remains throughout the label change
    Map<String, String> r2labels = new HashMap<>();
    r2labels.put("old", "yes");
    r2labels.put("new", "yes");
    final ResourceDTO r2 = new ResourceDTO()
        .setLabels(r2labels)
        .setResourceId("r-2")
        .setAssociatedWithEnvoy(true)
        .setTenantId("t-1");

    Map<String, String> r3labels = new HashMap<>();
    r3labels.put("new", "yes");
    final ResourceDTO r3 = new ResourceDTO()
        .setLabels(r3labels)
        .setResourceId("r-3")
        .setAssociatedWithEnvoy(true)
        .setTenantId("t-1");
    when(envoyResourceManagement.getOne("t-1", "r-3"))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ResourceInfo().setResourceId("r-3").setEnvoyId("e-3")
            )
        );

    when(resourceApi.getResourcesWithLabels("t-1", Collections.singletonMap("new", "yes"), LabelSelectorMethod.AND))
        .thenReturn(Arrays.asList(r2, r3));

    final Map<String, String> oldLabelSelector = new HashMap<>();
    oldLabelSelector.put("old", "yes");
    final Monitor monitor = new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.cpu)
        .setContent("{}")
        .setTenantId("t-1")
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelector(oldLabelSelector)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setInterval(Duration.ofSeconds(60));
    entityManager.persist(monitor);
    entityManager.flush();

    // simulate that r1 and r2 are already bound to monitor due to selector old=yes
    final BoundMonitor bound1 = new BoundMonitor()
        .setMonitor(monitor)
        .setResourceId("r-1")
        .setZoneName("")
        .setRenderedContent("{}");
    when(boundMonitorRepository
        .findAllByMonitor_IdAndResourceIdIn(monitor.getId(), Collections.singletonList("r-1")))
        .thenReturn(Collections.singletonList(bound1));

    when(boundMonitorRepository.findResourceIdsBoundToMonitor(any()))
        .thenReturn(new HashSet<>(Arrays.asList("r-1", "r-2")));

    // EXECUTE

    final Map<String, String> newLabelSelector = new HashMap<>();
    newLabelSelector.put("new", "yes");
    MonitorCU update = new MonitorCU()
        .setLabelSelector(newLabelSelector);

    final Monitor updatedMonitor = monitorManagement.updateMonitor("t-1", monitor.getId(), update);

    // VERIFY

    assertThat(updatedMonitor.getId(), equalTo(monitor.getId()));
    assertThat(updatedMonitor.getAgentType(), equalTo(monitor.getAgentType()));
    assertThat(updatedMonitor.getContent(), equalTo("{}"));
    assertThat(updatedMonitor.getTenantId(), equalTo("t-1"));
    assertThat(updatedMonitor.getSelectorScope(), equalTo(ConfigSelectorScope.LOCAL));
    assertThat(updatedMonitor.getLabelSelector(), equalTo(newLabelSelector));
    // The method was not specified so the existing one should remain instead of being set back to the default.
    assertThat(updatedMonitor.getLabelSelectorMethod(), equalTo(LabelSelectorMethod.AND));

    verify(resourceApi).getResourcesWithLabels("t-1", Collections.singletonMap("new", "yes"), LabelSelectorMethod.AND);

    verify(boundMonitorRepository).findResourceIdsBoundToMonitor(monitor.getId());

    verify(boundMonitorRepository).saveAll(Collections.singletonList(
        new BoundMonitor()
            .setMonitor(monitor)
            .setTenantId("t-1")
            .setResourceId("r-3")
            .setEnvoyId("e-3")
            .setZoneName("")
            .setRenderedContent("{}")
    ));

    verify(boundMonitorRepository).deleteAll(Collections.singletonList(
        new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setZoneName("")
            .setRenderedContent("{}")
    ));

    verify(boundMonitorRepository)
        .findAllByMonitor_IdAndResourceIdIn(monitor.getId(), Collections.singletonList("r-1"));
    verify(boundMonitorRepository)
        .findAllByMonitor_IdAndResourceId(monitor.getId(), "r-3");

    verify(envoyResourceManagement).getOne("t-1", "r-3");

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-3")
    );

    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi,
        zoneStorage, monitorEventProducer);
  }

  @Test
  public void testUpdateExistingMonitor_labelSelectorMethodChanged() {
    reset(envoyResourceManagement, resourceApi);

    // The labels to set on the monitor
    Map<String, String> labels = Map.of("os", "linux", "env", "dev");

    final ResourceDTO windowsResource = new ResourceDTO()
        .setLabels(Map.of("os", "windows", "env", "dev"))
        .setResourceId("r-windows")
        .setAssociatedWithEnvoy(true)
        .setTenantId("t-1");

    final ResourceDTO linuxResource = new ResourceDTO()
        .setLabels(labels)
        .setResourceId("r-linux")
        .setAssociatedWithEnvoy(true)
        .setTenantId("t-1");

    final Monitor monitor = new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.cpu)
        .setContent("{}")
        .setTenantId("t-1")
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelector(labels)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setInterval(Duration.ofSeconds(60));
    entityManager.persist(monitor);
    entityManager.flush();

    // Both resources will be relevant when method is changed to OR
    when(resourceApi.getResourcesWithLabels("t-1", labels, LabelSelectorMethod.OR))
        .thenReturn(List.of(linuxResource, windowsResource));

    // An envoy will have to be found for the windows resource when method is changed to OR
    when(envoyResourceManagement.getOne("t-1", "r-windows"))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ResourceInfo().setResourceId("r-windows").setEnvoyId("e-1")
            )
        );

    when(boundMonitorRepository.findResourceIdsBoundToMonitor(any()))
        .thenReturn(Set.of("r-linux"));
    when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(any(), anyString()))
        .thenReturn(Collections.emptyList());

    // EXECUTE

    MonitorCU update = new MonitorCU()
        .setLabelSelectorMethod(LabelSelectorMethod.OR);

    final Monitor updatedMonitor = monitorManagement.updateMonitor("t-1", monitor.getId(), update);

    // VERIFY

    assertThat(updatedMonitor.getId(), equalTo(monitor.getId()));
    assertThat(updatedMonitor.getAgentType(), equalTo(monitor.getAgentType()));
    assertThat(updatedMonitor.getContent(), equalTo("{}"));
    assertThat(updatedMonitor.getTenantId(), equalTo("t-1"));
    assertThat(updatedMonitor.getSelectorScope(), equalTo(ConfigSelectorScope.LOCAL));
    assertThat(updatedMonitor.getLabelSelector(), equalTo(labels));
    assertThat(updatedMonitor.getLabelSelectorMethod(), equalTo(LabelSelectorMethod.OR));

    verify(resourceApi).getResourcesWithLabels("t-1", labels, LabelSelectorMethod.OR);

    verify(boundMonitorRepository).saveAll(Collections.singletonList(
        new BoundMonitor()
            .setMonitor(monitor)
            .setTenantId("t-1")
            .setResourceId("r-windows")
            .setEnvoyId("e-1")
            .setZoneName("")
            .setRenderedContent("{}")
    ));
    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(monitor.getId(), "r-windows");

    verify(boundMonitorRepository).findResourceIdsBoundToMonitor(monitor.getId());
    verify(envoyResourceManagement).getOne("t-1", "r-windows");
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-1")
    );

    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi,
        zoneStorage, monitorEventProducer);
  }

  @Test
  public void testUpdateExistingMonitor_contentChanged() {
    reset(envoyResourceManagement, resourceApi);

    // This resource will result in a change to rendered content
    final Map<String, String> r1metadata = new HashMap<>();
    r1metadata.put("ping_ip", "something_else");
    r1metadata.put("address", "localhost");

    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    String resourceId1 = RandomStringUtils.randomAlphanumeric(10);
    String resourceId2 = RandomStringUtils.randomAlphanumeric(10);

    final Resource resource1 = podamFactory.manufacturePojo(Resource.class);
    resource1.setResourceId(resourceId1);
    resource1.setTenantId(tenantId);
    resource1.setLabels(Collections.singletonMap("os", "linux"));
    resource1.setMetadata(r1metadata);

    when(resourceRepository.findByTenantIdAndResourceId(tenantId, resourceId1)).thenReturn(Optional.of(resource1));

    // ...and this resource will NOT since both metadata values are the same
    final Map<String, String> r2metadata = new HashMap<>();
    r2metadata.put("ping_ip", "localhost");
    r2metadata.put("address", "localhost");

    final Resource resource2 = podamFactory.manufacturePojo(Resource.class);
    resource2.setResourceId(resourceId2);
    resource2.setTenantId(tenantId);
    resource2.setLabels(Collections.singletonMap("os", "linux"));
    resource2.setMetadata(r1metadata);

    when(resourceRepository.findByTenantIdAndResourceId(tenantId, resourceId2)).thenReturn(Optional.of(resource2));

    final Monitor monitor = new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setContent("address=${resource.metadata.ping_ip}")
        .setTenantId(tenantId)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setMonitorType(MonitorType.ping)
        .setLabelSelector(Collections.singletonMap("os", "linux"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setInterval(Duration.ofSeconds(60));
    entityManager.persist(monitor);

    final BoundMonitor bound1 = new BoundMonitor()
        .setMonitor(monitor)
        .setTenantId(tenantId)
        .setResourceId(resourceId1)
        .setEnvoyId("e-1")
        .setZoneName("z-1")
        .setRenderedContent("address=something_else");
    entityManager.persist(bound1);

    final BoundMonitor bound2 = new BoundMonitor()
        .setMonitor(monitor)
        .setTenantId(tenantId)
        .setResourceId(resourceId2)
        .setEnvoyId("e-2")
        .setZoneName("z-1")
        .setRenderedContent("address=localhost");
    entityManager.persist(bound2);

    // same resource r-2, but different zone to ensure query-by-resource is normalize to one query each
    final BoundMonitor bound3 = new BoundMonitor()
        .setMonitor(monitor)
        .setTenantId(tenantId)
        .setResourceId(resourceId2)
        .setEnvoyId("e-3")
        .setZoneName("z-2")
        .setRenderedContent("address=localhost");
    entityManager.persist(bound3);

    when(boundMonitorRepository.findAllByMonitor_Id(monitor.getId()))
        .thenReturn(Arrays.asList(bound1, bound2, bound3));

    // EXECUTE

    final MonitorCU update = new MonitorCU()
        .setContent("address=${resource.metadata.address}");
    final Monitor updatedMonitor = monitorManagement.updateMonitor(tenantId, monitor.getId(), update);

    // VERIFY

    org.assertj.core.api.Assertions.assertThat(Collections.singleton(updatedMonitor))
        .usingElementComparatorIgnoringFields("createdTimestamp", "updatedTimestamp")
        .containsExactly(
            new Monitor()
                .setId(monitor.getId())
                .setAgentType(AgentType.TELEGRAF)
                .setMonitorType(MonitorType.ping)
                .setContent("address=${resource.metadata.address}")
                .setMonitorMetadataFields(List.of("monitorName"))
                .setTenantId(tenantId)
                .setSelectorScope(ConfigSelectorScope.REMOTE)
                .setLabelSelector(Collections.singletonMap("os", "linux"))
                .setLabelSelectorMethod(LabelSelectorMethod.AND)
                .setInterval(Duration.ofSeconds(60)));

    verify(boundMonitorRepository).findAllByMonitor_Id(monitor.getId());

    verify(boundMonitorRepository).saveAll(captorOfBoundMonitorList.capture());
    org.assertj.core.api.Assertions.assertThat(captorOfBoundMonitorList.getValue())
        .usingElementComparatorIgnoringFields("createdTimestamp", "updatedTimestamp")
        .containsExactly(
            new BoundMonitor()
                .setMonitor(monitor)
                .setTenantId(tenantId)
                .setResourceId(resourceId1)
                .setEnvoyId("e-1")
                .setZoneName("z-1")
                .setRenderedContent("address=localhost")
        );

    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resourceId1);
    // even though two bindings for r-2, the queries were grouped by resource and only one call here
    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resourceId2);

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-1")
    );

    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi,
        zoneStorage, monitorEventProducer, resourceRepository);
  }

  @Test
  public void testUpdateExistingMonitor_intervalChanged() {
    final Duration initialInterval = Duration.ofSeconds(30);
    final Duration updatedInterval = Duration.ofSeconds(42);

    reset(envoyResourceManagement, resourceApi, boundMonitorRepository);

    final Monitor monitor = new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.ping)
        .setContent("address=${resource.metadata.ping_ip}")
        .setTenantId("t-1")
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setLabelSelector(Collections.singletonMap("os", "linux"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setInterval(initialInterval);
    entityManager.persist(monitor);

    final BoundMonitor bound1 = new BoundMonitor()
        .setMonitor(monitor)
        .setTenantId("t-1")
        .setResourceId("r-1")
        .setEnvoyId("e-1")
        .setZoneName("z-1")
        .setRenderedContent("address=something_else");
    entityManager.persist(bound1);

    when(boundMonitorRepository.findAllByMonitor_Id(monitor.getId()))
        .thenReturn(List.of(bound1));

    // EXECUTE

    final MonitorCU update = new MonitorCU()
        .setInterval(updatedInterval);
    final Monitor updatedMonitor = monitorManagement.updateMonitor("t-1", monitor.getId(), update);

    // VERIFY

    // verify returned entity's field
    assertThat(updatedMonitor.getInterval(), equalTo(updatedInterval));

    // and verify the stored entity
    Optional<Monitor> retrieved = monitorManagement.getMonitor("t-1", updatedMonitor.getId());
    assertTrue(retrieved.isPresent());
    assertThat(retrieved.get().getInterval(), equalTo(updatedInterval));

    verify(boundMonitorRepository).findAllByMonitor_Id(monitor.getId());

    // should NOT re-save the bound monitor...just generates an event
    verify(boundMonitorRepository, never()).saveAll(any());

    // but should still send the update event
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-1")
    );

    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi,
        zoneStorage, monitorEventProducer, resourceRepository);
  }

  @Test
  public void testUpdateExistingMonitor_resourceIdChanged() {
    // Starts with one monitor-with-resource-id bound to one resource
    // updates it to point to another resource
    // confirms monitor is updated, old binding is removed, new binding is added
    reset(envoyResourceManagement, resourceApi);
      String tenantId = RandomStringUtils.randomAlphanumeric(10);
      String resourceId1 = RandomStringUtils.randomAlphanumeric(10);
      String resourceId2 = RandomStringUtils.randomAlphanumeric(10);

    final Resource resource1 = podamFactory.manufacturePojo(Resource.class);
    resource1.setResourceId(resourceId1);
    resource1.setTenantId(tenantId);
    resource1.setLabels(Collections.singletonMap("os", "linux"));

    when(resourceRepository.findByTenantIdAndResourceId(tenantId, resourceId1)).thenReturn(Optional.of(resource1));

    when(envoyResourceManagement.getOne(tenantId, resourceId2))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ResourceInfo().setResourceId(resourceId2).setEnvoyId("e-2")
            )
        );

    final Resource resource2 = podamFactory.manufacturePojo(Resource.class);
    resource2.setResourceId(resourceId2);
    resource2.setTenantId(tenantId);
    resource2.setLabels(Collections.singletonMap("os", "linux"));
    resource2.setAssociatedWithEnvoy(true);

    when(resourceRepository.findByTenantIdAndResourceId(tenantId, resourceId2)).thenReturn(Optional.of(resource2));

    final Monitor monitor = new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.cpu)
        .setContent("static content")
        .setTenantId(tenantId)
        .setResourceId(resourceId1)
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setInterval(Duration.ofSeconds(60));
    entityManager.persist(monitor);

    final BoundMonitor bound1 = new BoundMonitor()
        .setTenantId(tenantId)
        .setMonitor(monitor)
        .setResourceId(resourceId1)
        .setZoneName("")
        .setEnvoyId("e-1");
    entityManager.persist(bound1);

    when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(monitor.getId(), resourceId1))
        .thenReturn(Collections.singletonList(bound1));

    when(boundMonitorRepository.findAllByMonitor_IdAndResourceIdIn(monitor.getId(), Collections.singletonList(resourceId1)))
        .thenReturn(Collections.singletonList(bound1));

    // EXECUTE

    final MonitorCU update = new MonitorCU()
        .setResourceId(resourceId2);
    final Monitor updatedMonitor = monitorManagement.updateMonitor(tenantId, monitor.getId(), update);

    // VERIFY
    // confirm monitor is updated
    org.assertj.core.api.Assertions.assertThat(Collections.singleton(updatedMonitor))
        .usingElementComparatorIgnoringFields("createdTimestamp", "updatedTimestamp")
        .containsExactly(
            new Monitor()
                .setId(monitor.getId())
                .setAgentType(AgentType.TELEGRAF)
                .setMonitorType(MonitorType.cpu)
                .setContent("static content")
                .setMonitorMetadataFields(List.of("monitorName"))
                .setTenantId(tenantId)
                .setSelectorScope(ConfigSelectorScope.LOCAL)
                .setResourceId(resourceId2)
                .setLabelSelectorMethod(LabelSelectorMethod.AND)
                .setInterval(Duration.ofSeconds(60)));

    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(monitor.getId(), resourceId1);
    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(monitor.getId(), resourceId2);
    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceIdIn(monitor.getId(), Collections.singletonList(resourceId1));

    // confirm new binding saved
    verify(boundMonitorRepository).saveAll(Collections.singletonList(
        new BoundMonitor()
            .setTenantId(tenantId)
            .setMonitor(monitor)
            .setResourceId(resourceId2)
            .setZoneName("")
            .setEnvoyId("e-2")
            .setRenderedContent("static content")
    ));

    // confirm old binding deleted
    verify(boundMonitorRepository).deleteAll(Collections.singletonList(bound1));

    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resourceId2);

    // confirm event sent for both old and new binding
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-1")
    );

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-2")
    );
    verify(envoyResourceManagement).getOne(tenantId, resourceId2);
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi,
        zoneStorage, monitorEventProducer);
  }

  @Test
  public void testPatchExistingMonitor_removeResourceIdSetLabels() {
    // Starts with one monitor-with-resource-id bound to one resource
    // updates it to use a label selector
    // confirms monitor is updated, old binding is removed, new bindings are added
    reset(envoyResourceManagement, resourceApi);
    final ResourceDTO r1 = new ResourceDTO()
        .setLabels(Collections.singletonMap("os", "linux"))
        .setResourceId("r-1")
        .setTenantId("t-1");

    when(envoyResourceManagement.getOne("t-1", "r-2"))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ResourceInfo().setResourceId("r-2").setEnvoyId("e-2")
            )
        );
    when(envoyResourceManagement.getOne("t-1", "r-3"))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ResourceInfo().setResourceId("r-2").setEnvoyId("e-3")
            )
        );

    // Resources that will be found by label selector
    final List<ResourceDTO> newResourcesMatched = List.of(
        new ResourceDTO()
          .setLabels(Collections.singletonMap("os", "linux"))
          .setResourceId("r-2")
          .setTenantId("t-1")
          .setAssociatedWithEnvoy(true),
        new ResourceDTO()
          .setLabels(Collections.singletonMap("os", "linux"))
          .setResourceId("r-3")
          .setTenantId("t-1")
          .setAssociatedWithEnvoy(true));

    final Monitor monitor = new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.cpu)
        .setContent("static content")
        .setTenantId("t-1")
        .setResourceId("r-1")
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setLabelSelector(null)
        .setZones(Collections.emptyList())
        .setInterval(Duration.ofSeconds(60));
    entityManager.persist(monitor);

    final BoundMonitor bound1 = new BoundMonitor()
        .setTenantId("t-1")
        .setMonitor(monitor)
        .setResourceId("r-1")
        .setZoneName("")
        .setEnvoyId("e-1");
    entityManager.persist(bound1);

    when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(monitor.getId(), "r-1"))
        .thenReturn(Collections.singletonList(bound1));

    when(boundMonitorRepository.findAllByMonitor_IdAndResourceIdIn(monitor.getId(), Collections.singletonList("r-1")))
        .thenReturn(Collections.singletonList(bound1));

    when(resourceApi.getResourcesWithLabels(any(), any(), any()))
        .thenReturn(newResourcesMatched);

    // EXECUTE

    final Map<String, String> newLabelSelector = Map.of("os", "linux");

    final MonitorCU update = new MonitorCU()
        .setResourceId(null)
        .setLabelSelector(newLabelSelector)
        // for a patch we need to set all the other values to the same as the original
        .setZones(monitor.getZones())
        .setMonitorType(monitor.getMonitorType())
        .setMonitorName(monitor.getMonitorName())
        .setContent(monitor.getContent())
        .setAgentType(monitor.getAgentType())
        .setLabelSelectorMethod(monitor.getLabelSelectorMethod())
        .setInterval(monitor.getInterval())
        .setSelectorScope(monitor.getSelectorScope())
        .setPluginMetadataFields(monitor.getPluginMetadataFields());

    final Monitor updatedMonitor = monitorManagement.updateMonitor("t-1", monitor.getId(), update, true);

    // VERIFY
    // confirm monitor is updated
    org.assertj.core.api.Assertions.assertThat(Collections.singleton(updatedMonitor))
        .usingElementComparatorIgnoringFields("createdTimestamp", "updatedTimestamp")
        .containsExactly(
            new Monitor()
                .setId(monitor.getId())
                .setAgentType(AgentType.TELEGRAF)
                .setMonitorType(MonitorType.cpu)
                .setContent("static content")
                .setMonitorMetadataFields(List.of("monitorName"))
                .setTenantId("t-1")
                .setSelectorScope(ConfigSelectorScope.LOCAL)
                .setResourceId(null)
                .setLabelSelector(newLabelSelector)
                .setLabelSelectorMethod(LabelSelectorMethod.AND)
                .setInterval(Duration.ofSeconds(60)));

    verify(resourceApi).getResourcesWithLabels("t-1", newLabelSelector, LabelSelectorMethod.AND);

    // confirm old binding deleted
    verify(boundMonitorRepository).deleteAll(Collections.singletonList(bound1));

    // confirm new binding saved
    // they are saved individually per resource not all at once.
    verify(boundMonitorRepository).saveAll(List.of(
        new BoundMonitor()
            .setTenantId("t-1")
            .setMonitor(monitor)
            .setResourceId("r-2")
            .setZoneName("")
            .setEnvoyId("e-2")
            .setRenderedContent("static content")
    ));
    verify(boundMonitorRepository).saveAll(List.of(
        new BoundMonitor()
            .setTenantId("t-1")
            .setMonitor(monitor)
            .setResourceId("r-3")
            .setZoneName("")
            .setEnvoyId("e-3")
            .setRenderedContent("static content")
    ));

    // confirm event sent for both old and new bindings
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-1")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-2")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-3")
    );
    verifyNoMoreInteractions(monitorEventProducer);
  }

  @Test
  public void testPatchExistingMonitor_removeLabelsSetResourceId() {
    // Starts with one monitor-with-label-selector bound to two resources
    // updates it to use a resourceId
    // confirms monitor is updated, old bindings are removed, new binding is added
    // This is the reverse scenario of testPatchExistingMonitor_removeResourceIdSetLabels
    reset(envoyResourceManagement, resourceApi);

    final Monitor monitor = new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.cpu)
        .setContent("static content")
        .setTenantId("t-1")
        .setResourceId(null)
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setLabelSelector(Map.of("os", "linux"))
        .setZones(Collections.emptyList())
        .setInterval(Duration.ofSeconds(60));
    entityManager.persist(monitor);

    Resource resource = new Resource()
        .setTenantId("t-1")
        .setResourceId("r-1")
        .setLabels(Collections.emptyMap())
        .setAssociatedWithEnvoy(true)
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

    List<BoundMonitor> existingBoundMonitors = List.of(
        new BoundMonitor()
          .setTenantId("t-1")
          .setMonitor(monitor)
          .setResourceId("r-2")
          .setZoneName("")
          .setEnvoyId("e-2")
          .setRenderedContent("static content"),
        new BoundMonitor()
          .setTenantId("t-1")
          .setMonitor(monitor)
          .setResourceId("r-3")
          .setZoneName("")
          .setEnvoyId("e-3")
          .setRenderedContent("static content"));


    // Called when binding new resource
    when(envoyResourceManagement.getOne("t-1", "r-1"))
        .thenReturn(
            CompletableFuture.completedFuture(
                new ResourceInfo().setResourceId("r-1").setEnvoyId("e-1")));
    when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(monitor.getId(), "r-1"))
        .thenReturn(Collections.emptyList());
    when(resourceRepository.findByTenantIdAndResourceId("t-1", "r-1"))
        .thenReturn(Optional.of(resource));

    // Called when unbinding old resources
    when(boundMonitorRepository.findResourceIdsBoundToMonitor(any()))
        .thenReturn(new HashSet<>(Arrays.asList("r-1", "r-2", "r-3")));
    when(boundMonitorRepository.findAllByMonitor_IdAndResourceIdIn(any(), any()))
        .thenReturn(existingBoundMonitors);

    // EXECUTE

    String newResourceIdBinding = "r-1";

    final MonitorCU update = new MonitorCU()
        .setResourceId(newResourceIdBinding)
        .setLabelSelector(null)
        // for a patch we need to set all the other values to the same as the original
        .setZones(monitor.getZones())
        .setMonitorType(monitor.getMonitorType())
        .setMonitorName(monitor.getMonitorName())
        .setContent(monitor.getContent())
        .setAgentType(monitor.getAgentType())
        .setLabelSelectorMethod(monitor.getLabelSelectorMethod())
        .setInterval(monitor.getInterval())
        .setSelectorScope(monitor.getSelectorScope())
        .setPluginMetadataFields(monitor.getPluginMetadataFields());

    final Monitor updatedMonitor = monitorManagement.updateMonitor("t-1", monitor.getId(), update, true);

    // VERIFY
    // confirm monitor is updated
    org.assertj.core.api.Assertions.assertThat(Collections.singleton(updatedMonitor))
        .usingElementComparatorIgnoringFields("createdTimestamp", "updatedTimestamp")
        .containsExactly(
            new Monitor()
                .setId(monitor.getId())
                .setAgentType(AgentType.TELEGRAF)
                .setMonitorType(MonitorType.cpu)
                .setContent("static content")
                .setMonitorMetadataFields(List.of("monitorName"))
                .setTenantId("t-1")
                .setSelectorScope(ConfigSelectorScope.LOCAL)
                .setResourceId(newResourceIdBinding)
                .setLabelSelector(null)
                .setLabelSelectorMethod(LabelSelectorMethod.AND)
                .setInterval(Duration.ofSeconds(60)));

    // confirm old binding deleted
    verify(boundMonitorRepository).deleteAll(existingBoundMonitors);

    // confirm new binding saved
    verify(boundMonitorRepository).saveAll(List.of(
        new BoundMonitor()
            .setTenantId("t-1")
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setZoneName("")
            .setEnvoyId("e-1")
            .setRenderedContent("static content")
    ));

    // confirm event sent for both old and new bindings
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-1")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-2")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-3")
    );
    verifyNoMoreInteractions(monitorEventProducer);
  }

  @Test
  public void testUpdateExistingMonitor_setBothResourceIdAndLabelSelector() {
    reset(envoyResourceManagement, resourceApi);

    final Monitor monitor = monitorRepository.save(new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.cpu)
        .setContent("static content")
        .setTenantId("t-1")
        .setResourceId("r-1")
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setLabelSelector(null)
        .setInterval(Duration.ofSeconds(60)));
    entityManager.flush();

    // EXECUTE

    final MonitorCU update = new MonitorCU()
        .setLabelSelector(Map.of("key1", "value1"));

    exceptionRule.expect(IllegalArgumentException.class);
    exceptionRule.expectMessage(ValidUpdateMonitor.DEFAULT_MESSAGE);
    monitorManagement.updateMonitor("t-1", monitor.getId(), update);

  }

  @Test
  public void testUpdateNonExistentMonitor() {
    String tenant = RandomStringUtils.randomAlphanumeric(10);
    UUID uuid = UUID.randomUUID();

    Map<String, String> newLabels = Collections.singletonMap("newLabel", "newValue");
    MonitorCU update = new MonitorCU();
    update.setLabelSelector(newLabels).setContent("newContent");

    exceptionRule.expect(NotFoundException.class);
    exceptionRule.expectMessage("No monitor found for");
    monitorManagement.updateMonitor(tenant, uuid, update);
  }

  @Test
  public void testRemoveMonitor() {
    final Monitor monitor =
        monitorRepository.save(new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setMonitorType(MonitorType.cpu)
            .setContent("{}")
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setLabelSelector(Collections.singletonMap("os", "linux"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
            .setInterval(Duration.ofSeconds(60)));

    BoundMonitor b = new BoundMonitor()
        .setMonitor(monitor)
        .setResourceId("r-1")
        .setEnvoyId("e-goner");
    when(boundMonitorRepository.findAllByTenantIdAndMonitor_IdIn(anyString(), any(), any()))
        .thenReturn(new PageImpl<>(Collections.singletonList(b)))
        .thenReturn(Page.empty());
    when(zoneStorage.getEnvoyIdToResourceIdMap(any()))
        .thenReturn(
            CompletableFuture.completedFuture(Collections.singletonMap("e-goner", "r-gone")));

    // EXECUTE

    monitorManagement.removeMonitor("t-1", monitor.getId());

    // VERIFY

    final Optional<Monitor> retrieved = monitorManagement.getMonitor("t-1", monitor.getId());
    assertThat(retrieved.isPresent(), equalTo(false));

    verify(boundMonitorRepository, times(2)).findAllByTenantIdAndMonitor_IdIn("t-1", Collections.singletonList(monitor.getId()), PageRequest.of(0, 1000));

    verify(boundMonitorRepository).deleteAll(Collections.singletonList(b));

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId("e-goner")
    );

    verifyNoMoreInteractions(boundMonitorRepository, zoneStorage, monitorEventProducer);
  }

  @Test
  public void testRemoveNonExistentMonitor() {
    String tenant = RandomStringUtils.randomAlphanumeric(10);
    UUID uuid = UUID.randomUUID();

    exceptionRule.expect(NotFoundException.class);
    exceptionRule.expectMessage("No monitor found for");
    monitorManagement.removeMonitor(tenant, uuid);
  }

  @Test
  public void testGetMonitorsFromLabels() {
    int monitorsWithLabels = new Random().nextInt(10) + 10;
    int monitorsWithMismatchedLabels = new Random().nextInt(10) + 20;
    int monitorsWithNoLabels = new Random().nextInt(10) + 20;
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    Map<String, String> labels = Collections.singletonMap("mykey", "myvalue");

    // Create monitors which don't have the labels we care about
    createMonitorsForTenant(monitorsWithMismatchedLabels, tenantId, Map.of("key", "nomatch"));

    // Create monitors which do have the labels we care about
    createMonitorsForTenant(monitorsWithLabels, tenantId, labels);

    // Create a "select all" type of monitor where label selector is empty
    createMonitorsForTenant(monitorsWithNoLabels, tenantId, Collections.emptyMap());

    entityManager.flush();

    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
    assertEquals(monitorsWithLabels+monitorsWithNoLabels, monitors.getTotalElements());
    assertNotNull(monitors);
  }

  @Test
  public void testGetMonitorsFromLabelsOnlyReturnsTenantMonitors() {
    int monitorsWithLabels = new Random().nextInt(10) + 10;
    int monitorsWithoutLabels = new Random().nextInt(10) + 20;
    int monitorsThatMatchEverything = new Random().nextInt(10) + 20;
    int monitorsWithResourceId = new Random().nextInt(10) + 20;
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    Map<String, String> labels = Collections.singletonMap("mykey", "myvalue");

    // Create monitors which don't have the labels we care about
    createMonitorsForTenant(monitorsWithoutLabels, tenantId);

    // Create monitors which do have the labels we care about
    createMonitorsForTenant(monitorsWithLabels, tenantId, labels);

    // Create a "select all" type of monitor where label selector is empty
    createMonitorsForTenant(monitorsThatMatchEverything, tenantId, Collections.emptyMap());

    String resourceId = RandomStringUtils.randomAlphabetic(10);
    createMonitors(monitorsWithResourceId, resourceId);

    entityManager.flush();

    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
    assertEquals(monitorsWithLabels+monitorsThatMatchEverything, monitors.getTotalElements());
    assertNotNull(monitors);
  }


  @Test
  public void testGetMonitorsFromLabelsPaginated() {
    int monitorsWithLabels = new Random().nextInt(10) + 10;
    int monitorsWithoutLabels = new Random().nextInt(10) + 20;
    String tenantId = RandomStringUtils.randomAlphabetic(10);

    Map<String, String> labels = Collections.singletonMap("mykey", "myvalue");

    // Create monitors which don't have the labels we care about
    createMonitorsForTenant(monitorsWithoutLabels, tenantId);

    // Create monitors which do have the labels we care about
    createMonitorsForTenant(monitorsWithLabels, tenantId, labels);

    entityManager.flush();

    int page = 3;
    int size = 2;
    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, PageRequest.of(page, size));
    assertThat(monitors.getTotalElements(), equalTo((long) monitorsWithLabels));
    int totalPages = (monitorsWithLabels  + size  -1 ) / size;
    assertThat(monitors.getTotalPages(), equalTo(totalPages));
    assertThat(monitors.getContent(), hasSize(size));
  }

  @Test
  public void testSpecificCreate() {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "DARWIN");

    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setLabelSelector(labels);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setResourceId(null);
    create.setInterval(Duration.ofSeconds(60));
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    monitorManagement.createMonitor(tenantId, create);
    entityManager.flush();
    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
    assertEquals(1L, monitors.getTotalElements());
    assertNotNull(monitors);
  }

  @Test
  public void testMisMatchSpecificCreate() {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "DARWIN");
    final Map<String, String> queryLabels = new HashMap<>();
    queryLabels.put("os", "linux");

    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setLabelSelector(labels);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setInterval(Duration.ofSeconds(60));
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    monitorManagement.createMonitor(tenantId, create);
    entityManager.flush();
    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(queryLabels, tenantId, Pageable.unpaged());
    assertEquals(0L, monitors.getTotalElements());
  }

  @Test
  public void testEmptyLabelsLookup() {
    when(zoneManagement.getAvailableZonesForTenant(any(), any()))
        .thenReturn(Page.empty());

    String tenantId = RandomStringUtils.randomAlphanumeric(10);

    // create a monitor with no labels or resource id
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setResourceId(null);
    create.setZones(Collections.emptyList());
    create.setLabelSelector(Collections.emptyMap());
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setInterval(Duration.ofSeconds(60));
    UUID id = monitorManagement.createMonitor(tenantId, create).getId();

    // create a monitor with a resource id
    create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setResourceId(RandomStringUtils.random(10));
    create.setZones(Collections.emptyList());
    create.setLabelSelector(Collections.emptyMap());
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setInterval(Duration.ofSeconds(60));
    monitorManagement.createMonitor(tenantId, create).getId();

    // create a monitor with some labels
    create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setResourceId(null);
    create.setZones(Collections.emptyList());
    create.setLabelSelector(Map.of("key", "value"));
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setInterval(Duration.ofSeconds(60));
    monitorManagement.createMonitor(tenantId, create).getId();

    entityManager.flush();

    // lookup monitors that match a resource with no labels set
    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(Collections.emptyMap(), tenantId, Pageable.unpaged());
    assertThat(monitors.getNumberOfElements(), equalTo(1));
    Monitor foundMonitor = monitors.get().findFirst().get();
    assertThat(foundMonitor.getId(), equalTo(id));
  }

  @Test
  public void testMonitorWithSameLabelsAndDifferentTenants() {
    final Map<String, String> labels = new HashMap<>();
    labels.put("key", "value");

    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setLabelSelector(labels);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setResourceId(null);
    create.setInterval(Duration.ofSeconds(60));
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    String tenantId2 = RandomStringUtils.randomAlphanumeric(10);
    monitorManagement.createMonitor(tenantId, create);
    monitorManagement.createMonitor(tenantId2, create);
    entityManager.flush();

    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
    assertEquals(1L, monitors.getTotalElements()); //make sure we only returned the one value
    assertEquals(tenantId, monitors.getContent().get(0).getTenantId());
    assertEquals(create.getAgentType(), monitors.getContent().get(0).getAgentType());
    assertEquals(create.getContent(), monitors.getContent().get(0).getContent());
    assertEquals(create.getMonitorName(), monitors.getContent().get(0).getMonitorName());
    assertEquals(create.getSelectorScope(), monitors.getContent().get(0).getSelectorScope());
    assertEquals(create.getLabelSelector(), monitors.getContent().get(0).getLabelSelector());
  }

  @Test
  public void testMatchMonitorWithMultipleLabels() {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "DARWIN");
    labels.put("env", "test");

    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setLabelSelector(labels);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setResourceId(null);
    create.setInterval(Duration.ofSeconds(60));
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    monitorManagement.createMonitor(tenantId, create);
    entityManager.flush();

    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
    assertEquals(1L, monitors.getTotalElements()); //make sure we only returned the one value
    assertEquals(tenantId, monitors.getContent().get(0).getTenantId());
    assertEquals(create.getAgentType(), monitors.getContent().get(0).getAgentType());
    assertEquals(create.getContent(), monitors.getContent().get(0).getContent());
    assertEquals(create.getMonitorName(), monitors.getContent().get(0).getMonitorName());
    assertEquals(create.getSelectorScope(), monitors.getContent().get(0).getSelectorScope());
    assertEquals(create.getLabelSelector(), monitors.getContent().get(0).getLabelSelector());
  }

  @Test
  public void testMisMatchMonitorWithMultipleLabels() {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "DARWIN");
    labels.put("env", "test");

    final Map<String, String> queryLabels = new HashMap<>();
    queryLabels.put("os", "linux");
    queryLabels.put("env", "test");

    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setLabelSelector(labels);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setLabelSelectorMethod(LabelSelectorMethod.AND);
    create.setZones(Collections.emptyList());
    create.setInterval(Duration.ofSeconds(60));
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    monitorManagement.createMonitor(tenantId, create);
    entityManager.flush();

    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(queryLabels, tenantId, Pageable.unpaged());
    assertEquals(0L, monitors.getTotalElements());
  }

  @Test
  public void testMatchMonitorWithSupersetOfLabels() {
    final Map<String, String> monitorLabels = new HashMap<>();
    monitorLabels.put("os", "DARWIN");
    monitorLabels.put("env", "test");
    monitorLabels.put("architecture", "x86");
    monitorLabels.put("region", "DFW");
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "DARWIN");
    labels.put("env", "test");

    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setLabelSelector(monitorLabels);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setLabelSelectorMethod(LabelSelectorMethod.AND);
    create.setInterval(Duration.ofSeconds(60));

    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    monitorManagement.createMonitor(tenantId, create);
    entityManager.flush();

    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
    assertEquals(0L, monitors.getTotalElements());
  }

  @Test
  public void testMatchMonitorWithSupersetOfLabelsUsingOr() {
    final Map<String, String> monitorLabels = new HashMap<>();
    monitorLabels.put("os", "DARWIN");
    monitorLabels.put("env", "test");
    monitorLabels.put("architecture", "x86");
    monitorLabels.put("region", "DFW");
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "DARWIN");
    labels.put("env", "prod");

    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setLabelSelector(monitorLabels);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setLabelSelectorMethod(LabelSelectorMethod.OR);
    create.setResourceId(null);
    create.setInterval(Duration.ofSeconds(60));
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    monitorManagement.createMonitor(tenantId, create);
    entityManager.flush();

    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
    assertEquals(1L, monitors.getTotalElements()); //make sure we only returned the one value
    assertEquals(tenantId, monitors.getContent().get(0).getTenantId());
    assertEquals(create.getAgentType(), monitors.getContent().get(0).getAgentType());
    assertEquals(create.getContent(), monitors.getContent().get(0).getContent());
    assertEquals(create.getMonitorName(), monitors.getContent().get(0).getMonitorName());
    assertEquals(create.getSelectorScope(), monitors.getContent().get(0).getSelectorScope());
    assertEquals(create.getLabelSelector(), monitors.getContent().get(0).getLabelSelector());
  }

  @Test
  public void testMisMatchMonitorWithSupersetOfLabels() {
    final Map<String, String> monitorLabels = new HashMap<>();
    monitorLabels.put("os", "DARWIN");
    monitorLabels.put("env", "test");
    monitorLabels.put("architecture", "x86");
    monitorLabels.put("region", "DFW");
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "Windows");
    labels.put("env", "test");

    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setLabelSelector(monitorLabels);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setLabelSelectorMethod(LabelSelectorMethod.AND);
    create.setInterval(Duration.ofSeconds(60));
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    monitorManagement.createMonitor(tenantId, create);
    entityManager.flush();

    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
    assertEquals(0L, monitors.getTotalElements());
  }

  @Test
  public void testMisMatchMonitorWithSupersetOfLabelsUsingOr() {
    final Map<String, String> monitorLabels = new HashMap<>();
    monitorLabels.put("os", "DARWIN");
    monitorLabels.put("env", "test");
    monitorLabels.put("architecture", "x86");
    monitorLabels.put("region", "DFW");
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "Windows");
    labels.put("env", "prod");

    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setLabelSelector(monitorLabels);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setLabelSelectorMethod(LabelSelectorMethod.OR);
    create.setInterval(Duration.ofSeconds(60));
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    monitorManagement.createMonitor(tenantId, create);
    entityManager.flush();

    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
    assertEquals(0L, monitors.getTotalElements());
  }

  @Test
  public void testMatchMonitorWithSubsetOfLabels() {
    final Map<String, String> monitorLabels = new HashMap<>();
    monitorLabels.put("os", "DARWIN");
    monitorLabels.put("env", "test");
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "DARWIN");
    labels.put("env", "test");
    labels.put("architecture", "x86");
    labels.put("region", "DFW");


    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setLabelSelector(monitorLabels);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setLabelSelectorMethod(LabelSelectorMethod.AND);
    create.setResourceId(null);
    create.setInterval(Duration.ofSeconds(60));
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    monitorManagement.createMonitor(tenantId, create);
    entityManager.flush();

    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
    assertEquals(1L, monitors.getTotalElements()); //make sure we only returned the one value
    assertEquals(tenantId, monitors.getContent().get(0).getTenantId());
    assertEquals(create.getAgentType(), monitors.getContent().get(0).getAgentType());
    assertEquals(create.getContent(), monitors.getContent().get(0).getContent());
    assertEquals(create.getMonitorName(), monitors.getContent().get(0).getMonitorName());
    assertEquals(create.getSelectorScope(), monitors.getContent().get(0).getSelectorScope());
    assertEquals(create.getLabelSelector(), monitors.getContent().get(0).getLabelSelector());
  }

  @Test
  public void testMatchMonitorWithSubsetOfLabelsUsingOr() {
    final Map<String, String> monitorLabels = new HashMap<>();
    monitorLabels.put("os", "DARWIN");
    monitorLabels.put("env", "test");
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "DARWIN");
    labels.put("env", "prod");
    labels.put("architecture", "x86");
    labels.put("region", "DFW");


    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setLabelSelector(monitorLabels);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    create.setResourceId(null);
    create.setLabelSelectorMethod(LabelSelectorMethod.OR);
    create.setInterval(Duration.ofSeconds(60));
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    monitorManagement.createMonitor(tenantId, create);
    entityManager.flush();

    Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
    assertEquals(1L, monitors.getTotalElements()); //make sure we only returned the one value
    assertEquals(tenantId, monitors.getContent().get(0).getTenantId());
    assertEquals(create.getAgentType(), monitors.getContent().get(0).getAgentType());
    assertEquals(create.getContent(), monitors.getContent().get(0).getContent());
    assertEquals(create.getMonitorName(), monitors.getContent().get(0).getMonitorName());
    assertEquals(create.getSelectorScope(), monitors.getContent().get(0).getSelectorScope());
    assertEquals(create.getLabelSelector(), monitors.getContent().get(0).getLabelSelector());
  }

  public void testMisMatchResourceWithSubsetOfLabels() {
    final Map<String, String> monitorLabels = new HashMap<>();
    monitorLabels.put("os", "DARWIN");
    monitorLabels.put("env", "test");
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "Windows");
    labels.put("env", "test");
    labels.put("architecture", "x86");
    labels.put("region", "DFW");


    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setLabelSelector(monitorLabels);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    monitorManagement.createMonitor(tenantId, create);
    entityManager.flush();

    Page<Monitor> resources = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
    assertEquals(0L, resources.getTotalElements());
  }

  @Test
  public void testExtractEnvoyIds() {

    final List<BoundMonitor> input = Arrays.asList(
        new BoundMonitor().setEnvoyId("e-1"),
        new BoundMonitor().setEnvoyId("e-2"),
        new BoundMonitor().setEnvoyId(null),
        new BoundMonitor().setEnvoyId("e-1"),
        new BoundMonitor().setEnvoyId("e-3")
    );

    final Set<String> result = MonitorManagement.extractEnvoyIds(input);

    assertThat(result, containsInAnyOrder("e-1", "e-2", "e-3"));

  }

  @Test
  public void testSendMonitorBoundEvents() {
    monitorManagement.sendMonitorBoundEvents(Sets.newHashSet("e-3", "e-2", "e-1"));

    ArgumentCaptor<MonitorBoundEvent> evtCaptor = ArgumentCaptor.forClass(MonitorBoundEvent.class);

    verify(monitorEventProducer, times(3)).sendMonitorEvent(evtCaptor.capture());

    assertThat(evtCaptor.getAllValues(), containsInAnyOrder(
        new MonitorBoundEvent().setEnvoyId("e-1"),
        new MonitorBoundEvent().setEnvoyId("e-2"),
        new MonitorBoundEvent().setEnvoyId("e-3")
    ));
  }

  @Test
  public void testDistributeNewMonitor_agent() {
    Monitor monitor = new Monitor()
        .setId(UUID.randomUUID())
        .setTenantId("t-1")
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelector(Collections.singletonMap("os", "LINUX"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setAgentType(AgentType.TELEGRAF)
        .setContent("{}");

    final Set<String> affectedEnvoys = monitorManagement.bindMonitor("t-1", monitor, monitor.getZones());

    final List<BoundMonitor> expected = Collections.singletonList(
        new BoundMonitor()
            .setResourceId(DEFAULT_RESOURCE_ID)
            .setMonitor(monitor)
            .setTenantId("t-1")
            .setEnvoyId(DEFAULT_ENVOY_ID)
            .setRenderedContent("{}")
            .setZoneName("")
    );
    verify(boundMonitorRepository).saveAll(
        expected
    );

    assertThat(affectedEnvoys, contains(DEFAULT_ENVOY_ID));

    verifyNoMoreInteractions(monitorEventProducer, boundMonitorRepository, resourceRepository);
  }

  @Test
  public void testHandleReattachedEnvoy() {

    when(boundMonitorRepository.findAllLocalByTenantResource("t-1", "r-1"))
        .thenReturn(Arrays.asList(
            new BoundMonitor()
                .setEnvoyId("e-old-1")
                .setRenderedContent("content-1"),
            new BoundMonitor()
                .setEnvoyId("e-old-2")
                .setRenderedContent("content-2")
        ));

    // EXECUTE

    final ResourceEvent event = new ResourceEvent()
        .setReattachedEnvoyId("e-new")
        .setLabelsChanged(false)
        .setTenantId("t-1")
        .setResourceId("r-1");

    monitorManagement.handleResourceChangeEvent(event);

    // VERIFY

    verify(boundMonitorRepository)
        .findAllLocalByTenantResource("t-1", "r-1");
    verify(boundMonitorRepository).saveAll(
        Arrays.asList(
            new BoundMonitor()
                .setEnvoyId("e-new")
                .setRenderedContent("content-1"),
            new BoundMonitor()
                .setEnvoyId("e-new")
                .setRenderedContent("content-2")
        )
    );

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-old-1")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-old-2")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent().setEnvoyId("e-new")
    );

    verifyNoInteractions(policyApi);
    verifyNoMoreInteractions(monitorEventProducer, boundMonitorRepository);
  }

  @Test
  public void testBindMonitor_AgentWithEnvoy() {
    final UUID m0 = UUID.fromString("00000000-0000-0000-0000-000000000000");
    final Monitor monitor = new Monitor()
        .setId(m0)
        .setTenantId("t-1")
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setContent("static content")
        .setZones(Collections.emptyList())
        .setLabelSelectorMethod(LabelSelectorMethod.AND);

    List<ResourceDTO> resourceList = Collections.singletonList(new ResourceDTO()
        .setTenantId("t-1")
        .setResourceId("r-1")
        .setLabels(Collections.emptyMap())
        .setAssociatedWithEnvoy(true)
        .setEnvoyId("e-1")
    );

    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);

    final ResourceInfo resourceInfo = new ResourceInfo()
        .setTenantId("t-1")
        .setResourceId("r-1")
        .setEnvoyId("e-1");

    when(envoyResourceManagement.getOne(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(resourceInfo));

    Set<String> result = monitorManagement.bindMonitor("t-1", monitor, monitor.getZones());

    assertThat(result, hasSize(1));
    assertThat(result.toArray()[0], equalTo("e-1"));

    verify(resourceApi).getResourcesWithLabels("t-1", monitor.getLabelSelector(), monitor.getLabelSelectorMethod());
    verify(boundMonitorRepository).saveAll(Collections.singletonList(
        new BoundMonitor()
            .setMonitor(monitor)
            .setTenantId("t-1")
            .setResourceId("r-1")
            .setEnvoyId("e-1")
            .setRenderedContent("static content")
            .setZoneName("")
    ));
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi);
  }

  @Test
  public void testBindMonitor_AgentWithNoEnvoy() {
    reset(resourceApi, envoyResourceManagement);

    final UUID m0 = UUID.fromString("00000000-0000-0000-0000-000000000000");
    final Monitor monitor = new Monitor()
        .setId(m0)
        .setTenantId("t-1")
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setContent("static content")
        .setZones(Collections.emptyList())
        .setLabelSelectorMethod(LabelSelectorMethod.AND);

    List<ResourceDTO> resourceList = Collections.singletonList(new ResourceDTO()
        .setTenantId("t-1")
        .setResourceId("r-1")
        .setLabels(Collections.emptyMap())
        // doesn't have envoy at the moment, but did before
        .setAssociatedWithEnvoy(true)
    );

    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);

    when(envoyResourceManagement.getOne(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    Set<String> result = monitorManagement.bindMonitor("t-1", monitor, monitor.getZones());

    assertThat(result, hasSize(0));

    verify(resourceApi).getResourcesWithLabels("t-1", monitor.getLabelSelector(), monitor.getLabelSelectorMethod());
    verify(boundMonitorRepository).saveAll(Collections.singletonList(
        new BoundMonitor()
            .setMonitor(monitor)
            .setTenantId("t-1")
            .setResourceId("r-1")
            .setEnvoyId(null)
            .setRenderedContent("static content")
            .setZoneName("")
    ));
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi);
  }

  @Test
  public void testBindMonitor_AgentWithNoPriorEnvoy() throws InvalidTemplateException {
    reset(resourceApi, envoyResourceManagement);

    final UUID m0 = UUID.fromString("00000000-0000-0000-0000-000000000000");
    final Monitor monitor = new Monitor()
        .setId(m0)
        .setTenantId("t-1")
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setContent("static content")
        .setZones(Collections.emptyList())
        .setLabelSelectorMethod(LabelSelectorMethod.AND);
    ResourceDTO resource = new ResourceDTO()
        .setResourceId("r-1")
        .setLabels(Collections.emptyMap())
        .setAssociatedWithEnvoy(false);
    List<ResourceDTO> resourceList = Collections.singletonList(resource);

    when(resourceApi.getResourcesWithLabels(any(), any(), eq(LabelSelectorMethod.AND)))
        .thenReturn(resourceList);

    Set<String> result = monitorManagement.bindMonitor("t-1", monitor, monitor.getZones());

    assertThat(result, hasSize(0));

    verify(resourceApi).getResourcesWithLabels("t-1", monitor.getLabelSelector(), monitor.getLabelSelectorMethod());

    verify(boundMonitorRepository).saveAll(captorOfBoundMonitorList.capture());
    assertThat(captorOfBoundMonitorList.getValue(), hasSize(1));
    assertThat(captorOfBoundMonitorList.getValue().get(0), equalTo(new BoundMonitor()
      .setResourceId("r-1")
      .setMonitor(monitor)
      .setRenderedContent("static content")
      .setEnvoyId(null)
      .setZoneName("")
    ));
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi);
  }

  @Test
  public void testBindMonitor_ResourceId() {
    reset(resourceApi, envoyResourceManagement);

    final UUID m0 = UUID.fromString("00000000-0000-0000-0000-000000000000");
    final Monitor monitor = new Monitor()
        .setId(m0)
        .setTenantId("t-1")
        .setResourceId("r-1")
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setContent("static content")
        .setZones(Collections.emptyList())
        .setLabelSelectorMethod(LabelSelectorMethod.AND);

    Resource resource = new Resource()
        .setResourceId("r-1")
        .setTenantId("t-1")
        .setLabels(Collections.emptyMap())
        .setAssociatedWithEnvoy(true)
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

    when(resourceRepository.findByTenantIdAndResourceId(any(), any()))
        .thenReturn(Optional.of(resource));

    when(envoyResourceManagement.getOne(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    Set<String> envoyIds = monitorManagement.bindMonitor("t-1", monitor, monitor.getZones());

    // Monitor was bound but no envoy connection so didn't return the envoyId
    assertThat(envoyIds, hasSize(0));

    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");
    verify(envoyResourceManagement).getOne("t-1", "r-1");
    verify(boundMonitorRepository).saveAll(Collections.singletonList(
        new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setTenantId("t-1")
            .setEnvoyId(null)
            .setRenderedContent("static content")
            .setZoneName("")
    ));
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi);
  }

  @Test
  public void testUpsertBindingToResource() {

    final UUID m0 = UUID.fromString("00000000-0000-0000-0000-000000000000");
    final UUID m1 = UUID.fromString("00000000-0000-0000-0001-000000000000");
    final UUID m2 = UUID.fromString("00000000-0000-0000-0002-000000000000");
    final UUID m3 = UUID.fromString("00000000-0000-0000-0003-000000000000");

    // The following monitors hits the various scenarios we need to support when a resource changes
    List<Monitor> monitors = Arrays.asList(
        // new local monitor
        // --> 1 x BoundMonitor
        new Monitor()
            .setId(m0)
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setContent("new local domain=${resource.labels.env}")
            .setLabelSelectorMethod(LabelSelectorMethod.AND),
        // new remote monitor
        // --> 2 x BoundMonitor
        new Monitor()
            .setId(m1)
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setContent("new remote domain=${resource.labels.env}")
            .setZones(Arrays.asList("z-1", "z-2"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND),
        // existing monitor needing re-render
        // --> 1 x BoundMonitor
        new Monitor()
            .setId(m2)
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setContent("existing local domain=${resource.labels.env}")
            .setLabelSelectorMethod(LabelSelectorMethod.AND),
        // existing monitor no re-render
        new Monitor()
            .setId(m3)
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setContent("static content")
            .setZones(Collections.singletonList("z-1"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
    );

    final ResourceDTO resource = new ResourceDTO()
        .setTenantId("t-1")
        .setResourceId("r-1")
        .setAssociatedWithEnvoy(true)
        .setLabels(Collections.singletonMap("env", "prod"));

    final ResourceInfo resourceInfo = new ResourceInfo()
        .setTenantId("t-1")
        .setResourceId("r-1")
        .setEnvoyId("e-1");

    when(envoyResourceManagement.getOne(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(resourceInfo));

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(Optional.of(
            new EnvoyResourcePair().setEnvoyId("e-2").setResourceId("poller-2")));

    when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(m0, "r-1"))
        .thenReturn(Collections.emptyList());
    when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(m1, "r-1"))
        .thenReturn(Collections.emptyList());
    when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(m2, "r-1"))
        .thenReturn(Collections.singletonList(
            new BoundMonitor()
                .setResourceId("r-1")
                .setMonitor(monitors.get(2))
                .setTenantId("t-1")
                .setRenderedContent("domain=dev")
                .setEnvoyId("e-3")
                .setZoneName("")
        ));
    when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(m3, "r-1"))
        .thenReturn(Collections.singletonList(
            new BoundMonitor()
                .setResourceId("r-1")
                .setMonitor(monitors.get(3))
                .setRenderedContent("static content")
                .setZoneName("z-1")
                .setEnvoyId("e-4")
        ));

    // EXERCISE

    final Set<String> affectedEnvoys =
        monitorManagement.upsertBindingToResource(monitors, resource, null);

    // VERIFY

    assertThat(affectedEnvoys, containsInAnyOrder("e-1", "e-2", "e-3"));

    verify(envoyResourceManagement).getOne("t-1", "r-1");

    final ResolvedZone z1 = createPrivateZone("t-1", "z-1");
    final ResolvedZone z2 = createPrivateZone("t-1", "z-2");
    verify(zoneAllocationResolver).findLeastLoadedEnvoy(z1);
    verify(zoneAllocationResolver).findLeastLoadedEnvoy(z2);

    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m0, "r-1");
    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m1, "r-1");
    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m2, "r-1");
    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m3, "r-1");
    verify(boundMonitorRepository).saveAll(
        Arrays.asList(
            new BoundMonitor()
                .setMonitor(monitors.get(0))
                .setTenantId("t-1")
                .setResourceId("r-1")
                .setEnvoyId("e-1")
                .setRenderedContent("new local domain=prod")
                .setZoneName(""),
            new BoundMonitor()
                .setMonitor(monitors.get(1))
                .setTenantId("t-1")
                .setResourceId("r-1")
                .setEnvoyId("e-2")
                .setPollerResourceId("poller-2")
                .setRenderedContent("new remote domain=prod")
                .setZoneName("z-1"),
            new BoundMonitor()
                .setMonitor(monitors.get(1))
                .setTenantId("t-1")
                .setResourceId("r-1")
                .setEnvoyId("e-2")
                .setPollerResourceId("poller-2")
                .setRenderedContent("new remote domain=prod")
                .setZoneName("z-2"),
            new BoundMonitor()
                .setMonitor(monitors.get(2))
                .setTenantId("t-1")
                .setResourceId("r-1")
                .setEnvoyId("e-3")
                .setRenderedContent("existing local domain=prod")
                .setZoneName("")
            // NOTE binding of m3 did not need to be re-bound since its "static content" was
            // unaffected by the change in resource labels.
        )
    );

    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
        zoneStorage, zoneAllocationResolver);
  }

  @Test
  public void testUpsertBindingToResource_noEnvoyResource() {
    final UUID m0 = UUID.fromString("00000000-0000-0000-0000-000000000000");
    final UUID m1 = UUID.fromString("00000000-0000-0000-0001-000000000000");

    List<Monitor> monitors = Arrays.asList(
        new Monitor()
            .setId(m0)
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setContent("new local domain=${resource.labels.env}")
            .setLabelSelectorMethod(LabelSelectorMethod.AND),
        new Monitor()
            .setId(m1)
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setContent("new remote domain=${resource.labels.env}")
            .setZones(Collections.singletonList("z-1"))
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
    );

    final ResourceDTO resource = new ResourceDTO()
        .setTenantId("t-1")
        .setResourceId("r-1")
        // doesn't have envoy at the moment, but did before
        .setAssociatedWithEnvoy(true)
        .setLabels(Collections.singletonMap("env", "prod"));

    // simulate no envoys attached
    when(envoyResourceManagement.getOne(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    when(zoneAllocationResolver.findLeastLoadedEnvoy(any()))
        .thenReturn(Optional.empty());

    // EXERCISE

    final Set<String> affectedEnvoys =
        monitorManagement.upsertBindingToResource(monitors, resource, null);

    // VERIFY

    assertThat(affectedEnvoys, hasSize(0));

    verify(envoyResourceManagement).getOne("t-1", "r-1");

    final ResolvedZone z1 = createPrivateZone("t-1", "z-1");
    verify(zoneAllocationResolver).findLeastLoadedEnvoy(z1);

    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m0, "r-1");
    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m1, "r-1");
    verify(boundMonitorRepository).saveAll(
        Arrays.asList(
            new BoundMonitor()
                .setMonitor(monitors.get(0))
                .setTenantId("t-1")
                .setResourceId("r-1")
                .setEnvoyId(null)
                .setRenderedContent("new local domain=prod")
                .setZoneName(""),
            new BoundMonitor()
                .setMonitor(monitors.get(1))
                .setTenantId("t-1")
                .setResourceId("r-1")
                .setEnvoyId(null)
                .setRenderedContent("new remote domain=prod")
                .setZoneName("z-1")
        )
    );


    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
        zoneStorage, zoneAllocationResolver
    );
  }

  @Test
  public void testUpsertBindingToResource_noPriorEnvoyResource() {
    final UUID m0 = UUID.fromString("00000000-0000-0000-0000-000000000000");

    List<Monitor> monitors = Collections.singletonList(
        new Monitor()
            .setId(m0)
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setContent("new local domain=${resource.labels.env}")
            .setLabelSelectorMethod(LabelSelectorMethod.AND)
    );

    final ResourceDTO resource = new ResourceDTO()
        .setTenantId("t-1")
        .setResourceId("r-1")
        // never had an envoy
        .setAssociatedWithEnvoy(false)
        .setLabels(Collections.singletonMap("env", "prod"));

    // simulate no envoys attached
    when(envoyResourceManagement.getOne(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // EXERCISE

    final Set<String> affectedEnvoys =
        monitorManagement.upsertBindingToResource(monitors, resource, null);

    // VERIFY

    assertThat(affectedEnvoys, hasSize(0));

    verify(envoyResourceManagement).getOne("t-1", "r-1");

    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m0, "r-1");

    verify(boundMonitorRepository).saveAll(captorOfBoundMonitorList.capture());
    assertThat(captorOfBoundMonitorList.getValue(), hasSize(1));
    assertThat(captorOfBoundMonitorList.getValue().get(0), equalTo(new BoundMonitor()
          .setResourceId("r-1")
          .setTenantId("t-1")
          .setMonitor(monitors.get(0))
          .setRenderedContent("new local domain=prod")
          .setEnvoyId(null)
          .setZoneName("")
    ));


    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
        zoneStorage
    );
  }

  @Test
  public void testhandleResourceEvent_newResource() {
    final Monitor monitor = setupTestingOfHandleResourceEvent(
        "t-1",
        "r-1",
        Collections.singletonMap("env", "prod"),
        ConfigSelectorScope.LOCAL,
        Collections.singletonMap("env", "prod"),
        "domain=${resource.labels.env}",
        null
    );

    // EXERCISE

    monitorManagement.handleResourceChangeEvent(new ResourceEvent()
        .setTenantId("t-1")
        .setResourceId("r-1"));

    // VERIFY

    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");

    verify(envoyResourceManagement).getOne("t-1", "r-1");

    verify(boundMonitorRepository).findMonitorIdsBoundToTenantAndResource("t-1", "r-1");

    verify(boundMonitorRepository).saveAll(captorOfBoundMonitorList.capture());
    final List<BoundMonitor> savedBoundMonitors = captorOfBoundMonitorList.getValue();
    assertThat(savedBoundMonitors, hasSize(1));
    assertThat(savedBoundMonitors, contains(
        new BoundMonitor()
            .setMonitor(monitor)
            .setTenantId("t-1")
            .setResourceId("r-1")
            .setEnvoyId("e-1")
            .setRenderedContent("domain=prod")
            .setZoneName("")
    ));

    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(monitor.getId(), "r-1");

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId("e-1")
    );

    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
        zoneStorage, monitorEventProducer, resourceApi, resourceRepository, policyApi);
  }

  @Test
  public void testhandleResourceEvent_monitorWithResourceId() {
    // Confirm that newly created resource binds with existing monitor-with-resourceId
    final Monitor monitor = setupTestingOfHandleResourceEvent(
        "t-1",
        "r-1",
        Collections.singletonMap("env", "prod"),
        ConfigSelectorScope.LOCAL,
        null,
        "static content",
        null
    );

    monitor.setResourceId("r-1");
    entityManager.merge(monitor);
    entityManager.flush();

    // EXERCISE

    monitorManagement.handleResourceChangeEvent(new ResourceEvent()
        .setTenantId("t-1")
        .setResourceId("r-1"));

    // VERIFY

    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");

    verify(envoyResourceManagement).getOne("t-1", "r-1");

    verify(boundMonitorRepository).findMonitorIdsBoundToTenantAndResource("t-1", "r-1");

    verify(boundMonitorRepository).saveAll(captorOfBoundMonitorList.capture());
    final List<BoundMonitor> savedBoundMonitors = captorOfBoundMonitorList.getValue();
    assertThat(savedBoundMonitors, hasSize(1));
    assertThat(savedBoundMonitors, contains(
        new BoundMonitor()
            .setTenantId("t-1")
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setEnvoyId("e-1")
            .setRenderedContent("static content")
            .setZoneName("")));

    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(monitor.getId(), "r-1");

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId("e-1")
    );

    verifyNoInteractions(policyApi);
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
        zoneStorage, monitorEventProducer, resourceApi, resourceRepository);
  }

  /**
   * Sets up mocking of a resource, stores a monitor, and optionally stores a bound monitor.
   * @param resourceId if non-null, mock resourceApi retrieval and return one with given ID
   * @param boundContent if non-null, creates a bound monitor and mocks the queries for that
   * @return the newly stored monitor
   */
  private Monitor setupTestingOfHandleResourceEvent(String tenantId, String resourceId,
      Map<String, String> resourceLabels,
      ConfigSelectorScope monitorScope,
      Map<String, String> labelSelector,
      String monitorContent, String boundContent) {
    if (resourceId != null && resourceLabels != null) {
      final Resource resource = new Resource()
          .setLabels(resourceLabels)
          .setResourceId(resourceId)
          .setTenantId(tenantId)
          .setAssociatedWithEnvoy(true)
          .setCreatedTimestamp(DEFAULT_TIMESTAMP)
          .setUpdatedTimestamp(DEFAULT_TIMESTAMP);
      when(resourceRepository.findByTenantIdAndResourceId(any(), any()))
          .thenReturn(Optional.of(resource));

      ResourceInfo resourceInfo = new ResourceInfo()
          .setResourceId(resourceId)
          .setEnvoyId("e-1");
      when(envoyResourceManagement.getOne(any(), any()))
          .thenReturn(CompletableFuture.completedFuture(resourceInfo));
    } else {
      when(resourceRepository.findByTenantIdAndResourceId(any(), any()))
          .thenReturn(Optional.empty());
    }

    final Monitor monitor = new Monitor()
        .setSelectorScope(monitorScope)
        .setTenantId(tenantId)
        .setMonitorType(MonitorType.cpu)
        .setLabelSelector(labelSelector)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setAgentType(AgentType.TELEGRAF)
        .setContent(monitorContent)
        .setInterval(Duration.ofSeconds(60));
    entityManager.persist(monitor);
    entityManager.flush();

    if (boundContent != null) {
      final BoundMonitor boundMonitor = new BoundMonitor()
          .setMonitor(monitor)
          .setResourceId(resourceId)
          .setZoneName("")
          .setRenderedContent(boundContent)
          .setEnvoyId("e-1");

      when(boundMonitorRepository.findMonitorIdsBoundToTenantAndResource(any(), any()))
          .thenReturn(Collections.singletonList(monitor.getId()));

      when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(any(), any()))
          .thenReturn(Collections.singletonList(boundMonitor));
    } else {
      when(boundMonitorRepository.findMonitorIdsBoundToTenantAndResource(any(), any()))
          .thenReturn(Collections.emptyList());

      when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(any(), any()))
          .thenReturn(Collections.emptyList());
    }

    return monitor;
  }

  @Test
  public void testhandleResourceEvent_modifiedResource() {

    final Monitor monitor = setupTestingOfHandleResourceEvent(
        "t-1", "r-1", Collections.singletonMap("env", "prod"),
        ConfigSelectorScope.LOCAL, Collections.singletonMap("env", "prod"),
        "domain=${resource.labels.env}",
        "domain=some old value"
    );

    // EXERCISE

    monitorManagement.handleResourceChangeEvent(new ResourceEvent()
        .setTenantId("t-1")
        .setResourceId("r-1"));

    // VERIFY

    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");

    verify(envoyResourceManagement).getOne("t-1", "r-1");

    verify(boundMonitorRepository).findMonitorIdsBoundToTenantAndResource("t-1", "r-1");

    verify(boundMonitorRepository).saveAll(captorOfBoundMonitorList.capture());
    final List<BoundMonitor> savedBoundMonitors = captorOfBoundMonitorList.getValue();
    assertThat(savedBoundMonitors, hasSize(1));
    assertThat(savedBoundMonitors, contains(
        new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setEnvoyId("e-1")
            .setRenderedContent("domain=prod")
            .setZoneName("")
    ));

    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(monitor.getId(), "r-1");

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId("e-1")
    );

    verifyNoInteractions(policyApi);
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
        zoneStorage, monitorEventProducer, resourceApi, resourceRepository);
  }

  @Test
  public void testhandleResourceEvent_modifiedResource_reattachedEnvoy_sameContent() {
    final Monitor monitor = new Monitor()
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setMonitorType(MonitorType.cpu)
        .setTenantId("t-1")
        .setLabelSelector(Collections.singletonMap("env", "prod"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setAgentType(AgentType.TELEGRAF)
        .setContent("static content")
        .setInterval(Duration.ofSeconds(60));
    entityManager.persist(monitor);

    entityManager.flush();

    final BoundMonitor boundMonitor = new BoundMonitor()
        .setMonitor(monitor)
        .setResourceId("r-1")
        .setZoneName("")
        .setRenderedContent("static content")
        .setEnvoyId("e-old");

    when(boundMonitorRepository.findAllLocalByTenantResource(any(), any()))
        .thenReturn(Collections.singletonList(boundMonitor));

    // EXERCISE

    monitorManagement.handleResourceChangeEvent(
        new ResourceEvent()
            .setTenantId("t-1")
            .setResourceId("r-1")
            .setReattachedEnvoyId("e-new")
    );

    // VERIFY

    verify(boundMonitorRepository).findAllLocalByTenantResource("t-1", "r-1");

    verify(boundMonitorRepository).saveAll(captorOfBoundMonitorList.capture());
    final List<BoundMonitor> savedBoundMonitors = captorOfBoundMonitorList.getValue();
    assertThat(savedBoundMonitors, hasSize(1));
    assertThat(savedBoundMonitors, contains(
        new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setEnvoyId("e-new")
            .setRenderedContent("static content")
            .setZoneName("")
    ));

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId("e-old")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId("e-new")
    );

    verifyNoInteractions(policyApi);
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
        zoneStorage, monitorEventProducer, resourceApi);
  }

  @Test
  public void testhandleResourceEvent_modifiedResource_reattachedEnvoy_changedContent() {

    // for this unit test the "new" value of the resource don't really matter as long as
    // the monitor label selector continues to align
    final Resource resource = new Resource()
        .setLabels(Collections.singletonMap("env", "prod"))
        .setMetadata(Collections.singletonMap("custom", "new"))
        .setResourceId("r-1")
        .setTenantId("t-1")
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);
    when(resourceRepository.findByTenantIdAndResourceId(any(), any()))
        .thenReturn(Optional.of(resource));

    ResourceInfo resourceInfo = new ResourceInfo()
        .setResourceId("r-1")
        .setEnvoyId("e-not-used"); // for this particular use case
    when(envoyResourceManagement.getOne(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(resourceInfo));

    final Monitor monitor = new Monitor()
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setMonitorType(MonitorType.cpu)
        .setTenantId("t-1")
        .setLabelSelector(Collections.singletonMap("env", "prod"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setAgentType(AgentType.TELEGRAF)
        .setContent("custom=${resource.metadata.custom}")
        .setInterval(Duration.ofSeconds(60));
    entityManager.persist(monitor);

    entityManager.flush();

    final BoundMonitor boundMonitor = new BoundMonitor()
        .setMonitor(monitor)
        .setResourceId("r-1")
        .setZoneName("")
        .setRenderedContent("custom=old")
        .setEnvoyId("e-old");

    when(boundMonitorRepository.findMonitorIdsBoundToTenantAndResource("t-1", "r-1"))
        .thenReturn(Collections.singletonList(monitor.getId()));

    when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(any(), any()))
        .thenReturn(Collections.singletonList(boundMonitor));

    // EXERCISE

    monitorManagement.handleResourceChangeEvent(
        new ResourceEvent()
            .setTenantId("t-1")
            .setResourceId("r-1")
            .setLabelsChanged(true)
            .setReattachedEnvoyId("e-new")
    );

    // VERIFY

    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");

    verify(envoyResourceManagement).getOne("t-1", "r-1");

    verify(boundMonitorRepository).findMonitorIdsBoundToTenantAndResource("t-1", "r-1");

    verify(boundMonitorRepository).saveAll(captorOfBoundMonitorList.capture());
    final List<BoundMonitor> savedBoundMonitors = captorOfBoundMonitorList.getValue();
    assertThat(savedBoundMonitors, hasSize(1));
    assertThat(savedBoundMonitors, contains(
        new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setEnvoyId("e-new")
            .setRenderedContent("custom=new")
            .setZoneName("")
    ));

    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(monitor.getId(), "r-1");

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId("e-old")
    );
    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId("e-new")
    );

    verifyNoInteractions(policyApi);
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
        zoneStorage, monitorEventProducer, resourceApi, resourceRepository);
  }

  @Test
  public void testhandleResourceEvent_modifiedResource_invalidRendering_existingBindings() {

    final Monitor monitor = setupTestingOfHandleResourceEvent(
        "t-1", "r-1",
        // simulate the removal of an 'other' label that is referenced from monitor content
        Collections.singletonMap("env", "prod"),
        ConfigSelectorScope.LOCAL, Collections.singletonMap("env", "prod"),
        "domain=${resource.labels.other}",
        "domain=some old value"
    );

    // EXERCISE

    monitorManagement.handleResourceChangeEvent(
        new ResourceEvent()
            .setTenantId("t-1")
            .setResourceId("r-1")
            .setLabelsChanged(true)
            .setReattachedEnvoyId("e-new")
    );

    // VERIFY
    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");
    verify(envoyResourceManagement).getOne("t-1", "r-1");
    verify(boundMonitorRepository).findMonitorIdsBoundToTenantAndResource("t-1", "r-1");
    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(monitor.getId(), "r-1");

    verify(boundMonitorRepository).deleteAll(captorOfBoundMonitorList.capture());
    final List<BoundMonitor> deletedBoundMonitors = captorOfBoundMonitorList.getValue();
    assertThat(deletedBoundMonitors, hasSize(1));
    assertThat(deletedBoundMonitors.get(0).getMonitor().getId(), equalTo(monitor.getId()));

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId("e-1")
    );

    verifyNoInteractions(policyApi);
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
        zoneStorage, monitorEventProducer, resourceApi, resourceRepository);
  }

  @Test
  public void testhandleResourceEvent_modifiedResource_invalidRendering_newBindings_local() {

    final Monitor monitor = setupTestingOfHandleResourceEvent(
        "t-1", "r-1",
        // simulate the removal of an 'other' label that is referenced from monitor content
        Collections.singletonMap("env", "prod"),
        ConfigSelectorScope.LOCAL,
        Collections.singletonMap("env", "prod"),
        "domain=${resource.labels.other}",
        null
    );

    // EXERCISE

    monitorManagement.handleResourceChangeEvent(
        new ResourceEvent()
            .setTenantId("t-1")
            .setResourceId("r-1")
            .setLabelsChanged(true)
            .setReattachedEnvoyId("e-new")
    );

    // VERIFY
    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");
    verify(envoyResourceManagement).getOne("t-1", "r-1");
    verify(boundMonitorRepository).findMonitorIdsBoundToTenantAndResource("t-1", "r-1");
    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(monitor.getId(), "r-1");

    // nothing new bound and no affected envoy events

    verifyNoInteractions(policyApi);
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
        zoneStorage, monitorEventProducer, resourceApi, resourceRepository);
  }

  @Test
  public void testhandleResourceEvent_modifiedResource_invalidRendering_newBindings_remote() {

    final Monitor monitor = setupTestingOfHandleResourceEvent(
        "t-1", "r-1",
        // simulate the removal of an 'other' label that is referenced from monitor content
        Collections.singletonMap("env", "prod"),
        ConfigSelectorScope.REMOTE,
        Collections.singletonMap("env", "prod"),
        "domain=${resource.labels.other}",
        null
    );

    // EXERCISE

    monitorManagement.handleResourceChangeEvent(
        new ResourceEvent()
            .setTenantId("t-1")
            .setResourceId("r-1")
            .setLabelsChanged(true)
            .setReattachedEnvoyId("e-new")
    );

    // VERIFY
    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");
    verify(envoyResourceManagement).getOne("t-1", "r-1");
    verify(boundMonitorRepository).findMonitorIdsBoundToTenantAndResource("t-1", "r-1");
    verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(monitor.getId(), "r-1");
    verify(policyApi).getDefaultMonitoringZones(MetadataPolicy.DEFAULT_ZONE, true);
    // nothing new bound and no affected envoy events

    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
        zoneStorage, monitorEventProducer, resourceApi, resourceRepository, policyApi);
  }

  @Test
  public void testhandleResourceEvent_removedResource() {
    when(resourceRepository.findByTenantIdAndResourceId(any(), any()))
        .thenReturn(Optional.empty());

    final Monitor monitor = new Monitor()
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setMonitorType(MonitorType.cpu)
        .setTenantId("t-1")
        .setLabelSelector(Collections.singletonMap("env", "prod"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setAgentType(AgentType.TELEGRAF)
        .setContent("domain=${resource.labels.env}")
        .setInterval(Duration.ofSeconds(60));
    entityManager.persist(monitor);
    entityManager.flush();

    final BoundMonitor boundMonitor = new BoundMonitor()
        .setMonitor(monitor)
        .setResourceId("r-1")
        .setZoneName("")
        .setRenderedContent("content is ignored")
        .setEnvoyId("e-1");

    when(boundMonitorRepository.findMonitorIdsBoundToTenantAndResource(any(), any()))
        .thenReturn(Collections.singletonList(monitor.getId()));

    when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(any(), any()))
        .thenReturn(Collections.singletonList(boundMonitor));

    when(boundMonitorRepository.findMonitorsBoundToTenantAndResourceAndMonitor_IdIn(anyString(),anyString(), any()))
        .thenReturn(Collections.singletonList(boundMonitor));

    // EXERCISE

    monitorManagement.handleResourceChangeEvent(new ResourceEvent()
        .setTenantId("t-1")
        .setResourceId("r-1"));

    // VERIFY

    verify(resourceRepository).findByTenantIdAndResourceId("t-1", "r-1");

    verify(monitorEventProducer).sendMonitorEvent(
        new MonitorBoundEvent()
            .setEnvoyId("e-1")
    );

    verify(boundMonitorRepository).findMonitorIdsBoundToTenantAndResource("t-1", "r-1");

    verify(boundMonitorRepository).deleteAll(
        Collections.singletonList(boundMonitor)
    );

    verify(boundMonitorRepository).findMonitorsBoundToTenantAndResourceAndMonitor_IdIn(
        "t-1","r-1",Collections.singleton(monitor.getId()));

    verifyNoInteractions(policyApi);
    verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
        zoneStorage, monitorEventProducer, resourceApi, resourceRepository);
  }

  @Test
  public void testGetTenantMonitorLabelSelectors() {
    Map<String, String> labels1 = new HashMap<>();
    labels1.put("key1", "value-1-1");
    labels1.put("key2", "value-2-1");
    labels1.put("key3", "value-3-1");
    persistNewMonitor("t-1", labels1);

    Map<String, String> labels2 = new HashMap<>();
    labels2.put("key1", "value-1-1");
    labels2.put("key2", "value-2-2");
    labels2.put("key3", "value-3-2");
    persistNewMonitor("t-1", labels2);

    Map<String, String> labels3 = new HashMap<>();
    labels3.put("key1", "value-1-2");
    labels3.put("key2", "value-2-2");
    labels3.put("key3", "value-3-2");
    persistNewMonitor("t-1", labels3);

    Map<String, String> labels4 = new HashMap<>();
    labels4.put("key1", "value-1-x");
    labels4.put("key2", "value-2-x");
    labels4.put("key3", "value-3-x");
    persistNewMonitor("t-2", labels4);

    final MultiValueMap<String, String> results = monitorManagement
        .getTenantMonitorLabelSelectors("t-1");

    assertThat(results.size(), equalTo(3));
    assertThat(results, hasKey("key1"));
    assertThat(results, hasKey("key2"));
    assertThat(results, hasKey("key3"));
    assertThat(results.get("key1"), containsInAnyOrder("value-1-1", "value-1-2"));
    assertThat(results.get("key2"), containsInAnyOrder("value-2-1", "value-2-2"));
    assertThat(results.get("key3"), containsInAnyOrder("value-3-1", "value-3-2"));
  }

  @Test
  public void testSearchOnTenantName() {
    // we will use the setup one
    monitorRepository.save(new Monitor()
        .setTenantId("t-1")
        .setMonitorName("mon1")
        .setMonitorType(MonitorType.cpu)
        .setLabelSelector(Collections.singletonMap("os", "LINUX"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setContent("content1")
        .setAgentType(AgentType.FILEBEAT)
        .setInterval(Duration.ofSeconds(60)));

    monitorRepository.save(new Monitor()
        .setTenantId("t-1")
        .setMonitorName("otherMonitor")
        .setMonitorType(MonitorType.cpu)
        .setLabelSelector(Collections.singletonMap("os", "LINUX"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setContent("content1")
        .setAgentType(AgentType.FILEBEAT)
        .setInterval(Duration.ofSeconds(60)));

    monitorRepository.save(new Monitor()
        .setTenantId("t-2")
        .setMonitorName("otherMonitor")
        .setMonitorType(MonitorType.cpu)
        .setLabelSelector(Collections.singletonMap("os", "LINUX"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setContent("content1")
        .setAgentType(AgentType.FILEBEAT)
        .setInterval(Duration.ofSeconds(60)));
    Pageable page = PageRequest.of(0, 1);
    Page<Monitor> value = monitorManagement.getMonitorsBySearchString("t-1", "mon", page);
    // Since we used a native query we want to test the paging since its its own separate query
    // TotalElements is the total number of elements returned from the paged request
    assertThat(value.getTotalElements(), equalTo(2L));
    assertThat(value.getTotalPages(), equalTo(2));
    // NumberOfElements is the number of elements on this page
    assertThat(value.getNumberOfElements(), equalTo(1));
  }

  @Test
  public void testSearchOnId() {
    // we will use the setup one
    Monitor savedMonitor = monitorRepository.save(new Monitor()
        .setTenantId("t-1")
        .setMonitorName("mon1")
        .setMonitorType(MonitorType.cpu)
        .setLabelSelector(Collections.singletonMap("os", "LINUX"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setContent("content1")
        .setAgentType(AgentType.FILEBEAT)
        .setInterval(Duration.ofSeconds(60)));

    monitorRepository.save(new Monitor()
        .setTenantId("t-1")
        .setMonitorName("otherMonitor")
        .setMonitorType(MonitorType.cpu)
        .setLabelSelector(Collections.singletonMap("os", "LINUX"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setContent("content1")
        .setAgentType(AgentType.FILEBEAT)
        .setInterval(Duration.ofSeconds(60)));

    monitorRepository.save(new Monitor()
        .setTenantId("t-2")
        .setMonitorName("otherMonitor")
        .setMonitorType(MonitorType.cpu)
        .setLabelSelector(Collections.singletonMap("os", "LINUX"))
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setContent("content1")
        .setAgentType(AgentType.FILEBEAT)
        .setInterval(Duration.ofSeconds(60)));
    Pageable page = PageRequest.of(0, 1);

    UUID searchId = savedMonitor.getId();
    String searchIdSubString = searchId.toString().substring(10, 16);

    Page<Monitor> value = monitorManagement.getMonitorsBySearchString("t-1", searchIdSubString, page);
    assertThat(value.getTotalElements(), equalTo(1L));
    assertThat(value.getTotalPages(), equalTo(1));
    assertThat(value.getNumberOfElements(), equalTo(1));
  }

  @Test
  public void deleteAllMonitorsForTenant() {
    when(boundMonitorRepository
        .findAllByTenantIdAndMonitor_IdIn(any(), any(), any()))
        .thenReturn(Page.empty());

    createMonitorsForTenant(20, "t-1");

    monitorManagement.removeAllTenantMonitors("t-1", true);

    Page<Monitor> result = monitorManagement.getMonitors("t-1", Pageable.unpaged());

    assertThat(result.getNumberOfElements(), equalTo(0));
    verify(monitorEventProducer, times(20)).sendMonitorEvent(any());
  }

  @Test
  public void deleteAllMonitorsForTenant_noEvents() {
    createMonitorsForTenant(20, "t-1");

    when(boundMonitorRepository
        .findAllByTenantIdAndMonitor_IdIn(any(), any(), any()))
        .thenReturn(Page.empty());
    Page<Monitor> monitorsBeforeDeletion = monitorManagement.getMonitors("t-1", Pageable.unpaged());

    monitorManagement.removeAllTenantMonitors("t-1", false);

    List<UUID> monitorIds = monitorsBeforeDeletion.get()
        .map(Monitor::getId)
        .collect(Collectors.toList());

    Page<Monitor> monitorsAfterDeletion = monitorManagement.getMonitors("t-1", Pageable.unpaged());

    verify(boundMonitorRepository)
        .findAllByTenantIdAndMonitor_IdIn("t-1", monitorIds, PageRequest.of(0, 1000));
    assertThat(monitorsAfterDeletion.getNumberOfElements(), equalTo(0));
  }

  @Test
  public void testPatchExistingMonitor_nonExistingResource() {

    String resourceId = RandomStringUtils.randomAlphanumeric(10);
    String tenantId = RandomStringUtils.randomAlphanumeric(10);

    final Monitor monitor = new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.cpu)
        .setContent("static content")
        .setTenantId(tenantId)
        .setResourceId(resourceId)
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setLabelSelector(Map.of("os", "linux"))
        .setZones(Collections.emptyList())
        .setInterval(Duration.ofSeconds(60));
    entityManager.persist(monitor);

    // Called when binding new resource
    when(resourceRepository.findByTenantIdAndResourceId(tenantId, resourceId))
        .thenReturn(Optional.empty());

    // Called when unbinding old resources
    when(boundMonitorRepository.findResourceIdsBoundToMonitor(any()))
        .thenReturn(Collections.emptySet());

    when(resourceRepository.findByTenantIdAndResourceId(tenantId, resourceId))
        .thenReturn(Optional.empty());

    // EXECUTE

    final MonitorCU update = new MonitorCU()
        .setResourceId(resourceId)
        .setLabelSelector(null)
        // for a patch we need to set all the other values to the same as the original
        .setZones(monitor.getZones())
        .setMonitorType(monitor.getMonitorType())
        .setMonitorName(monitor.getMonitorName())
        .setContent(monitor.getContent())
        .setAgentType(monitor.getAgentType())
        .setLabelSelectorMethod(monitor.getLabelSelectorMethod())
        .setInterval(monitor.getInterval())
        .setSelectorScope(monitor.getSelectorScope())
        .setPluginMetadataFields(monitor.getPluginMetadataFields());

    final Monitor updatedMonitor = monitorManagement
        .updateMonitor(tenantId, monitor.getId(), update, true);

    // VERIFY
    // confirm monitor is updated
    assertThat(Collections.singleton(updatedMonitor))
        .usingElementComparatorIgnoringFields("createdTimestamp", "updatedTimestamp")
        .containsExactly(
            new Monitor()
                .setId(monitor.getId())
                .setAgentType(AgentType.TELEGRAF)
                .setMonitorType(MonitorType.cpu)
                .setContent("static content")
                .setMonitorMetadataFields(List.of("monitorName"))
                .setTenantId(tenantId)
                .setSelectorScope(ConfigSelectorScope.LOCAL)
                .setResourceId(resourceId)
                .setLabelSelector(null)
                .setLabelSelectorMethod(LabelSelectorMethod.AND)
                .setInterval(Duration.ofSeconds(60)));

    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resourceId);

    verify(boundMonitorRepository).findResourceIdsBoundToMonitor(monitor.getId());

    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resourceId);

    verifyNoMoreInteractions(monitorEventProducer);
  }

  @Test
  public void testRenderedMonitorTemplate() throws IOException, InvalidTemplateException {

    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    String resourceId = RandomStringUtils.randomAlphanumeric(10);

    String monitorContent = "value=${resource.labels.os}";

    final Monitor monitor = new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.ping)
        .setContent(monitorContent)
        .setTenantId(tenantId)
        .setResourceId(null)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setZones(Collections.emptyList())
        .setInterval(Duration.ofSeconds(60));
    monitorRepository.save(monitor);

    final Resource resource = podamFactory.manufacturePojo(Resource.class);
    resource.setResourceId(resourceId);
    resource.setTenantId(tenantId);
    resource.setLabels(Collections.singletonMap("os", "linux"));

    final ResourceDTO r1 = new ResourceDTO(resource, null);

    when(resourceRepository.findByTenantIdAndResourceId(tenantId, resourceId)).thenReturn(Optional.of(resource));

    final RenderedMonitorTemplate renderedMonitorTemplate = monitorManagement
        .renderMonitorTemplate(monitor.getId(), resourceId, tenantId);

    assertEquals(renderedMonitorTemplate.getMonitor().getId(), monitor.getId());
    assertNotNull(renderedMonitorTemplate.getRenderedContent());
    assertEquals(renderedMonitorTemplate.getRenderedContent(), "value=linux");

    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resourceId);
  }

  @Test
  public void testRenderedMonitorTemplate_MonitorNotFound() {

    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    String resourceId = RandomStringUtils.randomAlphanumeric(10);
    UUID monitorId = UUID.randomUUID();

    assertThatThrownBy(() -> monitorManagement.renderMonitorTemplate(monitorId, resourceId, tenantId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage(
            String.format("No monitor found for %s", monitorId)
        );

  }

  @Test
  public void testRenderedMonitorTemplate_ResourceNotPresentInRequest() throws IOException {

    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    String resourceId = RandomStringUtils.randomAlphanumeric(10);

    String monitorContent = readContent("/MonitorConversionServiceTest_ping_with_policy.json");

    final Monitor monitor = new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.ping)
        .setContent(monitorContent)
        .setTenantId(tenantId)
        .setResourceId(null)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setZones(Collections.emptyList())
        .setInterval(Duration.ofSeconds(60));
    monitorRepository.save(monitor);

    RenderedMonitorTemplate renderedMonitorTemplate = monitorManagement
        .renderMonitorTemplate(monitor.getId(), null, tenantId);

    assertEquals(renderedMonitorTemplate.getMonitor().getId(), monitor.getId());
    assertEquals(renderedMonitorTemplate.getRenderedContent(), monitor.getContent());
  }

  @Test
  public void testRenderedMonitorTemplate_ResourceNotFound() throws IOException {

    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    String resourceId = RandomStringUtils.randomAlphanumeric(10);

    String monitorContent = readContent("/MonitorConversionServiceTest_ping_with_policy.json");

    final Monitor monitor = new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.ping)
        .setContent(monitorContent)
        .setTenantId(tenantId)
        .setResourceId(null)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setZones(Collections.emptyList())
        .setInterval(Duration.ofSeconds(60));
    monitorRepository.save(monitor);

    when(resourceRepository.findByTenantIdAndResourceId(tenantId, resourceId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> monitorManagement.renderMonitorTemplate(monitor.getId(), resourceId, tenantId))
        .isInstanceOf(NotFoundException.class)
        .hasMessage(
            String.format("Invalid resourceId=%s provided when rendering monitorId=%s template for tenantId=%s",
                resourceId, monitor.getId(), tenantId)
        );

    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resourceId);
  }

  @Test
  public void testRenderedMonitorTemplate_InvalidTemplate() throws IOException {

    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    String resourceId = RandomStringUtils.randomAlphanumeric(10);

    String monitorContent = "value=${resource.wrong.reference}";

    final Monitor monitor = new Monitor()
        .setAgentType(AgentType.TELEGRAF)
        .setMonitorType(MonitorType.ping)
        .setContent(monitorContent)
        .setTenantId(tenantId)
        .setResourceId(null)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setZones(Collections.emptyList())
        .setInterval(Duration.ofSeconds(60));
    monitorRepository.save(monitor);

    final Resource resource = podamFactory.manufacturePojo(Resource.class);
    resource.setResourceId(resourceId);
    resource.setTenantId(tenantId);

    final ResourceDTO r1 = new ResourceDTO(resource, null);

    when(resourceRepository.findByTenantIdAndResourceId(tenantId, resourceId)).thenReturn(Optional.of(resource));

    assertThatThrownBy(() -> monitorManagement.renderMonitorTemplate(monitor.getId(), resourceId, tenantId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            String.format("Unable to render content=%s for resource=%s",
                monitor.getContent(), r1)
        );
    verify(resourceRepository).findByTenantIdAndResourceId(tenantId, resourceId);
  }
}
