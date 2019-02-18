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

package com.rackspace.salus.monitor_management;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.google.common.collect.Maps;
import com.rackspace.salus.monitor_management.services.MonitorManagement;
import com.rackspace.salus.monitor_management.web.model.MonitorCreate;
import com.rackspace.salus.monitor_management.web.model.MonitorUpdate;
import com.rackspace.salus.telemetry.messaging.AttachEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.Monitor;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit4.SpringRunner;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

@RunWith(SpringRunner.class)
@DataJpaTest
@Import({MonitorManagement.class})
public class MonitorManagementTest {

    @Autowired
    MonitorManagement monitorManagement;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    MonitorRepository monitorRepository;

    PodamFactory podamFactory = new PodamFactoryImpl();

    Monitor currentMonitor;

    @Before
    public void setUp() {
        Monitor monitor = new Monitor()
                .setTenantId("abcde")
                .setMonitorId("mon1")
                .setLabels(Collections.singletonMap("key", "value"))
                .setContent("content1")
                .setAgentType(AgentType.FILEBEAT);
        monitorRepository.save(monitor);
        currentMonitor = monitor;
    }

    private void createMonitors(int count) {
        for (int i=0; i<count; i++) {
            String tenantId = RandomStringUtils.randomAlphanumeric(10);
            MonitorCreate create = podamFactory.manufacturePojo(MonitorCreate.class);
            create.setAgentType("REMOTE");
            monitorManagement.createMonitor(tenantId, create);
        }
    }

    private void createMonitorsForTenant(int count, String tenantId) {
        for (int i=0; i<count; i++) {
            MonitorCreate create = podamFactory.manufacturePojo(MonitorCreate.class);
            create.setAgentType("REMOTE");
            monitorManagement.createMonitor(tenantId, create);
        }
    }

    @Test
    public void testGetMonitor() {
        Monitor r = monitorManagement.getMonitor("abcde", "mon1");

        assertThat(r.getId(), notNullValue());
        assertThat(r.getLabels(), hasEntry("key", "value"));
        assertThat(r.getContent(), equalTo(currentMonitor.getContent()));
        assertThat(r.getAgentType(), equalTo(currentMonitor.getAgentType()));
    }

    @Test
    public void testCreateNewMonitor() {
        MonitorCreate create = podamFactory.manufacturePojo(MonitorCreate.class);
        create.setAgentType("REMOTE");
        String tenantId = RandomStringUtils.randomAlphanumeric(10);

        Monitor returned = monitorManagement.createMonitor(tenantId, create);

        assertThat(returned.getId(), notNullValue());
        assertThat(returned.getMonitorId(), equalTo(create.getMonitorId()));
        assertThat(returned.getContent(), equalTo(create.getContent()));
        assertThat(returned.getAgentType(), equalTo(AgentType.valueOf(create.getAgentType())));
        
        assertThat(returned.getLabels().size(), greaterThan(0));
        assertTrue(Maps.difference(create.getLabels(), returned.getLabels()).areEqual());

        Monitor retrieved = monitorManagement.getMonitor(tenantId, create.getMonitorId());

        assertThat(retrieved.getMonitorId(), equalTo(returned.getMonitorId()));
        assertTrue(Maps.difference(returned.getLabels(), retrieved.getLabels()).areEqual());
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
    public void testGetAllForTenant() {
        Random random = new Random();
        int totalMonitors = random.nextInt(150 - 50) + 50;
        int pageSize = 10;
        String tenantId = RandomStringUtils.randomAlphanumeric(10);

        Pageable page = PageRequest.of(0, pageSize);
        Page<Monitor> result = monitorManagement.getAllMonitors(page);

        assertThat(result.getTotalElements(), equalTo(1L));

        createMonitorsForTenant(totalMonitors , tenantId);

        page = PageRequest.of(0, 10);
        result = monitorManagement.getMonitors(tenantId, page);

        assertThat(result.getTotalElements(), equalTo((long) totalMonitors));
        assertThat(result.getTotalPages(), equalTo((totalMonitors + pageSize - 1) / pageSize));
    }

    // @Test
    // public void testGetMonitorsWithPresenceMonitoringAsStream() {
    //     int totalMonitors = 100;
    //     createMonitors(totalMonitors);
    //     Stream s = monitorManagement.getMonitors(true);
    //     // The one default monitor doesn't have presence monitoring enabled so can be ignored.
    //     assertThat(s.count(), equalTo((long) totalMonitors));
    // }

    @Test
    public void testUpdateExistingMonitor() {
        Monitor monitor = monitorManagement.getAllMonitors(PageRequest.of(0, 1)).getContent().get(0);
        Map<String, String> newLabels = new HashMap<>(monitor.getLabels());
        newLabels.put("newLabel", "newValue");
        MonitorUpdate update = new MonitorUpdate();

        update.setLabels(newLabels).setContent("newContent");

        Monitor newMonitor;
        try {
            newMonitor = monitorManagement.updateMonitor(
                    monitor.getTenantId(),
                    monitor.getMonitorId(),
                    update);
        } catch (Exception e) {
            assertThat(e, nullValue());
            return;
        }

        assertThat(newMonitor.getLabels(), equalTo(monitor.getLabels()));
        assertThat(newMonitor.getId(), equalTo(monitor.getId()));
        assertThat(newMonitor.getContent(), equalTo(monitor.getContent()));
    }

    @Test
    public void testRemoveMonitor() {
        MonitorCreate create = podamFactory.manufacturePojo(MonitorCreate.class);
        create.setAgentType("REMOTE");
        String tenantId = RandomStringUtils.randomAlphanumeric(10);
        monitorManagement.createMonitor(tenantId, create);

        Monitor monitor = monitorManagement.getMonitor(tenantId, create.getMonitorId());
        assertThat(monitor, notNullValue());

        monitorManagement.removeMonitor(tenantId, create.getMonitorId());
        monitor = monitorManagement.getMonitor(tenantId, create.getMonitorId());
        assertThat(monitor, nullValue());
    }
}
