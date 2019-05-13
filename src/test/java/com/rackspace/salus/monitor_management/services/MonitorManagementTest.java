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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import com.rackspace.salus.monitor_management.entities.Monitor;
import com.rackspace.salus.monitor_management.repositories.BoundMonitorRepository;
import com.rackspace.salus.monitor_management.repositories.MonitorRepository;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.Resource;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import javax.persistence.EntityManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;


@RunWith(SpringRunner.class)
@DataJpaTest(showSql = false)
@Import({ServicesProperties.class, ObjectMapper.class, MonitorManagement.class,
    MonitorContentRenderer.class,
    MonitorContentProperties.class})
public class MonitorManagementTest {

    public static final String DEFAULT_ENVOY_ID = "env1";
    public static final String DEFAULT_RESOURCE_ID = "os:LINUX";

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
    public void setUp() throws Exception {
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

        List<Resource> resourceList = new ArrayList<>();
        resourceList.add(new Resource()
            .setResourceId(resourceEvent.getResourceId())
            .setLabels(resourceInfo.getLabels())
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
            monitorManagement.createMonitor(tenantId, create);
        }
    }

    private void createMonitorsForTenant(int count, String tenantId) {
        for (int i = 0; i < count; i++) {
            MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
            create.setSelectorScope(ConfigSelectorScope.LOCAL);
            monitorManagement.createMonitor(tenantId, create);
        }
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
    public void testCreateNewMonitor() {
        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setSelectorScope(ConfigSelectorScope.LOCAL);
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
        final Resource r2 = new Resource()
            .setLabels(r2labels)
            .setResourceId("r-2")
            .setTenantId("t-1");

        Map<String, String> r3labels = new HashMap<>();
        r3labels.put("new", "yes");
        final Resource r3 = new Resource()
            .setLabels(r3labels)
            .setResourceId("r-3")
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
            .setZoneTenantId("")
            .setZoneId("")
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

        assertThat(updatedMonitor, equalTo(
            new Monitor()
                .setId(monitor.getId())
                .setAgentType(AgentType.TELEGRAF)
                .setContent("{}")
                .setTenantId("t-1")
                .setSelectorScope(ConfigSelectorScope.LOCAL)
                .setLabelSelector(newLabelSelector)
        ));

        verify(resourceApi).getResourcesWithLabels("t-1", Collections.singletonMap("new", "yes"));

        verify(boundMonitorRepository).findResourceIdsBoundToMonitor(monitor.getId());

        verify(boundMonitorRepository).saveAll(Collections.singletonList(
            new BoundMonitor()
                .setMonitor(monitor)
                .setResourceId("r-3")
                .setEnvoyId("e-3")
                .setZoneTenantId("")
                .setZoneId("")
                .setRenderedContent("{}")
        ));

        verify(boundMonitorRepository).deleteAll(Collections.singletonList(
            new BoundMonitor()
                .setMonitor(monitor)
                .setResourceId("r-1")
                .setZoneTenantId("")
                .setZoneId("")
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
        final Resource r1 = new Resource()
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
        final Resource r2 = new Resource()
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
            .setZoneTenantId("t-1")
            .setZoneId("z-1")
            .setRenderedContent("address=something_else");
        entityManager.persist(bound1);

        final BoundMonitor bound2 = new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-2")
            .setEnvoyId("e-2")
            .setZoneTenantId("t-1")
            .setZoneId("z-1")
            .setRenderedContent("address=localhost");
        entityManager.persist(bound2);

        // same resource r-2, but different zone to ensure query-by-resource is normalize to one query each
        final BoundMonitor bound3 = new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId("r-2")
            .setEnvoyId("e-3")
            .setZoneTenantId("t-1")
            .setZoneId("z-2")
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
                .setZoneTenantId("t-1")
                .setZoneId("z-1")
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
                new Resource()
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

        when(zoneStorage.findLeastLoadedEnvoy(any()))
            .thenReturn(CompletableFuture.completedFuture(Optional.of("e-new")));
        when(zoneStorage.incrementBoundCount(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(1));

        final BoundMonitor boundZ1 = new BoundMonitor()
            .setMonitor(monitor)
            .setZoneTenantId("t-1")
            .setZoneId("z-1")
            .setResourceId("r-1")
            .setRenderedContent("{}")
            .setEnvoyId("e-existing");
        entityManager.persist(boundZ1);
        when(boundMonitorRepository.findAllByMonitor_IdAndZoneIdIn(
            monitor.getId(), Collections.singletonList("z-1")
        ))
        .thenReturn(Collections.singletonList(
            boundZ1
        ));

        final BoundMonitor boundZ2 = new BoundMonitor()
            .setMonitor(monitor)
            .setZoneTenantId("t-1")
            .setZoneId("z-2")
            .setResourceId("r-1")
            .setRenderedContent("{}")
            .setEnvoyId("e-existing");
        entityManager.persist(boundZ2);

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
        verify(zoneStorage).incrementBoundCount(resolvedZ3, "e-new");
        verify(zoneStorage).decrementBoundCount(createPrivateZone("t-1", "z-1"), "e-existing");

        verify(boundMonitorRepository)
            .findAllByMonitor_IdAndZoneIdIn(monitor.getId(), Collections.singletonList("z-1"));

        verify(boundMonitorRepository).deleteAll(Collections.singletonList(boundZ1));

        verify(boundMonitorRepository).saveAll(Collections.singletonList(
            new BoundMonitor()
                .setMonitor(monitor)
                .setZoneTenantId("t-1")
                .setZoneId("z-3")
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

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi,
            zoneStorage, monitorEventProducer);
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

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement, resourceApi,
            zoneStorage, monitorEventProducer);
    }

    @Test(expected = NotFoundException.class)
    public void testUpdateNonExistentMonitor() {
        String tenant = RandomStringUtils.randomAlphanumeric(10);
        UUID uuid = UUID.randomUUID();

        Map<String, String> newLabels = Collections.singletonMap("newLabel", "newValue");
        MonitorCU update = new MonitorCU();
        update.setLabelSelector(newLabels).setContent("newContent");

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
            .setZoneTenantId("t-1")
            .setZoneId("z-1")
            .setRenderedContent("{}")
            .setEnvoyId("e-goner");

        when(boundMonitorRepository.findAllByMonitor_IdIn(any()))
            .thenReturn(Collections.singletonList(boundMonitor));

        // EXECUTE

        monitorManagement.removeMonitor("t-1", monitor.getId());

        // VERIFY

        final Optional<Monitor> retrieved = monitorManagement.getMonitor("t-1", monitor.getId());
        assertThat(retrieved.isPresent(), equalTo(false));

        verify(boundMonitorRepository).findAllByMonitor_IdIn(Collections.singletonList(monitor.getId()));

        verify(boundMonitorRepository).deleteAll(Collections.singletonList(boundMonitor));

        verify(zoneStorage).decrementBoundCount(
            ResolvedZone.createPrivateZone("t-1", "z-1"),
            "e-goner"
        );

        verify(monitorEventProducer).sendMonitorEvent(
            new MonitorBoundEvent()
            .setEnvoyId("e-goner")
        );

        verifyNoMoreInteractions(boundMonitorRepository, zoneStorage, monitorEventProducer);
    }

    @Test(expected = NotFoundException.class)
    public void testRemoveNonExistentMonitor() {
        String tenant = RandomStringUtils.randomAlphanumeric(10);
        UUID uuid = UUID.randomUUID();
        monitorManagement.removeMonitor(tenant, uuid);
    }

    @Test
    public void testSpecificCreate() {
        final Map<String, String> labels = new HashMap<>();
        labels.put("os", "DARWIN");

        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setLabelSelector(labels);
        create.setSelectorScope(ConfigSelectorScope.LOCAL);
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        monitorManagement.createMonitor(tenantId, create);
        entityManager.flush();
        List<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId);
        assertEquals(1, monitors.size());
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
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        monitorManagement.createMonitor(tenantId, create);
        entityManager.flush();
        List<Monitor> monitors = monitorManagement.getMonitorsFromLabels(queryLabels, tenantId);
        assertEquals(0, monitors.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyLabelsException() {
        final Map<String, String> labels = new HashMap<>();

        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setLabelSelector(labels);
        create.setSelectorScope(ConfigSelectorScope.LOCAL);
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        monitorManagement.createMonitor(tenantId, create);
        entityManager.flush();
        monitorManagement.getMonitorsFromLabels(labels, tenantId);
    }

    @Test
    public void testMonitorWithSameLabelsAndDifferentTenants() {
        final Map<String, String> labels = new HashMap<>();
        labels.put("key", "value");

        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setLabelSelector(labels);
        create.setSelectorScope(ConfigSelectorScope.LOCAL);
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        String tenantId2 = RandomStringUtils.randomAlphanumeric(10);
        monitorManagement.createMonitor(tenantId, create);
        monitorManagement.createMonitor(tenantId2, create);
        entityManager.flush();

        List<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId);
        assertEquals(1, monitors.size()); //make sure we only returned the one value
        assertEquals(tenantId, monitors.get(0).getTenantId());
        assertEquals(create.getAgentType(), monitors.get(0).getAgentType());
        assertEquals(create.getContent(), monitors.get(0).getContent());
        assertEquals(create.getMonitorName(), monitors.get(0).getMonitorName());
        assertEquals(create.getSelectorScope(), monitors.get(0).getSelectorScope());
        assertEquals(create.getLabelSelector(), monitors.get(0).getLabelSelector());
    }

    @Test
    public void testMatchMonitorWithMultipleLabels() {
        final Map<String, String> labels = new HashMap<>();
        labels.put("os", "DARWIN");
        labels.put("env", "test");

        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setLabelSelector(labels);
        create.setSelectorScope(ConfigSelectorScope.LOCAL);
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        monitorManagement.createMonitor(tenantId, create);
        entityManager.flush();

        List<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId);
        assertEquals(1, monitors.size()); //make sure we only returned the one value
        assertEquals(tenantId, monitors.get(0).getTenantId());
        assertEquals(create.getAgentType(), monitors.get(0).getAgentType());
        assertEquals(create.getContent(), monitors.get(0).getContent());
        assertEquals(create.getMonitorName(), monitors.get(0).getMonitorName());
        assertEquals(create.getSelectorScope(), monitors.get(0).getSelectorScope());
        assertEquals(create.getLabelSelector(), monitors.get(0).getLabelSelector());
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
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        monitorManagement.createMonitor(tenantId, create);
        entityManager.flush();

        List<Monitor> monitors = monitorManagement.getMonitorsFromLabels(queryLabels, tenantId);
        assertEquals(0, monitors.size());
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
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        monitorManagement.createMonitor(tenantId, create);
        entityManager.flush();

        List<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId);
        assertEquals(0, monitors.size()); //make sure we only returned the one value
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
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        monitorManagement.createMonitor(tenantId, create);
        entityManager.flush();

        List<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId);
        assertEquals(0, monitors.size());
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
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        monitorManagement.createMonitor(tenantId, create);
        entityManager.flush();

        List<Monitor> monitors = monitorManagement.getMonitorsFromLabels(labels, tenantId);
        assertEquals(1, monitors.size()); //make sure we only returned the one value
        assertEquals(tenantId, monitors.get(0).getTenantId());
        assertEquals(create.getAgentType(), monitors.get(0).getAgentType());
        assertEquals(create.getContent(), monitors.get(0).getContent());
        assertEquals(create.getMonitorName(), monitors.get(0).getMonitorName());
        assertEquals(create.getSelectorScope(), monitors.get(0).getSelectorScope());
        assertEquals(create.getLabelSelector(), monitors.get(0).getLabelSelector());
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

        List<Monitor> resources = monitorManagement.getMonitorsFromLabels(labels, tenantId);
        assertEquals(0, resources.size());
    }

    @Test
    public void testsendMonitorBoundEvents() {

        final List<BoundMonitor> input = Arrays.asList(
            new BoundMonitor().setEnvoyId("e-1"),
            new BoundMonitor().setEnvoyId("e-2"),
            new BoundMonitor().setEnvoyId("e-1"),
            new BoundMonitor().setEnvoyId("e-3")
        );

        monitorManagement.sendMonitorBoundEvents(input);

        ArgumentCaptor<MonitorBoundEvent> evtCaptor = ArgumentCaptor.forClass(MonitorBoundEvent.class);

        verify(monitorEventProducer, times(3)).sendMonitorEvent(evtCaptor.capture());

        assertThat(evtCaptor.getAllValues(), contains(
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

        final List<BoundMonitor> changes = monitorManagement.bindMonitor(monitor);

        final List<BoundMonitor> expected = Collections.singletonList(
            new BoundMonitor()
                .setResourceId(DEFAULT_RESOURCE_ID)
                .setMonitor(monitor)
                .setEnvoyId(DEFAULT_ENVOY_ID)
                .setRenderedContent("{}")
                .setZoneTenantId("")
                .setZoneId("")
        );
        verify(boundMonitorRepository).saveAll(
            expected
        );

        assertThat(changes, equalTo(expected));

        verifyNoMoreInteractions(monitorEventProducer, boundMonitorRepository);
    }

    @Test
    public void testDistributeNewMonitor_remote() {
        final ResolvedZone zone1 = createPrivateZone("t-1", "zone1");
        final ResolvedZone zoneWest = createPublicZone("public/west");

        when(zoneStorage.findLeastLoadedEnvoy(zone1))
            .thenReturn(CompletableFuture.completedFuture(
                Optional.of("zone1-e-1")
            ));
        when(zoneStorage.findLeastLoadedEnvoy(zoneWest))
            .thenReturn(CompletableFuture.completedFuture(
                Optional.of("zoneWest-e-2")
            ));
        when(zoneStorage.incrementBoundCount(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(1));

        final List<Resource> tenantResources = new ArrayList<>();
        tenantResources.add(
            new Resource().setResourceId("r-1")
                .setLabels(Collections.singletonMap("os", "LINUX"))
                .setMetadata(Collections.singletonMap("public_ip", "151.1.1.1"))
        );
        tenantResources.add(
            new Resource().setResourceId("r-2")
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

        final List<BoundMonitor> boundMonitors = monitorManagement.bindMonitor(monitor);

        final List<BoundMonitor> expected = Arrays.asList(
            new BoundMonitor()
                .setResourceId("r-1")
                .setMonitor(monitor)
                .setEnvoyId("zone1-e-1")
                .setRenderedContent("{\"type\": \"ping\", \"urls\": [\"151.1.1.1\"]}")
                .setZoneTenantId("t-1")
                .setZoneId("zone1"),
            new BoundMonitor()
                .setResourceId("r-1")
                .setMonitor(monitor)
                .setEnvoyId("zoneWest-e-2")
                .setRenderedContent("{\"type\": \"ping\", \"urls\": [\"151.1.1.1\"]}")
                .setZoneTenantId(ResolvedZone.PUBLIC)
                .setZoneId("public/west"),
            new BoundMonitor()
                .setResourceId("r-2")
                .setMonitor(monitor)
                .setEnvoyId("zone1-e-1")
                .setRenderedContent("{\"type\": \"ping\", \"urls\": [\"151.2.2.2\"]}")
                .setZoneTenantId("t-1")
                .setZoneId("zone1"),
            new BoundMonitor()
                .setResourceId("r-2")
                .setMonitor(monitor)
                .setEnvoyId("zoneWest-e-2")
                .setRenderedContent("{\"type\": \"ping\", \"urls\": [\"151.2.2.2\"]}")
                .setZoneTenantId(ResolvedZone.PUBLIC)
                .setZoneId("public/west")
        );
        verify(boundMonitorRepository).saveAll(expected);

        assertThat(boundMonitors, equalTo(expected));

        verify(zoneStorage, times(2)).findLeastLoadedEnvoy(zone1);
        verify(zoneStorage, times(2)).findLeastLoadedEnvoy(zoneWest);

        verify(zoneStorage, times(2)).incrementBoundCount(zone1, "zone1-e-1");
        verify(zoneStorage, times(2)).incrementBoundCount(zoneWest, "zoneWest-e-2");

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

        final List<BoundMonitor> boundMonitors = monitorManagement.bindMonitor(monitor);

        verify(zoneStorage).findLeastLoadedEnvoy(zone1);

        // Verify the envoy ID was NOT be set for this
        final List<BoundMonitor> expected = Collections.singletonList(
            new BoundMonitor()
                .setResourceId(DEFAULT_RESOURCE_ID)
                .setMonitor(monitor)
                .setRenderedContent("{}")
                .setZoneTenantId("t-1")
                .setZoneId("zone1")
        );
        verify(boundMonitorRepository).saveAll(expected);

        assertThat(boundMonitors, equalTo(expected));

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
        Queue<String> availableEnvoys = new LinkedList<>();
        availableEnvoys.add("e-1");
        // ...same envoy again to verify de-duping
        availableEnvoys.add("e-1");

        when(zoneStorage.findLeastLoadedEnvoy(any()))
            .then(invocationOnMock -> {
                final Optional<String> result;
                if (availableEnvoys.isEmpty()) {
                    result = Optional.empty();
                } else {
                    result = Optional.of(availableEnvoys.remove());
                }
                return CompletableFuture.completedFuture(result);
            });

        when(boundMonitorRepository.findAllWithoutEnvoy(any(), any()))
            .thenReturn(unassignedOnes);

        monitorManagement.handleNewEnvoyInZone("t-1", "z-1");

        verify(zoneStorage, times(3)).findLeastLoadedEnvoy(
            createPrivateZone("t-1", "z-1")
        );

        // two assignments to same envoy, but verify only one event
        verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
            .setEnvoyId("e-1"));

        verify(boundMonitorRepository).findAllWithoutEnvoy("t-1", "z-1");

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
                .setZoneTenantId("")
                .setZoneId("public/west"),
            new BoundMonitor()
                .setResourceId("r-2")
                .setZoneTenantId("")
                .setZoneId("public/west"),
            new BoundMonitor()
                .setResourceId("r-3")
                .setZoneTenantId("")
                .setZoneId("public/west")
        );

        // but only one envoy is available
        Queue<String> availableEnvoys = new LinkedList<>();
        availableEnvoys.add("e-1");
        // ...same envoy again to verify de-duping
        availableEnvoys.add("e-1");

        when(zoneStorage.findLeastLoadedEnvoy(any()))
            .then(invocationOnMock -> {
                final Optional<String> result;
                if (availableEnvoys.isEmpty()) {
                    result = Optional.empty();
                } else {
                    result = Optional.of(availableEnvoys.remove());
                }
                return CompletableFuture.completedFuture(result);
            });

        when(boundMonitorRepository.findAllWithoutEnvoy(any(), any()))
            .thenReturn(unassignedOnes);

        // EXECUTE

        // Main difference from testHandleNewEnvoyInZone_privateZone is that the
        // tenantId is null from the event

        monitorManagement.handleNewEnvoyInZone(null, "public/west");

        // VERIFY

        verify(zoneStorage, times(3)).findLeastLoadedEnvoy(
            createPublicZone("public/west")
        );

        // two assignments to same envoy, but verify only one event
        verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
            .setEnvoyId("e-1"));

        // verify query argument normalized to non-null
        verify(boundMonitorRepository).findAllWithoutEnvoy(ResolvedZone.PUBLIC, "public/west");

        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor()
                .setResourceId("r-1")
                .setEnvoyId("e-1")
                .setZoneTenantId("")
                .setZoneId("public/west"),
            new BoundMonitor()
                .setResourceId("r-2")
                .setEnvoyId("e-1")
                .setZoneTenantId("")
                .setZoneId("public/west")
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

        when(boundMonitorRepository.findAllWithEnvoy(any(), any(), any()))
            .thenReturn(boundMonitors);

        monitorManagement.handleEnvoyResourceChangedInZone("t-1", "z-1", "e-1", "e-2");

        verify(boundMonitorRepository).findAllWithEnvoy("t-1", "z-1", "e-1");

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

        verify(zoneStorage).incrementBoundCount(
            createPrivateZone("t-1", "z-1"),
            "e-2",
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

        when(boundMonitorRepository.findAllWithEnvoy(any(), any(), any()))
            .thenReturn(boundMonitors);

        // EXECUTE

        // The main thing being tested is that a null zone tenant ID
        monitorManagement.handleEnvoyResourceChangedInZone(null, "public/1", "e-1", "e-2");

        // VERIFY

        // ...gets normalized into an empty string for the query
        verify(boundMonitorRepository).findAllWithEnvoy(ResolvedZone.PUBLIC, "public/1", "e-1");

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

        verify(zoneStorage).incrementBoundCount(
            createPublicZone("public/1"),
            "e-2",
            3
        );

        verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
            .setEnvoyId("e-2"));

        verifyNoMoreInteractions(zoneStorage, monitorEventProducer, boundMonitorRepository);
    }

    @Test
    public void testUnbindByMonitorId() {
        final Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
        monitor.setId(null);
        monitor.setTenantId("t-1");
        entityManager.persist(monitor);
        entityManager.flush();

        when(boundMonitorRepository.findAllByMonitor_IdIn(any()))
            .thenReturn(Arrays.asList(
                new BoundMonitor()
                .setMonitor(monitor)
                .setResourceId("r-0")
                .setZoneId("z-1"),
                new BoundMonitor()
                .setMonitor(monitor)
                .setResourceId("r-0")
                .setZoneId("z-2")
            ));

        // EXECUTE

        final List<BoundMonitor> result = monitorManagement
            .unbindByMonitorId(Collections.singletonList(monitor.getId()));

        // VERIFY

        assertThat(result, hasSize(2));
        for (BoundMonitor boundMonitor : result) {
            assertThat(boundMonitor.getResourceId(), equalTo("r-0"));
        }

        verify(boundMonitorRepository).findAllByMonitor_IdIn(Collections.singletonList(monitor.getId()));

        verify(boundMonitorRepository).deleteAll(result);

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

        final Resource resource = new Resource()
            .setTenantId("t-1")
            .setResourceId("r-1")
            .setLabels(Collections.singletonMap("env", "prod"));

        final ResourceInfo resourceInfo = new ResourceInfo()
            .setTenantId("t-1")
            .setResourceId("r-1")
            .setEnvoyId("e-1");

        when(envoyResourceManagement.getOne(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(resourceInfo));

        when(zoneStorage.findLeastLoadedEnvoy(any()))
            .thenReturn(CompletableFuture.completedFuture(Optional.of("e-2")));

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
                    .setZoneTenantId("")
                    .setZoneId("")
            ));
        when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(m3, "r-1"))
            .thenReturn(Collections.singletonList(
                new BoundMonitor()
                    .setResourceId("r-1")
                    .setMonitor(monitors.get(3))
                    .setRenderedContent("static content")
                    .setZoneTenantId("t-1")
                    .setZoneId("z-1")
                    .setEnvoyId("e-4")
            ));

        // EXERCISE

        final List<BoundMonitor> results =
            monitorManagement.upsertBindingToResource(monitors, resource);

        // VERIFY

        assertThat(results, hasSize(4));
        assertThat(results, containsInAnyOrder(
            new BoundMonitor()
                .setMonitor(monitors.get(0))
                .setResourceId("r-1")
                .setEnvoyId("e-1")
                .setRenderedContent("new local domain=prod")
                .setZoneTenantId("")
                .setZoneId(""),
            new BoundMonitor()
                .setMonitor(monitors.get(1))
                .setResourceId("r-1")
                .setEnvoyId("e-2")
                .setRenderedContent("new remote domain=prod")
                .setZoneTenantId("t-1")
                .setZoneId("z-1"),
            new BoundMonitor()
                .setMonitor(monitors.get(1))
                .setResourceId("r-1")
                .setEnvoyId("e-2")
                .setRenderedContent("new remote domain=prod")
                .setZoneTenantId("t-1")
                .setZoneId("z-2"),
            new BoundMonitor()
                .setMonitor(monitors.get(2))
                .setResourceId("r-1")
                .setEnvoyId("e-3")
                .setRenderedContent("existing local domain=prod")
                .setZoneTenantId("")
                .setZoneId("")
            // NOTE binding of m3 did not need to be re-bound since its "static content" was
            // unaffected by the change in resource labels.
        ));

        verify(envoyResourceManagement).getOne("t-1", "r-1");

        final ResolvedZone z1 = createPrivateZone("t-1", "z-1");
        final ResolvedZone z2 = createPrivateZone("t-1", "z-2");
        verify(zoneStorage).findLeastLoadedEnvoy(z1);
        verify(zoneStorage).findLeastLoadedEnvoy(z2);
        verify(zoneStorage).incrementBoundCount(z1, "e-2");
        verify(zoneStorage).incrementBoundCount(z2, "e-2");

        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m0, "r-1");
        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m1, "r-1");
        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m2, "r-1");
        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m3, "r-1");
        verify(boundMonitorRepository).saveAll(results);

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

        final Resource resource = new Resource()
            .setTenantId("t-1")
            .setResourceId("r-1")
            .setLabels(Collections.singletonMap("env", "prod"));

        // simulate no envoys attached
        when(envoyResourceManagement.getOne(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // ...and therefore none registered in the zone
        when(zoneStorage.findLeastLoadedEnvoy(any()))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        // EXERCISE

        final List<BoundMonitor> results =
            monitorManagement.upsertBindingToResource(monitors, resource);

        // VERIFY

        assertThat(results, hasSize(2));
        assertThat(results, containsInAnyOrder(
            new BoundMonitor()
                .setMonitor(monitors.get(0))
                .setResourceId("r-1")
                .setEnvoyId(null)
                .setRenderedContent("new local domain=prod")
                .setZoneTenantId("")
                .setZoneId(""),
            new BoundMonitor()
                .setMonitor(monitors.get(1))
                .setResourceId("r-1")
                .setEnvoyId(null)
                .setRenderedContent("new remote domain=prod")
                .setZoneTenantId("t-1")
                .setZoneId("z-1")
        ));

        verify(envoyResourceManagement).getOne("t-1", "r-1");

        final ResolvedZone z1 = createPrivateZone("t-1", "z-1");
        verify(zoneStorage).findLeastLoadedEnvoy(z1);

        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m0, "r-1");
        verify(boundMonitorRepository).findAllByMonitor_IdAndResourceId(m1, "r-1");
        verify(boundMonitorRepository).saveAll(results);

        verifyNoMoreInteractions(boundMonitorRepository, envoyResourceManagement,
            zoneStorage
        );
    }

    @Test
    public void testhandleResourceEvent_newResource() {
        final Resource resource = new Resource()
            .setLabels(Collections.singletonMap("env", "prod"))
            .setResourceId("r-1")
            .setTenantId("t-1")
            .setId(1001L);
        when(resourceApi.getByResourceId(any(), any()))
            .thenReturn(resource);

        ResourceInfo resourceInfo = new ResourceInfo()
            .setResourceId("r-1")
            .setEnvoyId("e-1");
        when(envoyResourceManagement.getOne(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(resourceInfo));

        final Monitor monitor = new Monitor()
            .setSelectorScope(ConfigSelectorScope.LOCAL)
            .setTenantId("t-1")
            .setLabelSelector(Collections.singletonMap("env", "prod"))
            .setAgentType(AgentType.TELEGRAF)
            .setContent("domain=${resource.labels.env}");
        entityManager.persist(monitor);
        entityManager.flush();
        // ...but no BoundMonitor

        when(boundMonitorRepository.findMonitorsBoundToResource(any(), any()))
            .thenReturn(Collections.emptyList());

        // EXERCISE

        monitorManagement.handleResourceEvent(new ResourceEvent()
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
            .setZoneTenantId("")
            .setZoneId("")
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
    public void testhandleResourceEvent_modifiedResource() {

        final Resource resource = new Resource()
            .setLabels(Collections.singletonMap("env", "prod"))
            .setResourceId("r-1")
            .setTenantId("t-1")
            .setId(1001L);
        when(resourceApi.getByResourceId(any(), any()))
            .thenReturn(resource);

        ResourceInfo resourceInfo = new ResourceInfo()
            .setResourceId("r-1")
            .setEnvoyId("e-1");
        when(envoyResourceManagement.getOne(any(), any()))
            .thenReturn(CompletableFuture.completedFuture(resourceInfo));

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
            .setZoneId("")
            .setZoneTenantId("")
            .setRenderedContent("domain=some old value")
            .setEnvoyId("e-1");

        when(boundMonitorRepository.findMonitorsBoundToResource("t-1", "r-1"))
            .thenReturn(Collections.singletonList(monitor.getId()));

        when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(any(), any()))
            .thenReturn(Collections.singletonList(boundMonitor));

        // EXERCISE

        monitorManagement.handleResourceEvent(new ResourceEvent()
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
                .setZoneTenantId("")
                .setZoneId("")
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
            .setZoneId("")
            .setZoneTenantId("")
            .setRenderedContent("content is ignored")
            .setEnvoyId("e-1");

        when(boundMonitorRepository.findMonitorsBoundToResource(any(), any()))
            .thenReturn(Collections.singletonList(monitor.getId()));

        when(boundMonitorRepository.findAllByMonitor_IdAndResourceId(any(), any()))
            .thenReturn(Collections.singletonList(boundMonitor));

        when(boundMonitorRepository.findAllByMonitor_IdIn(any()))
            .thenReturn(Collections.singletonList(boundMonitor));

        // EXERCISE

        monitorManagement.handleResourceEvent(new ResourceEvent()
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
}
