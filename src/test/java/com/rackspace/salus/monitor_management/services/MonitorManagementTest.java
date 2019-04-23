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

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import com.rackspace.salus.monitor_management.repositories.BoundMonitorRepository;
import com.rackspace.salus.monitor_management.repositories.MonitorRepository;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.MonitorEvent;
import com.rackspace.salus.telemetry.messaging.OperationType;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.Monitor;
import com.rackspace.salus.telemetry.model.Resource;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;


@RunWith(SpringRunner.class)
@DataJpaTest
@AutoConfigureWebClient
@AutoConfigureMockRestServiceServer
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

    @Autowired
    private MockRestServiceServer mockRestServer;
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

    private ResourceEvent resourceEvent;
    private MonitorEvent monitorEvent;


    private List<Monitor> monitorList;

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
        String resourceEventString = "{\"operation\":\"UPDATE\", \"resource\":{\"resourceId\":\""
            + DEFAULT_RESOURCE_ID + "\"," +
                "\"labels\":{\"os\":\"LINUX\"},\"id\":1," +
                "\"presenceMonitoringEnabled\":true," +
                "\"tenantId\":\"abcde\"}}";
        resourceEvent = objectMapper.readValue(resourceEventString, ResourceEvent.class);
        String resourceInfoString = "{\"tenantId\":\"abcde\", \"envoyId\":\"" + DEFAULT_ENVOY_ID
            + "\", \"resourceId\":\"" + DEFAULT_RESOURCE_ID + "\"," +
                "\"labels\":{\"os\":\"LINUX\"}}";
        ResourceInfo resourceInfo = objectMapper.readValue(resourceInfoString, ResourceInfo.class);
        String monitorEventString = "{\"tenantId\":\"abcde\", \"envoyId\":\"" + DEFAULT_ENVOY_ID
            + "\", \"operationType\":\"UPDATE\", " +
                "\"config\":{\"content\":\"content1\"," +
                "\"labels\":{\"os\":\"LINUX\"}}}";
        monitorEvent = objectMapper.readValue(monitorEventString, MonitorEvent.class);
        monitorEvent.setMonitorId(currentMonitor.getId().toString());
        monitorList = new ArrayList<>();
        monitorList.add(currentMonitor);
        List<Resource> resourceList = new ArrayList<>();
        resourceList.add(new Resource()
            .setResourceId(resourceEvent.getResource().getResourceId())
            .setLabels(resourceEvent.getResource().getLabels())
        );

        mockRestServer.expect(manyTimes(), requestTo(containsString("/resourceLabels")))
            .andRespond(withSuccess(
                objectMapper.writeValueAsString(resourceList), MediaType.APPLICATION_JSON
            ));

        when(envoyResourceManagement.getOne(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(resourceInfo));
    }

    private void createMonitors(int count) {
        for (int i = 0; i < count; i++) {
            String tenantId = RandomStringUtils.randomAlphanumeric(10);
            MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
            // limit to local/agent monitors only
            create.setSelectorScope(ConfigSelectorScope.ALL_OF);
            monitorManagement.createMonitor(tenantId, create);
        }
    }

    private void createMonitorsForTenant(int count, String tenantId) {
        for (int i = 0; i < count; i++) {
            MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
            create.setSelectorScope(ConfigSelectorScope.ALL_OF);
            monitorManagement.createMonitor(tenantId, create);
        }
    }

    @Test
    public void testGetMonitor() {
        Monitor r = monitorManagement.getMonitor("abcde", currentMonitor.getId());

        assertThat(r.getId(), notNullValue());
        assertThat(r.getLabelSelector(), hasEntry("os", "LINUX"));
        assertThat(r.getContent(), equalTo(currentMonitor.getContent()));
        assertThat(r.getAgentType(), equalTo(currentMonitor.getAgentType()));
    }

    @Test
    public void testCreateNewMonitor() {
        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setSelectorScope(ConfigSelectorScope.ALL_OF);
        String tenantId = RandomStringUtils.randomAlphanumeric(10);

        Monitor returned = monitorManagement.createMonitor(tenantId, create);

        assertThat(returned.getId(), notNullValue());
        assertThat(returned.getMonitorName(), equalTo(create.getMonitorName()));
        assertThat(returned.getContent(), equalTo(create.getContent()));
        assertThat(returned.getAgentType(), equalTo(create.getAgentType()));

        assertThat(returned.getLabelSelector().size(), greaterThan(0));
        assertTrue(Maps.difference(create.getLabelSelector(), returned.getLabelSelector()).areEqual());

        Monitor retrieved = monitorManagement.getMonitor(tenantId, returned.getId());

        assertThat(retrieved.getMonitorName(), equalTo(returned.getMonitorName()));
        assertTrue(Maps.difference(returned.getLabelSelector(), retrieved.getLabelSelector()).areEqual());
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
    public void testUpdateExistingMonitor() {
        Monitor monitor = monitorManagement.getAllMonitors(PageRequest.of(0, 1)).getContent().get(0);
        Map<String, String> newLabels = new HashMap<>(monitor.getLabelSelector());
        newLabels.put("newLabel", "newValue");
        MonitorCU update = new MonitorCU();

        update.setLabelSelector(newLabels).setContent("newContent");

        Monitor newMonitor;
        try {
            newMonitor = monitorManagement.updateMonitor(
                    monitor.getTenantId(),
                    monitor.getId(),
                    update);
        } catch (Exception e) {
            assertThat(e, nullValue());
            return;
        }

        assertThat(newMonitor.getLabelSelector(), equalTo(monitor.getLabelSelector()));
        assertThat(newMonitor.getId(), equalTo(monitor.getId()));
        assertThat(newMonitor.getContent(), equalTo(monitor.getContent()));
    }

    @Test
    public void testRemoveMonitor() {
        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setSelectorScope(ConfigSelectorScope.ALL_OF);
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        Monitor newMon = monitorManagement.createMonitor(tenantId, create);

        Monitor monitor = monitorManagement.getMonitor(tenantId, newMon.getId());
        assertThat(monitor, notNullValue());

        monitorManagement.removeMonitor(tenantId, newMon.getId());
        monitor = monitorManagement.getMonitor(tenantId, newMon.getId());
        assertThat(monitor, nullValue());
    }

    @Test
    public void testHandleResourceEvent() {
        // mock the getMonitorsWithLabel method until that method is written
        MonitorManagement spyMonitorManagement = Mockito.spy(monitorManagement);
        doReturn(monitorList).when(spyMonitorManagement).getMonitorsFromLabels(any(), any());

        spyMonitorManagement.handleResourceEvent(resourceEvent);
        verify(monitorEventProducer).sendMonitorEvent(monitorEvent);
    }

    @Test
    public void testPublishMonitor() {
        monitorManagement.publishMonitor(currentMonitor, OperationType.UPDATE, currentMonitor.getLabelSelector());
        verify(monitorEventProducer).sendMonitorEvent(monitorEvent);

    }

    @Test
    public void testSpecificCreate() {
        final Map<String, String> labels = new HashMap<>();
        labels.put("os", "DARWIN");

        MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
        create.setLabelSelector(labels);
        create.setSelectorScope(ConfigSelectorScope.ALL_OF);
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
        create.setSelectorScope(ConfigSelectorScope.ALL_OF);
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
        create.setSelectorScope(ConfigSelectorScope.ALL_OF);
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
        create.setSelectorScope(ConfigSelectorScope.ALL_OF);
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
        create.setSelectorScope(ConfigSelectorScope.ALL_OF);
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
        create.setSelectorScope(ConfigSelectorScope.ALL_OF);
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
        create.setSelectorScope(ConfigSelectorScope.ALL_OF);
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
        create.setSelectorScope(ConfigSelectorScope.ALL_OF);
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
        create.setSelectorScope(ConfigSelectorScope.ALL_OF);
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
        create.setSelectorScope(ConfigSelectorScope.ALL_OF);
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        monitorManagement.createMonitor(tenantId, create);
        entityManager.flush();

        List<Monitor> resources = monitorManagement.getMonitorsFromLabels(labels, tenantId);
        assertEquals(0, resources.size());
    }

    @Test
    public void testDistributeNewMonitor_agent() {
        Monitor monitor = new Monitor()
            .setId(UUID.randomUUID())
            .setTenantId("t-1")
            .setAgentType(AgentType.TELEGRAF)
            .setSelectorScope(ConfigSelectorScope.ALL_OF)
            .setLabelSelector(Collections.singletonMap("os", "LINUX"))
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{}");

        monitorManagement.distributeNewMonitor(monitor);

        verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
            .setEnvoyId(DEFAULT_ENVOY_ID));

        verify(boundMonitorRepository).saveAll(
            Collections.singletonList(
                new BoundMonitor()
                    .setResourceId(DEFAULT_RESOURCE_ID)
                    .setMonitorId(monitor.getId())
                    .setEnvoyId(DEFAULT_ENVOY_ID)
                    .setAgentType(AgentType.TELEGRAF)
                    .setRenderedContent("{}")
                    .setTargetTenant("")
            )
        );

        verifyNoMoreInteractions(monitorEventProducer, boundMonitorRepository);
    }

    @Test
    public void testDistributeNewMonitor_remote() throws JsonProcessingException {
        final ResolvedZone zone1 = new ResolvedZone()
            .setTenantId("t-1")
            .setId("zone1");
        final ResolvedZone zoneWest = new ResolvedZone()
            .setId("public/west");

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
        mockRestServer.reset();
        mockRestServer.expect(requestTo(containsString("/resourceLabels")))
            .andRespond(withSuccess(
                objectMapper.writeValueAsString(tenantResources), MediaType.APPLICATION_JSON
            ));

        Monitor monitor = new Monitor()
            .setId(UUID.randomUUID())
            .setTenantId("t-1")
            .setAgentType(AgentType.TELEGRAF)
            .setSelectorScope(ConfigSelectorScope.REMOTE)
            .setLabelSelector(Collections.singletonMap("os", "LINUX"))
            .setZones(Arrays.asList("zone1", "public/west"))
            .setAgentType(AgentType.TELEGRAF)
            .setContent("{\"type\": \"ping\", \"urls\": [\"<<resource.metadata.public_ip>>\"]}");

        monitorManagement.distributeNewMonitor(monitor);

        verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
            .setEnvoyId("zone1-e-1"));
        verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
            .setEnvoyId("zoneWest-e-2"));

        verify(boundMonitorRepository).saveAll(Arrays.asList(
            new BoundMonitor()
                .setResourceId("r-1")
                .setMonitorId(monitor.getId())
                .setEnvoyId("zone1-e-1")
                .setAgentType(AgentType.TELEGRAF)
                .setTargetTenant("t-1")
                .setRenderedContent("{\"type\": \"ping\", \"urls\": [\"151.1.1.1\"]}")
                .setZoneTenantId("t-1")
                .setZoneId("zone1"),
            new BoundMonitor()
                .setResourceId("r-1")
                .setMonitorId(monitor.getId())
                .setEnvoyId("zoneWest-e-2")
                .setAgentType(AgentType.TELEGRAF)
                .setTargetTenant("t-1")
                .setRenderedContent("{\"type\": \"ping\", \"urls\": [\"151.1.1.1\"]}")
                .setZoneId("public/west"),
            new BoundMonitor()
                .setResourceId("r-2")
                .setMonitorId(monitor.getId())
                .setEnvoyId("zone1-e-1")
                .setAgentType(AgentType.TELEGRAF)
                .setTargetTenant("t-1")
                .setRenderedContent("{\"type\": \"ping\", \"urls\": [\"151.2.2.2\"]}")
                .setZoneTenantId("t-1")
                .setZoneId("zone1"),
            new BoundMonitor()
                .setResourceId("r-2")
                .setMonitorId(monitor.getId())
                .setEnvoyId("zoneWest-e-2")
                .setAgentType(AgentType.TELEGRAF)
                .setTargetTenant("t-1")
                .setRenderedContent("{\"type\": \"ping\", \"urls\": [\"151.2.2.2\"]}")
                .setZoneId("public/west")
        ));

        verify(zoneStorage, times(2)).findLeastLoadedEnvoy(zone1);
        verify(zoneStorage, times(2)).findLeastLoadedEnvoy(zoneWest);

        verify(zoneStorage, times(2)).incrementBoundCount(zone1, "zone1-e-1");
        verify(zoneStorage, times(2)).incrementBoundCount(zoneWest, "zoneWest-e-2");

        verifyNoMoreInteractions(zoneStorage, monitorEventProducer, boundMonitorRepository);
    }

    @Test
    public void testDistributeNewMonitor_remote_emptyZone() {
        final ResolvedZone zone1 = new ResolvedZone()
            .setTenantId("t-1")
            .setId("zone1");

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

        monitorManagement.distributeNewMonitor(monitor);

        verify(zoneStorage).findLeastLoadedEnvoy(zone1);

        // Verify the envoy ID was NOT be set for this
        verify(boundMonitorRepository).saveAll(Collections.singletonList(
            new BoundMonitor()
                .setResourceId(DEFAULT_RESOURCE_ID)
                .setMonitorId(monitor.getId())
                .setAgentType(AgentType.TELEGRAF)
                .setTargetTenant("t-1")
                .setRenderedContent("{}")
                .setZoneId("zone1")
        ));

        // ...and no MonitorBoundEvent was sent
        verifyNoMoreInteractions(zoneStorage, monitorEventProducer, boundMonitorRepository);
    }

    @Test
    public void testHandleNewResourceInZone() {
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

        when(boundMonitorRepository.findOnesWithoutEnvoy(any(), any()))
            .thenReturn(unassignedOnes);

        monitorManagement.handleNewResourceInZone("t-1", "z-1");

        verify(zoneStorage, times(3)).findLeastLoadedEnvoy(
            new ResolvedZone()
                .setTenantId("t-1")
                .setId("z-1")
        );

        // two assignments to same envoy, but verify only one event
        verify(monitorEventProducer).sendMonitorEvent(new MonitorBoundEvent()
            .setEnvoyId("e-1"));

        verify(boundMonitorRepository).findOnesWithoutEnvoy("t-1", "z-1");

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
}
