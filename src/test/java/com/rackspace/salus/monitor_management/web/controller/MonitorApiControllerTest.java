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

package com.rackspace.salus.monitor_management.web.controller;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.entities.Monitor;
import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.services.MonitorManagement;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.telegraf.Mem;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.PagedContent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

/**
 * The benefits from these tests are strictly making sure that our routes are working correctly
 *
 * The business logic is being tested in MonitorManagementTest and MonitorConversionServiceTest with proper edge case testing
 */

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = MonitorApiController.class)
@AutoConfigureDataJpa
@Import({MonitorConversionService.class})
public class MonitorApiControllerTest {

    private PodamFactory podamFactory = new PodamFactoryImpl();

    @Autowired
    MockMvc mockMvc;

    @MockBean
    MonitorManagement monitorManagement;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MonitorConversionService monitorConversionService;

    @Test
    public void testGetMonitor() throws Exception {
        Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
        monitor.setSelectorScope(ConfigSelectorScope.LOCAL);
        monitor.setAgentType(AgentType.TELEGRAF);
        monitor.setContent("{\"type\":\"mem\"}");
        when(monitorManagement.getMonitor(anyString(), any()))
                .thenReturn(Optional.of(monitor));

        String tenantId = RandomStringUtils.randomAlphabetic(8);
        UUID id = UUID.randomUUID();
        String url = String.format("/api/tenant/%s/monitors/%s", tenantId, id);

        mockMvc.perform(get(url).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(monitor.getId().toString())));
    }

    @Test
    public void testNoMonitorFound() throws Exception {
        when(monitorManagement.getMonitor(anyString(), any()))
                .thenReturn(Optional.empty());

        String tenantId = RandomStringUtils.randomAlphabetic(8);
        UUID id = UUID.randomUUID();
        String url = String.format("/api/tenant/%s/monitors/%s", tenantId, id);
        String errorMsg = String.format("No monitor found for %s on tenant %s", id, tenantId);

        mockMvc.perform(get(url).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", is(errorMsg)));
    }

    @Test
    public void testGetAllForTenant() throws Exception {
        int numberOfMonitors = 1;
        // Use the APIs default Pageable settings
        int page = 0;
        int pageSize = 100;
        List<Monitor> monitors = createMonitors(numberOfMonitors);

        int start = page * pageSize;
        Page<Monitor> pageOfMonitors = new PageImpl<>(monitors.subList(start, numberOfMonitors),
                PageRequest.of(page, pageSize),
                numberOfMonitors);

        PagedContent<Monitor> result = PagedContent.fromPage(pageOfMonitors);

        when(monitorManagement.getMonitors(anyString(), any()))
                .thenReturn(pageOfMonitors);

        String tenantId = RandomStringUtils.randomAlphabetic(8);
        String url = String.format("/api/tenant/%s/monitors", tenantId);

        mockMvc.perform(get(url).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(objectMapper.writeValueAsString(result.map(monitorConversionService::convertToOutput))))
                .andExpect(jsonPath("$.content.*", hasSize(numberOfMonitors)))
                .andExpect(jsonPath("$.totalPages", equalTo(1)))
                .andExpect(jsonPath("$.totalElements", equalTo(numberOfMonitors)))
                .andExpect(jsonPath("$.number", equalTo(page)))
                .andExpect(jsonPath("$.last", is(true)))
                .andExpect(jsonPath("$.first", is(true)));
    }

    private List<Monitor> createMonitors(int numberOfMonitors) {
        List<Monitor> monitors = new ArrayList<>();
        for (int i = 0; i < numberOfMonitors; i++) {
            final Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
            monitors.add(monitor);
            monitor.setSelectorScope(ConfigSelectorScope.LOCAL);
            monitor.setAgentType(AgentType.TELEGRAF);
            monitor.setContent("{\"type\":\"mem\"}");
        }
        return monitors;
    }

    @Test
    public void testGetAllForTenantPagination() throws Exception {
        int numberOfMonitors = 99;
        int pageSize = 4;
        int page = 14;
        final List<Monitor> monitors = createMonitors(numberOfMonitors);
        int start = page * pageSize;
        int end = start + pageSize;
        Page<Monitor> pageOfMonitors = new PageImpl<>(monitors.subList(start, end),
                PageRequest.of(page, pageSize),
                numberOfMonitors);

        PagedContent<Monitor> result = PagedContent.fromPage(pageOfMonitors);

        assertThat(pageOfMonitors.getContent().size(), equalTo(pageSize));

        when(monitorManagement.getMonitors(anyString(), any()))
                .thenReturn(pageOfMonitors);

        String tenantId = RandomStringUtils.randomAlphabetic(8);
        String url = String.format("/api/tenant/%s/monitors", tenantId);

        mockMvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)
                .param("page", Integer.toString(page))
                .param("size", Integer.toString(pageSize)))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(objectMapper.writeValueAsString(result.map(monitorConversionService::convertToOutput))))
                .andExpect(jsonPath("$.content.*", hasSize(pageSize)))
                .andExpect(jsonPath("$.totalPages", equalTo((numberOfMonitors + pageSize - 1) / pageSize)))
                .andExpect(jsonPath("$.totalElements", equalTo(numberOfMonitors)))
                .andExpect(jsonPath("$.number", equalTo(page)))
                .andExpect(jsonPath("$.last", is(false)))
                .andExpect(jsonPath("$.first", is(false)));
    }

    @Test
    public void testCreateMonitor() throws Exception {
        Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
        monitor.setSelectorScope(ConfigSelectorScope.LOCAL);
        monitor.setAgentType(AgentType.TELEGRAF);
        monitor.setContent("{\"type\":\"mem\"}");
        when(monitorManagement.createMonitor(anyString(), any()))
                .thenReturn(monitor);

        String tenantId = RandomStringUtils.randomAlphabetic(8);
        String url = String.format("/api/tenant/%s/monitors", tenantId);
        DetailedMonitorInput create = podamFactory.manufacturePojo(DetailedMonitorInput.class);
        create.setDetails(new LocalMonitorDetails().setPlugin(new Mem()));

        mockMvc.perform(post(url)
                .content(objectMapper.writeValueAsString(create))
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8.name()))
                .andExpect(status().isCreated())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }



    @Test
    public void testUpdateMonitor() throws Exception {
        Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
        monitor.setSelectorScope(ConfigSelectorScope.LOCAL);
        monitor.setAgentType(AgentType.TELEGRAF);
        monitor.setContent("{\"type\":\"mem\"}");
        when(monitorManagement.updateMonitor(anyString(), any(), any()))
                .thenReturn(monitor);

        String tenantId = monitor.getTenantId();
        UUID id = monitor.getId();
        String url = String.format("/api/tenant/%s/monitors/%s", tenantId, id);

        DetailedMonitorInput update = podamFactory.manufacturePojo(DetailedMonitorInput.class);
        update.setLabelSelector(null);
        update.setDetails(new LocalMonitorDetails().setPlugin(new Mem()));

        mockMvc.perform(put(url)
                .content(objectMapper.writeValueAsString(update))
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8.name()))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    public void testUpdateNonExistentMonitor() throws Exception {
        when(monitorManagement.updateMonitor(anyString(), any(), any()))
                .thenThrow(new NotFoundException("Custom not found message"));

        String tenantId = RandomStringUtils.randomAlphabetic(10);
        UUID id = UUID.randomUUID();
        String url = String.format("/api/tenant/%s/monitors/%s", tenantId, id);

        DetailedMonitorInput update = podamFactory.manufacturePojo(DetailedMonitorInput.class);
        update.setDetails(new LocalMonitorDetails().setPlugin(new Mem()));

        mockMvc.perform(put(url)
                .content(objectMapper.writeValueAsString(update))
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8.name()))
                .andExpect(status().isNotFound())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    public void testGetAll() throws Exception {
        int numberOfMonitors = 20;
        // Use the APIs default Pageable settings
        int page = 0;
        int pageSize = 100;
        final List<Monitor> monitors = createMonitors(numberOfMonitors);

        int start = page * pageSize;
        Page<Monitor> pageOfMonitors = new PageImpl<>(monitors.subList(start, numberOfMonitors),
                PageRequest.of(page, pageSize),
                numberOfMonitors);

        PagedContent<Monitor> result = PagedContent.fromPage(pageOfMonitors);

        when(monitorManagement.getAllMonitors(any()))
                .thenReturn(pageOfMonitors);

        String url = "/api/admin/monitors";

        mockMvc.perform(get(url).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(objectMapper.writeValueAsString(result.map(monitorConversionService::convertToOutput))))
                .andExpect(jsonPath("$.content.*", hasSize(numberOfMonitors)))
                .andExpect(jsonPath("$.totalPages", equalTo(1)))
                .andExpect(jsonPath("$.totalElements", equalTo(numberOfMonitors)))
                .andExpect(jsonPath("$.number", equalTo(page)))
                .andExpect(jsonPath("$.last", is(true)))
                .andExpect(jsonPath("$.first", is(true)));
    }
}
