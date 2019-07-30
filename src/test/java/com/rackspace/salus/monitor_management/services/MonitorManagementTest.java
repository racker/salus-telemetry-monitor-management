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

import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.createPrivateZone;
import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.createPublicZone;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import com.rackspace.salus.monitor_management.entities.Monitor;
import com.rackspace.salus.monitor_management.entities.Zone;
import com.rackspace.salus.monitor_management.repositories.BoundMonitorRepository;
import com.rackspace.salus.monitor_management.repositories.MonitorRepository;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.ZoneAssignmentCount;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;


@RunWith(SpringRunner.class)
@DataJpaTest(showSql = false)
@Import({ServicesProperties.class, ObjectMapper.class, MonitorManagement.class,
    MonitorContentRenderer.class,
    MonitorContentProperties.class})
public class MonitorManagementTest {

    private static final String DEFAULT_ENVOY_ID = "env1";
    private static final String DEFAULT_RESOURCE_ID = "os:LINUX";

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
    ResourceApi resourceApi;

    @MockBean
    ZoneManagement zoneManagement;

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MonitorRepository monitorRepository;
    @Autowired
    EntityManager entityManager;
    @Autowired
    JdbcTemplate jdbcTemplate;

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
                .setLabelSelector(Collections.singletonMap("os", "LINUX"))
                .setContent("content1")
                .setAgentType(AgentType.FILEBEAT);
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
        );

        when(resourceApi.getResourcesWithLabels(any(), any()))
            .thenReturn(resourceList);
    }

    private void createMonitors(int count) {
        for (int i = 0; i < count; i++) {
            String tenantId = RandomStringUtils.randomAlphanumeric(10);
            MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
            // limit to local/agent monitors only
            create.setSelectorScope(ConfigSelectorScope.LOCAL);
            create.setZones(Collections.emptyList());
            monitorManagement.createMonitor(tenantId, create);
        }
    }

    private void createMonitorsForTenant(int count, String tenantId) {
        for (int i = 0; i < count; i++) {
            MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
            create.setSelectorScope(ConfigSelectorScope.LOCAL);
            create.setZones(Collections.emptyList());
            monitorManagement.createMonitor(tenantId, create);
        }
    }

    private void createMonitorsForTenant(int count, String tenantId, Map<String, String> labels) {
        for (int i = 0; i < count; i++) {
            MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
            create.setSelectorScope(ConfigSelectorScope.LOCAL);
            create.setZones(Collections.emptyList());
            create.setLabelSelector(labels);
            monitorManagement.createMonitor(tenantId, create);
        }
    }

    private Monitor persistNewMonitor(String tenantId) {
        return persistNewMonitor(tenantId, Collections.singletonMap("os", "LINUX"));
    }

    private Monitor persistNewMonitor(
        String tenantId, Map<String, String> labelSelector) {
        return monitorRepository.save(
            new Monitor()
                .setTenantId(tenantId)
                .setAgentType(AgentType.TELEGRAF)
                .setSelectorScope(ConfigSelectorScope.LOCAL)
                .setLabelSelector(labelSelector)
                .setAgentType(AgentType.TELEGRAF)
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
    public void testGetPolicyMonitor() {
        final Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
        monitor.setTenantId(MonitorManagement.POLICY_TENANT);
        Monitor saved = monitorRepository.save(monitor);

        Optional<Monitor> m = monitorManagement.getPolicyMonitor(saved.getId());

        assertTrue(m.isPresent());
        assertThat(m.get().getTenantId(), equalTo(MonitorManagement.POLICY_TENANT));
        assertThat(m.get().getId(), equalTo(saved.getId()));
        assertThat(m.get().getLabelSelector(), equalTo(saved.getLabelSelector()));
        assertThat(m.get().getContent(), equalTo(saved.getContent()));
        assertThat(m.get().getAgentType(), equalTo(saved.getAgentType()));
    }

    @Test
    public void testCreateNewMonitor() {
        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setSelectorScope(ConfigSelectorScope.LOCAL);
        create.setZones(null);

        String tenantId = RandomStringUtils.randomAlphanumeric(10);

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
    }

    @Test
    public void testCreateNewMonitor_EmptyLabelSelector() {
        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setSelectorScope(ConfigSelectorScope.LOCAL);
        create.setZones(null);
        create.setLabelSelector(Collections.emptyMap());

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
    public void testCreateNewMonitor_LocalWithZones() {
        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        // zones gets populated by podam
        create.setSelectorScope(ConfigSelectorScope.LOCAL);
        String tenantId = RandomStringUtils.randomAlphanumeric(10);

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Local monitors cannot have zones");
        Monitor returned = monitorManagement.createMonitor(tenantId, create);

        verifyNoMoreInteractions(envoyResourceManagement, resourceApi, boundMonitorRepository);
    }

    @Test
    public void testCreateNewMonitor_InvalidTemplate_Local() {
        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setSelectorScope(ConfigSelectorScope.LOCAL);
        create.setZones(Collections.emptyList());
        create.setContent("value=${does_not_exist}");
        String tenantId = RandomStringUtils.randomAlphanumeric(10);

        Monitor returned = monitorManagement.createMonitor(tenantId, create);
        assertThat(returned.getId(), notNullValue());

        Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, returned.getId());
        assertTrue(retrieved.isPresent());

        verify(envoyResourceManagement).getOne(tenantId, DEFAULT_RESOURCE_ID);

        verify(resourceApi).getResourcesWithLabels(tenantId, create.getLabelSelector());

        // ...but no bindings created, verified by no interaction with boundMonitorRepository
        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi,
            zoneManagement);
    }

    @Test
    public void testCreateNewMonitor_InvalidTemplate_Remote() {
        String tenantId = RandomStringUtils.randomAlphanumeric(10);

        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setSelectorScope(ConfigSelectorScope.REMOTE);

        create.setContent("value=${does_not_exist}");

        List<Zone> zones = podamFactory.manufacturePojo(ArrayList.class, Zone.class);
        create.setZones(zones.stream().map(Zone::getName).distinct().filter(Objects::nonNull).collect(Collectors.toList()));
        create.setLabelSelector(Collections.emptyMap());

        when(zoneManagement.getAvailableZonesForTenant(any(), any()))
            .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

        Monitor returned = monitorManagement.createMonitor(tenantId, create);
        assertThat(returned.getId(), notNullValue());

        Optional<Monitor> retrieved = monitorManagement.getMonitor(tenantId, returned.getId());
        assertTrue(retrieved.isPresent());

        verify(resourceApi).getResourcesWithLabels(tenantId, create.getLabelSelector());

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

        when(resourceApi.getResourcesWithLabels("t-1", Collections.singletonMap("new", "yes")))
            .thenReturn(Arrays.asList(r2, r3));

        final Map<String, String> oldLabelSelector = new HashMap<>();
        oldLabelSelector.put("old", "yes");
        final Monitor monitor = new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{}")
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setLabelSelector(oldLabelSelector);
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

        verify(resourceApi).getResourcesWithLabels("t-1", Collections.singletonMap("new", "yes"));

        verify(boundMonitorRepository).findResourceIdsBoundToMonitor(monitor.getId());

        verify(boundMonitorRepository).saveAll(Collections.singletonList(
            new BoundMonitor()
                .setMonitor(monitor)
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
    public void testUpdateExistingMonitor_contentChanged() {
        reset(envoyResourceManagement, resourceApi);

        // This resource will result in a change to rendered content
        final Map<String, String> r1metadata = new HashMap<>();
        r1metadata.put("ping_ip", "something_else");
        r1metadata.put("address", "localhost");
        final ResourceDTO r1 = new ResourceDTO()
            .setLabels(Collections.singletonMap("os", "linux"))
            .setMetadata(r1metadata)
            .setResourceId("r-1")
            .setTenantId("t-1");

        when(resourceApi.getByResourceId("t-1", "r-1"))
            .thenReturn(r1);

        // ...and this resource will NOT since both metadata values are the same
        final Map<String, String> r2metadata = new HashMap<>();
        r2metadata.put("ping_ip", "localhost");
        r2metadata.put("address", "localhost");
        final ResourceDTO r2 = new ResourceDTO()
            .setLabels(Collections.singletonMap("os", "linux"))
            .setMetadata(r2metadata)
            .setResourceId("r-2")
            .setTenantId("t-1");

        when(resourceApi.getByResourceId("t-1", "r-2"))
            .thenReturn(r2);

        final Monitor monitor = new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("address=${resource.metadata.ping_ip}")
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setLabelSelector(Collections.singletonMap("os", "linux"));
        entityManager.persist(monitor);

        final BoundMonitor bound1 = new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setEnvoyId("e-1")
            .setZoneName("z-1")
            .setRenderedContent("address=something_else");
        entityManager.persist(bound1);

        final BoundMonitor bound2 = new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-2")
            .setEnvoyId("e-2")
            .setZoneName("z-1")
            .setRenderedContent("address=localhost");
        entityManager.persist(bound2);

        // same resource r-2, but different zone to ensure query-by-resource is normalize to one query each
        final BoundMonitor bound3 = new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-2")
            .setEnvoyId("e-3")
            .setZoneName("z-2")
            .setRenderedContent("address=localhost");
        entityManager.persist(bound3);

        when(boundMonitorRepository.findAllByMonitor_Id(monitor.getId()))
            .thenReturn(Arrays.asList(bound1, bound2, bound3));

        // EXECUTE

        final MonitorCU update = new MonitorCU()
            .setContent("address=${resource.metadata.address}");
        final Monitor updatedMonitor = monitorManagement.updateMonitor("t-1", monitor.getId(), update);

        // VERIFY

        assertThat(updatedMonitor, equalTo(
            new Monitor()
                .setId(monitor.getId())
                .setAgentType(AgentType.TELEGRAF)
                .setContent("address=${resource.metadata.address}")
                .setTenantId("t-1")
                .setSelectorScope(ConfigSelectorScope.REMOTE)
                .setLabelSelector(Collections.singletonMap("os", "linux"))
        ));

        verify(boundMonitorRepository).findAllByMonitor_Id(monitor.getId());

        verify(boundMonitorRepository).saveAll(Collections.singletonList(
            new BoundMonitor()
                .setMonitor(monitor)
                .setResourceId("r-1")
                .setEnvoyId("e-1")
                .setZoneName("z-1")
                .setRenderedContent("address=localhost")
        ));

        verify(resourceApi).getByResourceId("t-1", "r-1");
        // even though two bindings for r-2, the queries were grouped by resource and only one call here
        verify(resourceApi).getByResourceId("t-1", "r-2");

        verify(monitorEventProducer).sendMonitorEvent(
            new MonitorBoundEvent().setEnvoyId("e-1")
        );

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi,
            zoneStorage, monitorEventProducer);
    }

    @Test
    public void testUpdateExistingMonitor_zonesChanged() {
        reset(envoyResourceManagement, resourceApi);

        when(resourceApi.getResourcesWithLabels(any(), any()))
            .thenReturn(Collections.singletonList(
                new ResourceDTO()
                .setResourceId("r-1")
            ));

        final Monitor monitor = new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{}")
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Arrays.asList("z-1", "z-2"))
            .setLabelSelector(Collections.singletonMap("os", "linux"));
        entityManager.persist(monitor);

        EnvoyResourcePair pair = new EnvoyResourcePair().setEnvoyId("e-new").setResourceId("r-new-1");

        when(zoneStorage.findLeastLoadedEnvoy(any()))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(pair)));
        when(zoneStorage.incrementBoundCount(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(1));

        final BoundMonitor boundZ1 = new BoundMonitor()
            .setMonitor(monitor)
            .setZoneName("z-1")
            .setResourceId("r-1")
            .setRenderedContent("{}")
            .setEnvoyId("e-existing");
        entityManager.persist(boundZ1);
        when(boundMonitorRepository.findAllByMonitor_IdAndZoneNameIn(
            monitor.getId(), Collections.singletonList("z-1")
        ))
        .thenReturn(Collections.singletonList(
            boundZ1
        ));

        final BoundMonitor boundZ2 = new BoundMonitor()
            .setMonitor(monitor)
            .setZoneName("z-2")
            .setResourceId("r-1")
            .setRenderedContent("{}")
            .setEnvoyId("e-existing");
        entityManager.persist(boundZ2);

        List<Zone> zones = Arrays.asList(
            new Zone().setName("z-1"),
            new Zone().setName("z-2"),
            new Zone().setName("z-3")
        );

        when(zoneManagement.getAvailableZonesForTenant("t-1", Pageable.unpaged()))
            .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

        when(zoneStorage.getEnvoyIdToResourceIdMap(any()))
            .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap("e-existing", "r-exist")));

        // EXECUTE

        final MonitorCU update = new MonitorCU()
            .setZones(Arrays.asList("z-2", "z-3"));

        final Monitor updatedMonitor = monitorManagement.updateMonitor("t-1", monitor.getId(), update);

        // VERIFY

        assertThat(updatedMonitor, equalTo(
            new Monitor()
                .setId(monitor.getId())
                .setAgentType(AgentType.TELEGRAF)
                .setContent("{}")
                .setTenantId("t-1")
                .setSelectorScope(ConfigSelectorScope.REMOTE)
                .setZones(Arrays.asList("z-2", "z-3"))
                .setLabelSelector(Collections.singletonMap("os", "linux"))
        ));

        verify(resourceApi).getResourcesWithLabels("t-1", Collections.singletonMap("os", "linux"));

        final ResolvedZone resolvedZ3 = createPrivateZone("t-1", "z-3");
        verify(zoneStorage).findLeastLoadedEnvoy(resolvedZ3);
        verify(zoneStorage).incrementBoundCount(resolvedZ3, "r-new-1");
        verify(zoneStorage).decrementBoundCount(createPrivateZone("t-1", "z-1"), "r-exist");
        verify(zoneStorage).getEnvoyIdToResourceIdMap(createPrivateZone("t-1", "z-1"));

        verify(boundMonitorRepository)
            .findAllByMonitor_IdAndZoneNameIn(monitor.getId(), Collections.singletonList("z-1"));

        verify(boundMonitorRepository).deleteAll(Collections.singletonList(boundZ1));

        verify(boundMonitorRepository).saveAll(Collections.singletonList(
            new BoundMonitor()
                .setMonitor(monitor)
                .setZoneName("z-3")
                .setResourceId("r-1")
                .setRenderedContent("{}")
                .setEnvoyId("e-new")
        ));

        verify(monitorEventProducer).sendMonitorEvent(
            new MonitorBoundEvent().setEnvoyId("e-existing")
        );
        verify(monitorEventProducer).sendMonitorEvent(
            new MonitorBoundEvent().setEnvoyId("e-new")
        );

        verify(zoneManagement).getAvailableZonesForTenant("t-1", Pageable.unpaged());

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi,
            zoneStorage, monitorEventProducer, zoneManagement);
    }

    @Test
    public void testUpdateExistingMonitor_zonesOnlyChangedOrder() {
        reset(envoyResourceManagement, resourceApi);

        final Monitor monitor = new Monitor()
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{}")
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setZones(Arrays.asList("z-1", "z-2"))
            .setLabelSelector(Collections.singletonMap("os", "linux"));
        entityManager.persist(monitor);

        List<Zone> zones = Arrays.asList(
            new Zone().setName("z-1"),
            new Zone().setName("z-2")
        );

        when(zoneManagement.getAvailableZonesForTenant(any(), any()))
            .thenReturn(new PageImpl<>(zones, Pageable.unpaged(), zones.size()));

        // EXECUTE

        final MonitorCU update = new MonitorCU()
            .setZones(Arrays.asList("z-2", "z-1"));

        final Monitor updatedMonitor = monitorManagement.updateMonitor("t-1", monitor.getId(), update);

        // VERIFY

        assertThat(updatedMonitor, equalTo(
            new Monitor()
                .setId(monitor.getId())
                .setAgentType(AgentType.TELEGRAF)
                .setContent("{}")
                .setTenantId("t-1")
                .setSelectorScope(ConfigSelectorScope.REMOTE)
                .setZones(Arrays.asList("z-1", "z-2"))
                .setLabelSelector(Collections.singletonMap("os", "linux"))
        ));

        verify(zoneManagement).getAvailableZonesForTenant("t-1", Pageable.unpaged());

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi,
            zoneStorage, monitorEventProducer, zoneManagement);
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
                .setContent("{}")
                .setTenantId("t-1")
                .setSelectorScope(ConfigSelectorScope.REMOTE)
                .setZones(Collections.singletonList("z-1"))
                .setLabelSelector(Collections.singletonMap("os", "linux")));

        final BoundMonitor boundMonitor = new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setZoneName("z-1")
            .setRenderedContent("{}")
            .setEnvoyId("e-goner");

        when(boundMonitorRepository.findAllByMonitor_IdIn(any()))
            .thenReturn(Collections.singletonList(boundMonitor));

        when(zoneStorage.getEnvoyIdToResourceIdMap(any()))
            .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap("e-goner", "r-gone")));

        // EXECUTE

        monitorManagement.removeMonitor("t-1", monitor.getId());

        // VERIFY

        final Optional<Monitor> retrieved = monitorManagement.getMonitor("t-1", monitor.getId());
        assertThat(retrieved.isPresent(), equalTo(false));

        verify(boundMonitorRepository).findAllByMonitor_IdIn(Collections.singletonList(monitor.getId()));

        verify(boundMonitorRepository).deleteAll(Collections.singletonList(boundMonitor));

        verify(zoneStorage).decrementBoundCount(
            ResolvedZone.createPrivateZone("t-1", "z-1"),
            "r-gone"
        );
        verify(zoneStorage).getEnvoyIdToResourceIdMap(ResolvedZone.createPrivateZone("t-1", "z-1"));

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
        int monitorsWithoutLabels = new Random().nextInt(10) + 20;
        String tenantId = RandomStringUtils.randomAlphabetic(10);

        Map<String, String> labels = Collections.singletonMap("mykey", "myvalue");

        // Create monitors which don't have the labels we care about
        createMonitorsForTenant(monitorsWithoutLabels, tenantId);

        // Create monitors which do have the labels we care about
        createMonitorsForTenant(monitorsWithLabels, tenantId, labels);

        entityManager.flush();

        Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
        assertEquals(monitorsWithLabels, monitors.getTotalElements());
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
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        monitorManagement.createMonitor(tenantId, create);
        entityManager.flush();
        Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(queryLabels, tenantId, Pageable.unpaged());
        assertEquals(0L, monitors.getTotalElements());
    }

    @Test
    public void testEmptyLabelsException() {
        final Map<String, String> labels = new HashMap<>();

        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setZones(Collections.emptyList());
        create.setLabelSelector(labels);
        create.setSelectorScope(ConfigSelectorScope.LOCAL);
        String tenantId = RandomStringUtils.randomAlphanumeric(10);

        when(zoneManagement.getAvailableZonesForTenant(any(), any()))
            .thenReturn(Page.empty());

        monitorManagement.createMonitor(tenantId, create);
        entityManager.flush();

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Labels must be provided for search");
        monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
    }

    @Test
    public void testMonitorWithSameLabelsAndDifferentTenants() {
        final Map<String, String> labels = new HashMap<>();
        labels.put("key", "value");

        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setLabelSelector(labels);
        create.setSelectorScope(ConfigSelectorScope.LOCAL);
        create.setZones(Collections.emptyList());
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
        create.setZones(Collections.emptyList());
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
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        monitorManagement.createMonitor(tenantId, create);
        entityManager.flush();

        Page<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId, Pageable.unpaged());
        assertEquals(0L, monitors.getTotalElements()); //make sure we only returned the one value
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
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{}");

        final Set<String> affectedEnvoys = monitorManagement.bindNewMonitor(monitor);

        final List<BoundMonitor> expected = Collections.singletonList(
            new BoundMonitor()
                .setResourceId(DEFAULT_RESOURCE_ID)
                .setMonitor(monitor)
                .setEnvoyId(DEFAULT_ENVOY_ID)
                .setRenderedContent("{}")
                .setZoneName("")
        );
        verify(boundMonitorRepository).saveAll(
            expected
        );

        assertThat(affectedEnvoys, contains(DEFAULT_ENVOY_ID));

        verifyNoMoreInteractions(monitorEventProducer, boundMonitorRepository);
    }

    @Test
    public void testDistributeNewMonitor_remote() {
        final ResolvedZone zone1 = createPrivateZone("t-1", "zone1");
        final ResolvedZone zoneWest = createPublicZone("public/west");


        when(zoneStorage.findLeastLoadedEnvoy(zone1))
            .thenReturn(CompletableFuture.completedFuture(
                Optional.of(
                    new EnvoyResourcePair().setEnvoyId("zone1-e-1").setResourceId("r-e-1"))
            ));
        when(zoneStorage.findLeastLoadedEnvoy(zoneWest))
            .thenReturn(CompletableFuture.completedFuture(
                Optional.of(new EnvoyResourcePair().setEnvoyId("zoneWest-e-2").setResourceId("r-e-2"))
            ));
        when(zoneStorage.incrementBoundCount(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(1));

        final List<ResourceDTO> tenantResources = new ArrayList<>();
        tenantResources.add(
            new ResourceDTO().setResourceId("r-1")
                .setLabels(Collections.singletonMap("os", "LINUX"))
                .setMetadata(Collections.singletonMap("public_ip", "151.1.1.1"))
        );
        tenantResources.add(
            new ResourceDTO().setResourceId("r-2")
                .setLabels(Collections.singletonMap("os", "LINUX"))
                .setMetadata(Collections.singletonMap("public_ip", "151.2.2.2"))
        );
        reset(resourceApi);
        when(resourceApi.getResourcesWithLabels(any(), any()))
            .thenReturn(tenantResources);

        Monitor monitor = new Monitor()
            .setId(UUID.randomUUID())
            .setTenantId("t-1")
            .setAgentType(AgentType.TELEGRAF)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setLabelSelector(Collections.singletonMap("os", "LINUX"))
            .setZones(Arrays.asList("zone1", "public/west"))
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{\"type\": \"ping\", \"urls\": [\"${resource.metadata.public_ip}\"]}");

        final Set<String> affectedEnvoys = monitorManagement.bindNewMonitor(monitor);

        final List<BoundMonitor> expected = Arrays.asList(
            new BoundMonitor()
                .setResourceId("r-1")
                .setMonitor(monitor)
                .setEnvoyId("zone1-e-1")
                .setRenderedContent("{\"type\": \"ping\", \"urls\": [\"151.1.1.1\"]}")
                .setZoneName("zone1"),
            new BoundMonitor()
                .setResourceId("r-1")
                .setMonitor(monitor)
                .setEnvoyId("zoneWest-e-2")
                .setRenderedContent("{\"type\": \"ping\", \"urls\": [\"151.1.1.1\"]}")
                .setZoneName("public/west"),
            new BoundMonitor()
                .setResourceId("r-2")
                .setMonitor(monitor)
                .setEnvoyId("zone1-e-1")
                .setRenderedContent("{\"type\": \"ping\", \"urls\": [\"151.2.2.2\"]}")
                .setZoneName("zone1"),
            new BoundMonitor()
                .setResourceId("r-2")
                .setMonitor(monitor)
                .setEnvoyId("zoneWest-e-2")
                .setRenderedContent("{\"type\": \"ping\", \"urls\": [\"151.2.2.2\"]}")
                .setZoneName("public/west")
        );
        verify(boundMonitorRepository).saveAll(expected);

        assertThat(affectedEnvoys, containsInAnyOrder("zone1-e-1", "zoneWest-e-2"));

        verify(zoneStorage, times(2)).findLeastLoadedEnvoy(zone1);
        verify(zoneStorage, times(2)).findLeastLoadedEnvoy(zoneWest);

        verify(zoneStorage, times(2)).incrementBoundCount(zone1, "r-e-1");
        verify(zoneStorage, times(2)).incrementBoundCount(zoneWest, "r-e-2");

        verifyNoMoreInteractions(zoneStorage, monitorEventProducer, boundMonitorRepository);
    }

    @Test
    public void testDistributeNewMonitor_remote_emptyZone() {
        final ResolvedZone zone1 = createPrivateZone("t-1", "zone1");

        when(zoneStorage.findLeastLoadedEnvoy(zone1))
            .thenReturn(CompletableFuture.completedFuture(
                Optional.empty()
            ));
        when(zoneStorage.incrementBoundCount(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(1));

        Monitor monitor = new Monitor()
            .setId(UUID.randomUUID())
            .setTenantId("t-1")
            .setAgentType(AgentType.TELEGRAF)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setLabelSelector(Collections.singletonMap("os", "LINUX"))
            // NOTE only one zone used in this test
            .setZones(Collections.singletonList("zone1"))
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{}");

        final Set<String> affectedEnvoys = monitorManagement.bindNewMonitor(monitor);

        verify(zoneStorage).findLeastLoadedEnvoy(zone1);

        // Verify the envoy ID was NOT be set for this
        final List<BoundMonitor> expected = Collections.singletonList(
            new BoundMonitor()
                .setResourceId(DEFAULT_RESOURCE_ID)
                .setMonitor(monitor)
                .setRenderedContent("{}")
                .setZoneName("zone1")
        );
        verify(boundMonitorRepository).saveAll(expected);

        assertThat(affectedEnvoys, hasSize(0));

        // ...and no MonitorBoundEvent was sent
        verifyNoMoreInteractions(zoneStorage, monitorEventProducer, boundMonitorRepository);
    }

    @Test
    public void testHandleNewEnvoyInZone_privateZone() {
        // simulate that three in zone are needing envoys
        List<BoundMonitor> unassignedOnes = Arrays.asList(
            new BoundMonitor()
                .setResourceId("r-1"),
            new BoundMonitor()
                .setResourceId("r-2"),
            new BoundMonitor()
                .setResourceId("r-3")
        );

        // but only one envoy is available
        Queue<EnvoyResourcePair> availableEnvoys = new LinkedList<>();
        availableEnvoys.add(new EnvoyResourcePair().setEnvoyId("e-1").setResourceId("r-e-1"));
        // ...same envoy again to verify de-duping
        availableEnvoys.add(new EnvoyResourcePair().setEnvoyId("e-1").setResourceId("r-e-1"));

        when(zoneStorage.findLeastLoadedEnvoy(any()))
            .then(invocationOnMock -> {
                final Optional<EnvoyResourcePair> result;
                if (availableEnvoys.isEmpty()) {
                    result = Optional.empty();
                } else {
                    result = Optional.of(availableEnvoys.remove());
                }
                return CompletableFuture.completedFuture(result);
            });

        when(boundMonitorRepository.findAllWithoutEnvoyInPrivateZone(any(), any()))
            .thenReturn(unassignedOnes);

        // EXECUTE

        monitorManagement.handleNewEnvoyInZone("t-1", "z-1");

        // VERIFY

        verify(zoneStorage, times(3)).findLeastLoadedEnvoy(
            createPrivateZone("t-1", "z-1")
        );

        verify(zoneStorage, times(2)).incrementBoundCount(
            createPrivateZone("t-1", "z-1"),
            "r-e-1"
        );

        // two assignments to same envoy, but verify only one event
        verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
            .setEnvoyId("e-1"));

        verify(boundMonitorRepository).findAllWithoutEnvoyInPrivateZone("t-1", "z-1");

        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor()
                .setResourceId("r-1")
                .setEnvoyId("e-1"),
            new BoundMonitor()
                .setResourceId("r-2")
                .setEnvoyId("e-1")
        ));

        verifyNoMoreInteractions(zoneStorage, monitorEventProducer, boundMonitorRepository);
    }

    @Test
    public void testHandleNewEnvoyInZone_publicZone() {
        // simulate that three in zone are needing envoys
        List<BoundMonitor> unassignedOnes = Arrays.asList(
            new BoundMonitor()
                .setResourceId("r-1")
                .setZoneName("public/west"),
            new BoundMonitor()
                .setResourceId("r-2")
                .setZoneName("public/west"),
            new BoundMonitor()
                .setResourceId("r-3")
                .setZoneName("public/west")
        );

        // but only one envoy is available
        Queue<EnvoyResourcePair> availableEnvoys = new LinkedList<>();
        availableEnvoys.add(new EnvoyResourcePair().setEnvoyId("e-1").setResourceId("r-e-1"));
        // ...same envoy again to verify de-duping
        availableEnvoys.add(new EnvoyResourcePair().setEnvoyId("e-1").setResourceId("r-e-1"));

        when(zoneStorage.findLeastLoadedEnvoy(any()))
            .then(invocationOnMock -> {
                final Optional<EnvoyResourcePair> result;
                if (availableEnvoys.isEmpty()) {
                    result = Optional.empty();
                } else {
                    result = Optional.of(availableEnvoys.remove());
                }
                return CompletableFuture.completedFuture(result);
            });

        when(boundMonitorRepository.findAllWithoutEnvoyInPublicZone(any()))
            .thenReturn(unassignedOnes);

        // EXECUTE

        // Main difference from testHandleNewEnvoyInZone_privateZone is that the
        // tenantId is null from the event

        monitorManagement.handleNewEnvoyInZone(null, "public/west");

        // VERIFY

        verify(zoneStorage, times(3)).findLeastLoadedEnvoy(
            createPublicZone("public/west")
        );

        verify(zoneStorage, times(2)).incrementBoundCount(
            createPublicZone("public/west"),
            "r-e-1"
        );

        // two assignments to same envoy, but verify only one event
        verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
            .setEnvoyId("e-1"));

        // verify query argument normalized to non-null
        verify(boundMonitorRepository).findAllWithoutEnvoyInPublicZone("public/west");

        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor()
                .setResourceId("r-1")
                .setEnvoyId("e-1")
                .setZoneName("public/west"),
            new BoundMonitor()
                .setResourceId("r-2")
                .setEnvoyId("e-1")
                .setZoneName("public/west")
        ));

        verifyNoMoreInteractions(zoneStorage, monitorEventProducer, boundMonitorRepository);
    }

    @Test
    public void testHandleZoneResourceChanged_privateZone() {
        List<BoundMonitor> boundMonitors = Arrays.asList(
            new BoundMonitor()
                .setEnvoyId("e-1")
                .setResourceId("r-1"),
            new BoundMonitor()
                .setEnvoyId("e-1")
                .setResourceId("r-2"),
            new BoundMonitor()
                .setEnvoyId("e-1")
                .setResourceId("r-3")
        );

        when(boundMonitorRepository.findWithEnvoyInPrivateZone(any(), any(), any(), any()))
            .thenReturn(boundMonitors);

        // EXECUTE

        monitorManagement.handleEnvoyResourceChangedInZone("t-1", "z-1", "r-1", "e-1", "e-2");

        // VERIFY

        verify(boundMonitorRepository).findWithEnvoyInPrivateZone(
            "t-1", "z-1", "e-1", null);

        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor()
                .setEnvoyId("e-2")
                .setResourceId("r-1"),
            new BoundMonitor()
                .setEnvoyId("e-2")
                .setResourceId("r-2"),
            new BoundMonitor()
                .setEnvoyId("e-2")
                .setResourceId("r-3")
        ));

        verify(zoneStorage).changeBoundCount(
            createPrivateZone("t-1", "z-1"),
            "r-1",
            3
        );

        verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
            .setEnvoyId("e-2"));

        verifyNoMoreInteractions(zoneStorage, monitorEventProducer, boundMonitorRepository);
    }

    @Test
    public void testHandleZoneResourceChanged_publicZone() {
        List<BoundMonitor> boundMonitors = Arrays.asList(
            new BoundMonitor()
                .setEnvoyId("e-1")
                .setResourceId("r-1"),
            new BoundMonitor()
                .setEnvoyId("e-1")
                .setResourceId("r-2"),
            new BoundMonitor()
                .setEnvoyId("e-1")
                .setResourceId("r-3")
        );

        when(boundMonitorRepository.findWithEnvoyInPublicZone(any(), any(), any()))
            .thenReturn(boundMonitors);

        // EXECUTE

        // The main thing being tested is that a null zone tenant ID
        monitorManagement.handleEnvoyResourceChangedInZone(null, "public/1", "r-1", "e-1", "e-2");

        // VERIFY

        // ...gets normalized into an empty string for the query
        verify(boundMonitorRepository).findWithEnvoyInPublicZone(
            "public/1", "e-1", null);

        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor()
                .setEnvoyId("e-2")
                .setResourceId("r-1"),
            new BoundMonitor()
                .setEnvoyId("e-2")
                .setResourceId("r-2"),
            new BoundMonitor()
                .setEnvoyId("e-2")
                .setResourceId("r-3")
        ));

        verify(zoneStorage).changeBoundCount(
            createPublicZone("public/1"),
            "r-1",
            3
        );

        verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
            .setEnvoyId("e-2"));

        verifyNoMoreInteractions(zoneStorage, monitorEventProducer, boundMonitorRepository);
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
            .setZones(Collections.emptyList());

        List<ResourceDTO> resourceList = Collections.singletonList(new ResourceDTO()
            .setResourceId("r-1")
            .setLabels(Collections.emptyMap())
            .setAssociatedWithEnvoy(true)
        );

        when(resourceApi.getResourcesWithLabels(any(), any()))
            .thenReturn(resourceList);

        final ResourceInfo resourceInfo = new ResourceInfo()
            .setTenantId("t-1")
            .setResourceId("r-1")
            .setEnvoyId("e-1");

        when(envoyResourceManagement.getOne(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(resourceInfo));

        Set<String> result = monitorManagement.bindMonitor(monitor, monitor.getZones());

        assertThat(result, hasSize(1));
        assertThat(result.toArray()[0], equalTo("e-1"));

        verify(resourceApi).getResourcesWithLabels("t-1", monitor.getLabelSelector());
        verify(envoyResourceManagement).getOne("t-1", "r-1");
        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor()
                .setMonitor(monitor)
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
            .setZones(Collections.emptyList());

        List<ResourceDTO> resourceList = Collections.singletonList(new ResourceDTO()
            .setResourceId("r-1")
            .setLabels(Collections.emptyMap())
            // doesn't have envoy at the moment, but did before
            .setAssociatedWithEnvoy(true)
        );

        when(resourceApi.getResourcesWithLabels(any(), any()))
            .thenReturn(resourceList);

        when(envoyResourceManagement.getOne(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        Set<String> result = monitorManagement.bindMonitor(monitor, monitor.getZones());

        assertThat(result, hasSize(0));

        verify(resourceApi).getResourcesWithLabels("t-1", monitor.getLabelSelector());
        verify(envoyResourceManagement).getOne("t-1", "r-1");
        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor()
                .setMonitor(monitor)
                .setResourceId("r-1")
                .setEnvoyId(null)
                .setRenderedContent("static content")
                .setZoneName("")
        ));
        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi);
    }

    @Test
    public void testBindMonitor_AgentWithNoPriorEnvoy() {
        reset(resourceApi, envoyResourceManagement);

        final UUID m0 = UUID.fromString("00000000-0000-0000-0000-000000000000");
        final Monitor monitor = new Monitor()
            .setId(m0)
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setContent("static content")
            .setZones(Collections.emptyList());

        List<ResourceDTO> resourceList = Collections.singletonList(new ResourceDTO()
            .setResourceId("r-1")
            .setLabels(Collections.emptyMap())
            .setAssociatedWithEnvoy(false)
        );

        when(resourceApi.getResourcesWithLabels(any(), any()))
            .thenReturn(resourceList);

        Set<String> result = monitorManagement.bindMonitor(monitor, monitor.getZones());

        assertThat(result, hasSize(0));

        verify(resourceApi).getResourcesWithLabels("t-1", monitor.getLabelSelector());

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi);
    }

    @Test
    public void testUnbindByMonitorId_remote() {
        final Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
        monitor.setId(null);
        monitor.setTenantId("t-1");
        monitor.setSelectorScope(ConfigSelectorScope.REMOTE);
        entityManager.persist(monitor);
        entityManager.flush();

        final List<BoundMonitor> bound = Arrays.asList(
            new BoundMonitor()
                .setMonitor(monitor)
                .setResourceId("r-0")
                .setEnvoyId("e-1")
                .setZoneName("z-1"),
            new BoundMonitor()
                .setMonitor(monitor)
                .setResourceId("r-0")
                .setEnvoyId("e-2")
                .setZoneName("z-2")
        );
        when(boundMonitorRepository.findAllByMonitor_IdIn(any()))
            .thenReturn(bound);

        when(zoneStorage.getEnvoyIdToResourceIdMap(any()))
            .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap("e-1", "r-e-1")))
            .thenReturn(CompletableFuture.completedFuture(Collections.singletonMap("e-2", "r-e-2")));

        // EXECUTE

        final Set<String> affectedEnvoys = monitorManagement
            .unbindByMonitorId(Collections.singletonList(monitor.getId()));

        // VERIFY

        assertThat(affectedEnvoys, contains("e-1", "e-2"));

        verify(zoneStorage).decrementBoundCount(createPrivateZone("t-1", "z-1"), "r-e-1");
        verify(zoneStorage).decrementBoundCount(createPrivateZone("t-1", "z-2"), "r-e-2");

        verify(zoneStorage).getEnvoyIdToResourceIdMap(createPrivateZone("t-1", "z-1"));
        verify(zoneStorage).getEnvoyIdToResourceIdMap(createPrivateZone("t-1", "z-2"));

        verify(boundMonitorRepository).findAllByMonitor_IdIn(Collections.singletonList(monitor.getId()));

        verify(boundMonitorRepository).deleteAll(bound);

        verifyNoMoreInteractions(boundMonitorRepository);
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
            .setContent("new local domain=${resource.labels.env}"),
            // new remote monitor
            // --> 2 x BoundMonitor
            new Monitor()
            .setId(m1)
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setContent("new remote domain=${resource.labels.env}")
            .setZones(Arrays.asList("z-1", "z-2")),
            // existing monitor needing re-render
            // --> 1 x BoundMonitor
            new Monitor()
            .setId(m2)
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setContent("existing local domain=${resource.labels.env}"),
            // existing monitor no re-render
            new Monitor()
            .setId(m3)
            .setTenantId("t-1")
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setContent("static content")
            .setZones(Collections.singletonList("z-1"))
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

        when(zoneStorage.findLeastLoadedEnvoy(any()))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(
                new EnvoyResourcePair().setEnvoyId("e-2").setResourceId("r-1"))));

        when(zoneStorage.incrementBoundCount(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(1));

        when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(m0, "r-1"))
            .thenReturn(Collections.emptyList());
        when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(m1, "r-1"))
            .thenReturn(Collections.emptyList());
        when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(m2, "r-1"))
            .thenReturn(Collections.singletonList(
                new BoundMonitor()
                    .setResourceId("r-1")
                    .setMonitor(monitors.get(2))
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
        verify(zoneStorage).findLeastLoadedEnvoy(z1);
        verify(zoneStorage).findLeastLoadedEnvoy(z2);
        verify(zoneStorage).incrementBoundCount(z1, "r-1");
        verify(zoneStorage).incrementBoundCount(z2, "r-1");

        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m0, "r-1");
        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m1, "r-1");
        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m2, "r-1");
        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m3, "r-1");
        verify(boundMonitorRepository).saveAll(
            Arrays.asList(
                new BoundMonitor()
                    .setMonitor(monitors.get(0))
                    .setResourceId("r-1")
                    .setEnvoyId("e-1")
                    .setRenderedContent("new local domain=prod")
                    .setZoneName(""),
                new BoundMonitor()
                    .setMonitor(monitors.get(1))
                    .setResourceId("r-1")
                    .setEnvoyId("e-2")
                    .setRenderedContent("new remote domain=prod")
                    .setZoneName("z-1"),
                new BoundMonitor()
                    .setMonitor(monitors.get(1))
                    .setResourceId("r-1")
                    .setEnvoyId("e-2")
                    .setRenderedContent("new remote domain=prod")
                    .setZoneName("z-2"),
                new BoundMonitor()
                    .setMonitor(monitors.get(2))
                    .setResourceId("r-1")
                    .setEnvoyId("e-3")
                    .setRenderedContent("existing local domain=prod")
                    .setZoneName("")
                // NOTE binding of m3 did not need to be re-bound since its "static content" was
                // unaffected by the change in resource labels.
            )
        );

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage);
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
                .setContent("new local domain=${resource.labels.env}"),
            new Monitor()
                .setId(m1)
                .setTenantId("t-1")
                .setSelectorScope(ConfigSelectorScope.REMOTE)
                .setContent("new remote domain=${resource.labels.env}")
                .setZones(Collections.singletonList("z-1"))
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

        // ...and therefore none registered in the zone
        when(zoneStorage.findLeastLoadedEnvoy(any()))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        // EXERCISE

        final Set<String> affectedEnvoys =
            monitorManagement.upsertBindingToResource(monitors, resource, null);

        // VERIFY

        assertThat(affectedEnvoys, hasSize(0));

        verify(envoyResourceManagement).getOne("t-1", "r-1");

        final ResolvedZone z1 = createPrivateZone("t-1", "z-1");
        verify(zoneStorage).findLeastLoadedEnvoy(z1);

        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m0, "r-1");
        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m1, "r-1");
        verify(boundMonitorRepository).saveAll(
            Arrays.asList(
                new BoundMonitor()
                    .setMonitor(monitors.get(0))
                    .setResourceId("r-1")
                    .setEnvoyId(null)
                    .setRenderedContent("new local domain=prod")
                    .setZoneName(""),
                new BoundMonitor()
                    .setMonitor(monitors.get(1))
                    .setResourceId("r-1")
                    .setEnvoyId(null)
                    .setRenderedContent("new remote domain=prod")
                    .setZoneName("z-1")
            )
        );

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage
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

        verify(resourceApi).getByResourceId("t-1", "r-1");

        verify(envoyResourceManagement).getOne("t-1", "r-1");

        verify(boundMonitorRepository).findMonitorsBoundToResource("t-1", "r-1");

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

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage, monitorEventProducer, resourceApi);
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
            final ResourceDTO resource = new ResourceDTO()
                .setLabels(resourceLabels)
                .setResourceId(resourceId)
                .setTenantId(tenantId)
                .setAssociatedWithEnvoy(true);
            when(resourceApi.getByResourceId(any(), any()))
                .thenReturn(resource);

            ResourceInfo resourceInfo = new ResourceInfo()
                .setResourceId(resourceId)
                .setEnvoyId("e-1");
            when(envoyResourceManagement.getOne(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(resourceInfo));
        } else {
            when(resourceApi.getByResourceId(any(), any()))
                .thenReturn(null);
        }

        final Monitor monitor = new Monitor()
            .setSelectorScope(monitorScope)
            .setTenantId(tenantId)
            .setLabelSelector(labelSelector)
            .setAgentType(AgentType.TELEGRAF)
            .setContent(monitorContent);
        entityManager.persist(monitor);
        entityManager.flush();

        if (boundContent != null) {
            final BoundMonitor boundMonitor = new BoundMonitor()
                .setMonitor(monitor)
                .setResourceId(resourceId)
                .setZoneName("")
                .setRenderedContent(boundContent)
                .setEnvoyId("e-1");

            when(boundMonitorRepository.findMonitorsBoundToResource(any(), any()))
                .thenReturn(Collections.singletonList(monitor.getId()));

            when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(any(), any()))
                .thenReturn(Collections.singletonList(boundMonitor));
        } else {
            when(boundMonitorRepository.findMonitorsBoundToResource(any(), any()))
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

        verify(resourceApi).getByResourceId("t-1", "r-1");

        verify(envoyResourceManagement).getOne("t-1", "r-1");

        verify(boundMonitorRepository).findMonitorsBoundToResource("t-1", "r-1");

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

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage, monitorEventProducer, resourceApi);
    }

    @Test
    public void testhandleResourceEvent_modifiedResource_reattachedEnvoy_sameContent() {
        final Monitor monitor = new Monitor()
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setTenantId("t-1")
            .setLabelSelector(Collections.singletonMap("env", "prod"))
            .setAgentType(AgentType.TELEGRAF)
            .setContent("static content");
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

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage, monitorEventProducer, resourceApi);
    }

    @Test
    public void testhandleResourceEvent_modifiedResource_reattachedEnvoy_changedContent() {

        // for this unit test the "new" value of the resource don't really matter as long as
        // the monitor label selector continues to align
        final ResourceDTO resource = new ResourceDTO()
            .setLabels(Collections.singletonMap("env", "prod"))
            .setMetadata(Collections.singletonMap("custom", "new"))
            .setResourceId("r-1")
            .setTenantId("t-1");
        when(resourceApi.getByResourceId(any(), any()))
            .thenReturn(resource);

        ResourceInfo resourceInfo = new ResourceInfo()
            .setResourceId("r-1")
            .setEnvoyId("e-not-used"); // for this particular use case
        when(envoyResourceManagement.getOne(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(resourceInfo));

        final Monitor monitor = new Monitor()
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setTenantId("t-1")
            .setLabelSelector(Collections.singletonMap("env", "prod"))
            .setAgentType(AgentType.TELEGRAF)
            .setContent("custom=${resource.metadata.custom}");
        entityManager.persist(monitor);

        entityManager.flush();

        final BoundMonitor boundMonitor = new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setZoneName("")
            .setRenderedContent("custom=old")
            .setEnvoyId("e-old");

        when(boundMonitorRepository.findMonitorsBoundToResource("t-1", "r-1"))
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

        verify(resourceApi).getByResourceId("t-1", "r-1");

        verify(envoyResourceManagement).getOne("t-1", "r-1");

        verify(boundMonitorRepository).findMonitorsBoundToResource("t-1", "r-1");

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

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage, monitorEventProducer, resourceApi);
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
        verify(resourceApi).getByResourceId("t-1", "r-1");
        verify(envoyResourceManagement).getOne("t-1", "r-1");
        verify(boundMonitorRepository).findMonitorsBoundToResource("t-1", "r-1");
        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(monitor.getId(), "r-1");

        verify(boundMonitorRepository).deleteAll(captorOfBoundMonitorList.capture());
        final List<BoundMonitor> deletedBoundMonitors = captorOfBoundMonitorList.getValue();
        assertThat(deletedBoundMonitors, hasSize(1));
        assertThat(deletedBoundMonitors.get(0).getMonitor().getId(), equalTo(monitor.getId()));

        verify(monitorEventProducer).sendMonitorEvent(
            new MonitorBoundEvent()
                .setEnvoyId("e-1")
        );

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage, monitorEventProducer, resourceApi);
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
        verify(resourceApi).getByResourceId("t-1", "r-1");
        verify(envoyResourceManagement).getOne("t-1", "r-1");
        verify(boundMonitorRepository).findMonitorsBoundToResource("t-1", "r-1");
        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(monitor.getId(), "r-1");

        // nothing new bound and no affected envoy events

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage, monitorEventProducer, resourceApi);
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
        verify(resourceApi).getByResourceId("t-1", "r-1");
        verify(envoyResourceManagement).getOne("t-1", "r-1");
        verify(boundMonitorRepository).findMonitorsBoundToResource("t-1", "r-1");
        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(monitor.getId(), "r-1");

        // nothing new bound and no affected envoy events

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage, monitorEventProducer, resourceApi);
    }

    @Test
    public void testhandleResourceEvent_removedResource() {
        when(resourceApi.getByResourceId(any(), any()))
            .thenReturn(null);

        final Monitor monitor = new Monitor()
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setTenantId("t-1")
            .setLabelSelector(Collections.singletonMap("env", "prod"))
            .setAgentType(AgentType.TELEGRAF)
            .setContent("domain=${resource.labels.env}");
        entityManager.persist(monitor);
        entityManager.flush();

        final BoundMonitor boundMonitor = new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-1")
            .setZoneName("")
            .setRenderedContent("content is ignored")
            .setEnvoyId("e-1");

        when(boundMonitorRepository.findMonitorsBoundToResource(any(), any()))
            .thenReturn(Collections.singletonList(monitor.getId()));

        when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(any(), any()))
            .thenReturn(Collections.singletonList(boundMonitor));

        when(boundMonitorRepository.findAllByMonitor_IdIn(any()))
            .thenReturn(Collections.singletonList(boundMonitor));

        // EXERCISE

        monitorManagement.handleResourceChangeEvent(new ResourceEvent()
            .setTenantId("t-1")
            .setResourceId("r-1"));

        // VERIFY

        verify(resourceApi).getByResourceId("t-1", "r-1");

        verify(monitorEventProducer).sendMonitorEvent(
            new MonitorBoundEvent()
                .setEnvoyId("e-1")
        );

        verify(boundMonitorRepository).findMonitorsBoundToResource("t-1", "r-1");

        verify(boundMonitorRepository).findAllByMonitor_IdIn(
            new HashSet<>(Collections.singletonList(monitor.getId()))
        );

        verify(boundMonitorRepository).deleteAll(
            Collections.singletonList(boundMonitor)
        );

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage, monitorEventProducer, resourceApi);
    }

    @Test
    public void testGetZoneAssignmentCounts() {
        Map<EnvoyResourcePair, Integer> rawCounts = new HashMap<>();
        rawCounts.put(new EnvoyResourcePair().setResourceId("r-1").setEnvoyId("e-1"), 5);
        rawCounts.put(new EnvoyResourcePair().setResourceId("r-2").setEnvoyId("e-2"), 6);

        when(zoneStorage.getZoneBindingCounts(any()))
            .thenReturn(CompletableFuture.completedFuture(rawCounts));

        // EXECUTE

        final List<ZoneAssignmentCount> counts =
            monitorManagement.getZoneAssignmentCounts("t-1", "z-1").join();

        // VERIFY

        assertThat(counts, hasSize(2));
        assertThat(counts, containsInAnyOrder(
            new ZoneAssignmentCount().setResourceId("r-1").setEnvoyId("e-1").setAssignments(5),
            new ZoneAssignmentCount().setResourceId("r-2").setEnvoyId("e-2").setAssignments(6)

        ));

        verify(zoneStorage).getZoneBindingCounts(
            ResolvedZone.createPrivateZone("t-1", "z-1")
        );

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage, monitorEventProducer, resourceApi
        );
    }

    @Test
    public void testRebalanceZone_privateZone() {
        monitorManagement.getZonesProperties().setRebalanceStandardDeviations(1);
        monitorManagement.getZonesProperties().setRebalanceEvaluateZeroes(false);

        Map<EnvoyResourcePair, Integer> counts = new HashMap<>();
        // mean=3.25, stddev=1.639
        counts.put(new EnvoyResourcePair().setResourceId("r-least").setEnvoyId("e-least"), 0);
        counts.put(new EnvoyResourcePair().setResourceId("r-1").setEnvoyId("e-1"), 2);
        counts.put(new EnvoyResourcePair().setResourceId("r-2").setEnvoyId("e-2"), 3);
        counts.put(new EnvoyResourcePair().setResourceId("r-3").setEnvoyId("e-3"), 6);
        counts.put(new EnvoyResourcePair().setResourceId("r-2").setEnvoyId("e-4"), 2);

        when(zoneStorage.getZoneBindingCounts(any()))
            .thenReturn(CompletableFuture.completedFuture(counts));

        when(zoneStorage.findLeastLoadedEnvoy(any()))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(
                new EnvoyResourcePair().setResourceId("r-least").setEnvoyId("e-least")
            )));

        // only need to return the two within the limit that will get used
        final List<BoundMonitor> e3bound = Arrays.asList(
            new BoundMonitor().setEnvoyId("e-3").setMonitor(persistNewMonitor("t-1")),
            new BoundMonitor().setEnvoyId("e-3").setMonitor(persistNewMonitor("t-1"))
        );
        when(boundMonitorRepository.findWithEnvoyInPrivateZone(any(), any(), any(), eq(PageRequest.of(0,2))))
            .thenReturn(e3bound);

        when(boundMonitorRepository.findAllWithoutEnvoyInPrivateZone(any(), any()))
            .thenReturn(Arrays.asList(
                new BoundMonitor().setMonitor(e3bound.get(0).getMonitor()),
                new BoundMonitor().setMonitor(e3bound.get(1).getMonitor())
            ));

        // EXECUTE

        monitorManagement.rebalanceZone("t-1", "z-1").join();

        // VERIFY

        final ResolvedZone zone = createPrivateZone("t-1", "z-1");
        verify(zoneStorage).getZoneBindingCounts(zone);

        verify(boundMonitorRepository).findWithEnvoyInPrivateZone(
            "t-1", "z-1", "e-3", PageRequest.of(0, 2));

        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor().setMonitor(e3bound.get(0).getMonitor()),
            new BoundMonitor().setMonitor(e3bound.get(1).getMonitor())
        ));

        verify(boundMonitorRepository).findAllWithoutEnvoyInPrivateZone("t-1", "z-1");

        verify(monitorEventProducer).sendMonitorEvent(
            new MonitorBoundEvent().setEnvoyId("e-3")
        );
        verify(monitorEventProducer).sendMonitorEvent(
            new MonitorBoundEvent().setEnvoyId("e-least")
        );

        verify(zoneStorage, times(2)).findLeastLoadedEnvoy(zone);

        verify(zoneStorage).changeBoundCount(zone, "r-3", -2);
        verify(zoneStorage, times(2)).incrementBoundCount(zone, "r-least");

        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor().setEnvoyId("e-least").setMonitor(e3bound.get(0).getMonitor()),
            new BoundMonitor().setEnvoyId("e-least").setMonitor(e3bound.get(1).getMonitor())
        ));

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage, monitorEventProducer, resourceApi
        );
    }

    @Test
    public void testRebalanceZone_publicZone() {
        monitorManagement.getZonesProperties().setRebalanceStandardDeviations(1);
        monitorManagement.getZonesProperties().setRebalanceEvaluateZeroes(false);

        Map<EnvoyResourcePair, Integer> counts = new HashMap<>();
        // mean=3.25, stddev=1.639
        counts.put(new EnvoyResourcePair().setResourceId("r-least").setEnvoyId("e-least"), 0);
        counts.put(new EnvoyResourcePair().setResourceId("r-1").setEnvoyId("e-1"), 2);
        counts.put(new EnvoyResourcePair().setResourceId("r-2").setEnvoyId("e-2"), 3);
        counts.put(new EnvoyResourcePair().setResourceId("r-3").setEnvoyId("e-3"), 6);
        counts.put(new EnvoyResourcePair().setResourceId("r-2").setEnvoyId("e-4"), 2);

        when(zoneStorage.getZoneBindingCounts(any()))
            .thenReturn(CompletableFuture.completedFuture(counts));

        when(zoneStorage.findLeastLoadedEnvoy(any()))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(
                new EnvoyResourcePair().setResourceId("r-least").setEnvoyId("e-least")
            )));

        // only need to return the two within the limit that will get used
        final List<BoundMonitor> e3bound = Arrays.asList(
            new BoundMonitor().setEnvoyId("e-3").setMonitor(persistNewMonitor("t-1")),
            new BoundMonitor().setEnvoyId("e-3").setMonitor(persistNewMonitor("t-1"))
        );
        when(boundMonitorRepository.findWithEnvoyInPublicZone(any(), any(), eq(PageRequest.of(0,2))))
            .thenReturn(e3bound);

        when(boundMonitorRepository.findAllWithoutEnvoyInPublicZone(any()))
            .thenReturn(Arrays.asList(
                new BoundMonitor().setMonitor(e3bound.get(0).getMonitor()),
                new BoundMonitor().setMonitor(e3bound.get(1).getMonitor())
            ));

        // EXECUTE

        final Integer reassigned =
            monitorManagement.rebalanceZone(null, "public/west").join();

        // VERIFY

        assertThat(reassigned, equalTo(2));

        final ResolvedZone zone = createPublicZone("public/west");
        verify(zoneStorage).getZoneBindingCounts(zone);

        verify(boundMonitorRepository).findWithEnvoyInPublicZone(
            "public/west", "e-3", PageRequest.of(0, 2));

        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor().setMonitor(e3bound.get(0).getMonitor()),
            new BoundMonitor().setMonitor(e3bound.get(1).getMonitor())
        ));

        verify(boundMonitorRepository).findAllWithoutEnvoyInPublicZone("public/west");

        verify(monitorEventProducer).sendMonitorEvent(
            new MonitorBoundEvent().setEnvoyId("e-3")
        );
        verify(monitorEventProducer).sendMonitorEvent(
            new MonitorBoundEvent().setEnvoyId("e-least")
        );

        verify(zoneStorage, times(2)).findLeastLoadedEnvoy(zone);

        verify(zoneStorage).changeBoundCount(zone, "r-3", -2);
        verify(zoneStorage, times(2)).incrementBoundCount(zone, "r-least");

        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor().setEnvoyId("e-least").setMonitor(e3bound.get(0).getMonitor()),
            new BoundMonitor().setEnvoyId("e-least").setMonitor(e3bound.get(1).getMonitor())
        ));

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage, monitorEventProducer, resourceApi
        );
    }

    @Test
    public void testRebalanceZone_includeZeroes() {
        monitorManagement.getZonesProperties().setRebalanceStandardDeviations(1);
        // INCLUDE zero-count assignments
        monitorManagement.getZonesProperties().setRebalanceEvaluateZeroes(true);

        Map<EnvoyResourcePair, Integer> counts = new HashMap<>();
        // mean=2.6 stddev=1.9595
        counts.put(new EnvoyResourcePair().setResourceId("r-least").setEnvoyId("e-least"), 0);
        counts.put(new EnvoyResourcePair().setResourceId("r-1").setEnvoyId("e-1"), 2);
        counts.put(new EnvoyResourcePair().setResourceId("r-2").setEnvoyId("e-2"), 3);
        counts.put(new EnvoyResourcePair().setResourceId("r-3").setEnvoyId("e-3"), 6);
        counts.put(new EnvoyResourcePair().setResourceId("r-2").setEnvoyId("e-4"), 2);

        when(zoneStorage.getZoneBindingCounts(any()))
            .thenReturn(CompletableFuture.completedFuture(counts));

        when(zoneStorage.findLeastLoadedEnvoy(any()))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(
                new EnvoyResourcePair().setResourceId("r-least").setEnvoyId("e-least")
            )));

        // with zeroes included the average skewed lower and it should compute three to reassign
        final List<BoundMonitor> e3bound = Arrays.asList(
            new BoundMonitor().setEnvoyId("e-3").setMonitor(persistNewMonitor("t-1")),
            new BoundMonitor().setEnvoyId("e-3").setMonitor(persistNewMonitor("t-1")),
            new BoundMonitor().setEnvoyId("e-3").setMonitor(persistNewMonitor("t-1"))
        );
        when(boundMonitorRepository.findWithEnvoyInPublicZone(any(), any(), eq(PageRequest.of(0,3))))
            .thenReturn(e3bound);

        when(boundMonitorRepository.findAllWithoutEnvoyInPublicZone(any()))
            .thenReturn(Arrays.asList(
                new BoundMonitor().setMonitor(e3bound.get(0).getMonitor()),
                new BoundMonitor().setMonitor(e3bound.get(1).getMonitor()),
                new BoundMonitor().setMonitor(e3bound.get(2).getMonitor())
            ));

        // EXECUTE

        final Integer reassigned =
            monitorManagement.rebalanceZone(null, "public/west").join();

        // VERIFY

        assertThat(reassigned, equalTo(3));

        final ResolvedZone zone = createPublicZone("public/west");
        verify(zoneStorage).getZoneBindingCounts(zone);

        verify(boundMonitorRepository).findWithEnvoyInPublicZone(
            "public/west", "e-3", PageRequest.of(0, 3));

        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor().setMonitor(e3bound.get(0).getMonitor()),
            new BoundMonitor().setMonitor(e3bound.get(1).getMonitor()),
            new BoundMonitor().setMonitor(e3bound.get(2).getMonitor())
        ));

        verify(boundMonitorRepository).findAllWithoutEnvoyInPublicZone("public/west");

        verify(monitorEventProducer).sendMonitorEvent(
            new MonitorBoundEvent().setEnvoyId("e-3")
        );
        verify(monitorEventProducer).sendMonitorEvent(
            new MonitorBoundEvent().setEnvoyId("e-least")
        );

        verify(zoneStorage, times(3)).findLeastLoadedEnvoy(zone);

        verify(zoneStorage).changeBoundCount(zone, "r-3", -3);
        verify(zoneStorage, times(3)).incrementBoundCount(zone, "r-least");

        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor().setEnvoyId("e-least").setMonitor(e3bound.get(0).getMonitor()),
            new BoundMonitor().setEnvoyId("e-least").setMonitor(e3bound.get(1).getMonitor()),
            new BoundMonitor().setEnvoyId("e-least").setMonitor(e3bound.get(2).getMonitor())
        ));

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage, monitorEventProducer, resourceApi
        );
    }

    @Test
    public void testRebalanceZone_emptyZone() {
        when(zoneStorage.getZoneBindingCounts(any()))
            .thenReturn(CompletableFuture.completedFuture(
                Collections.emptyMap()
            ));

        // EXECUTE

        final Integer reassigned =
            monitorManagement.rebalanceZone("t-1", "z-1").join();

        // VERIFY

        assertThat(reassigned, equalTo(0));

        verify(zoneStorage).getZoneBindingCounts(ResolvedZone.createPrivateZone("t-1", "z-1"));

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage, monitorEventProducer, resourceApi
        );
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

        final MultiValueMap<String, String> expected = new LinkedMultiValueMap<>();
        expected.put("key1", Arrays.asList("value-1-1", "value-1-2"));
        expected.put("key2", Arrays.asList("value-2-1", "value-2-2"));
        expected.put("key3", Arrays.asList("value-3-1", "value-3-2"));
        assertThat(results, equalTo(expected));
    }
}
