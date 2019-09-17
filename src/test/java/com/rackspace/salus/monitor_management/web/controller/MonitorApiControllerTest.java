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

import static com.rackspace.salus.telemetry.entities.Monitor.POLICY_TENANT;
import static com.rackspace.salus.test.WebTestUtils.classValidationError;
import static com.rackspace.salus.test.WebTestUtils.httpMessageNotReadable;
import static com.rackspace.salus.test.WebTestUtils.validationError;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.rackspace.salus.common.util.SpringResourceUtils.readContent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.services.MonitorManagement;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.telegraf.Mem;
import com.rackspace.salus.monitor_management.web.model.telegraf.Ping;
import com.rackspace.salus.monitor_management.web.model.validator.ValidCreateMonitor;
import com.rackspace.salus.monitor_management.web.model.validator.ValidUpdateMonitor;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.PagedContent;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

/**
 * The benefits from these tests are strictly making sure that our routes are working correctly
 *
 * The business logic is being tested in MonitorManagementTest and MonitorConversionServiceTest with proper edge case testing
 */

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = MonitorApiController.class)
@Import({MonitorConversionService.class, MetadataUtils.class})
public class MonitorApiControllerTest {

  private PodamFactory podamFactory = new PodamFactoryImpl();

  @Autowired
  MockMvc mockMvc;

  @MockBean
  PolicyApi policyApi;

  @MockBean
  MonitorManagement monitorManagement;

  @MockBean
  MonitorRepository monitorRepository;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  MonitorConversionService monitorConversionService;

  @Autowired
  MetadataUtils metadataUtils;

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
        .andDo(print())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id", is(monitor.getId().toString())));
  }

  @Test
  public void testGetPolicyMonitor() throws Exception {
    Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
    monitor.setSelectorScope(ConfigSelectorScope.LOCAL);
    monitor.setAgentType(AgentType.TELEGRAF);
    monitor.setContent("{\"type\":\"mem\"}");
    monitor.setTenantId(POLICY_TENANT);

    when(monitorManagement.getPolicyMonitor(any()))
        .thenReturn(Optional.of(monitor));

    String url = String.format("/api/admin/policy-monitors/%s", monitor.getId());

    mockMvc.perform(get(url).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id", is(monitor.getId().toString())));

    verify(monitorManagement).getPolicyMonitor(monitor.getId());
    verifyNoMoreInteractions(monitorManagement);
  }

  @Test
  public void testGetPolicyMonitor_doesntExist() throws Exception {
    when(monitorManagement.getPolicyMonitor(any()))
        .thenReturn(Optional.empty());

    UUID id = UUID.randomUUID();
    String url = String.format("/api/admin/policy-monitors/%s", id);

    mockMvc.perform(get(url).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

    verify(monitorManagement).getPolicyMonitor(id);
    verifyNoMoreInteractions(monitorManagement);
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
    int pageSize = 20;
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
    monitor.setLabelSelectorMethod(LabelSelectorMethod.OR);
    when(monitorManagement.createMonitor(anyString(), any()))
        .thenReturn(monitor);

    String tenantId = RandomStringUtils.randomAlphabetic(8);
    String url = String.format("/api/tenant/%s/monitors", tenantId);
    DetailedMonitorInput create = podamFactory.manufacturePojo(DetailedMonitorInput.class);
    create.setDetails(new LocalMonitorDetails().setPlugin(new Mem()))
        .setResourceId("");

    mockMvc.perform(post(url)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isCreated())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  @Test
  public void testCreateMonitor_NullLabelSelector() throws Exception {
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
    create.setLabelSelector(null);

    mockMvc.perform(post(url)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isCreated())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  @Test
  public void testCreateRemotePingMonitor() throws Exception {
    Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
    monitor.setSelectorScope(ConfigSelectorScope.REMOTE);
    monitor.setAgentType(AgentType.TELEGRAF);
    monitor.setContent("{\"type\":\"ping\"}");
    when(monitorManagement.createMonitor(anyString(), any()))
        .thenReturn(monitor);

    String tenantId = RandomStringUtils.randomAlphabetic(8);
    String url = String.format("/api/tenant/%s/monitors", tenantId);
    DetailedMonitorInput create = podamFactory.manufacturePojo(DetailedMonitorInput.class);
    create.setDetails(new RemoteMonitorDetails()
        .setMonitoringZones(monitor.getZones())
        .setPlugin(new Ping()
            .setUrls(Collections.singletonList("my.test.url.com"))))
        .setResourceId("");

    mockMvc.perform(post(url)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isCreated())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  @Test
  public void testCreateRemotePingMonitor_NoUrls() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic(8);
    String url = String.format("/api/tenant/%s/monitors", tenantId);
    DetailedMonitorInput create = podamFactory.manufacturePojo(DetailedMonitorInput.class);
    create.setDetails(new RemoteMonitorDetails()
        .setMonitoringZones(Collections.singletonList("myzone"))
        .setPlugin(new Ping()
            // If no urls are set validation should fail
            .setUrls(Collections.emptyList())))
        .setResourceId("");

    mockMvc.perform(post(url)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isBadRequest())
        .andExpect(validationError("details.plugin.urls", "must not be empty"));
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
    update.setDetails(new LocalMonitorDetails().setPlugin(new Mem()))
        .setResourceId("");

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
    int pageSize = 20;
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

  @Test
  public void testGetAllMonitors_LargePage() throws Exception {
    // This test is to verify that we can get more than 1000 results back via spring mvc.

    int numberOfMonitors = 2000;
    // Use the APIs default Pageable settings
    int page = 0;
    int pageSize = Integer.MAX_VALUE;
    final List<Monitor> monitors = createMonitors(numberOfMonitors);

    int start = page * pageSize;
    Page<Monitor> pageOfMonitors = new PageImpl<>(monitors.subList(start, numberOfMonitors),
        PageRequest.of(page, pageSize),
        numberOfMonitors);

    PagedContent<Monitor> result = PagedContent.fromPage(pageOfMonitors);

    when(monitorManagement.getAllMonitors(any()))
        .thenReturn(pageOfMonitors);

    mockMvc.perform(get("/api/admin/monitors")
        .requestAttr("size", pageSize)
        .contentType(MediaType.APPLICATION_JSON))
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

  @Test
  public void testGetMonitorLabelSelectors() throws Exception {
    final MultiValueMap<String, String> expected = new LinkedMultiValueMap<>();
    expected.put("agent_discovered_os", Arrays.asList("linux", "darwin", "windows"));
    expected.put("agent_discovered_arch", Arrays.asList("amd64", "386"));
    expected.put("cluster", Arrays.asList("dev", "prod"));

    when(monitorManagement.getTenantMonitorLabelSelectors(any()))
        .thenReturn(expected);

    mockMvc.perform(get(
        "/api/tenant/{tenantId}/monitor-label-selectors",
        "t-1"
    ).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(
            content().json(readContent("/MonitorApiControllerTest/monitor_label_selectors.json"), true));

    verify(monitorManagement).getTenantMonitorLabelSelectors("t-1");

    verifyNoMoreInteractions(monitorManagement);
  }

  private DetailedMonitorInput setupCreateMonitorTest() {
    Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
    monitor.setSelectorScope(ConfigSelectorScope.LOCAL);
    monitor.setAgentType(AgentType.TELEGRAF);
    monitor.setContent("{\"type\":\"mem\"}");
    when(monitorManagement.createMonitor(anyString(), any()))
        .thenReturn(monitor);

    DetailedMonitorInput create = podamFactory.manufacturePojo(DetailedMonitorInput.class);
    create.setDetails(new LocalMonitorDetails().setPlugin(new Mem()));
    return create;
  }

  @Test
  public void testCreateMonitor_ResourceId() throws Exception {
    DetailedMonitorInput create = setupCreateMonitorTest();
    String tenantId = RandomStringUtils.randomAlphabetic(8);
    String url = String.format("/api/tenant/%s/monitors", tenantId);
    create.setDetails(new LocalMonitorDetails().setPlugin(new Mem()))
        .setLabelSelector(null);

    mockMvc.perform(post(url)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isCreated())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  @Test
  public void testCreateMonitor_BothResourceIdAndLabels() throws Exception {
    DetailedMonitorInput create = setupCreateMonitorTest();
    String tenantId = RandomStringUtils.randomAlphabetic(8);
    String url = String.format("/api/tenant/%s/monitors", tenantId);
    create.setDetails(new LocalMonitorDetails().setPlugin(new Mem()));

    mockMvc.perform(post(url)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isBadRequest())
        .andExpect(classValidationError(ValidCreateMonitor.DEFAULT_MESSAGE));
  }

  @Test
  public void testCreateMonitor_BothResourceIdAndEmptyLabels() throws Exception {
    DetailedMonitorInput create = setupCreateMonitorTest();
    String tenantId = RandomStringUtils.randomAlphabetic(8);
    String url = String.format("/api/tenant/%s/monitors", tenantId);
    create.setDetails(new LocalMonitorDetails().setPlugin(new Mem()));
    create.setLabelSelector(Collections.emptyMap());

    mockMvc.perform(post(url)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isBadRequest())
        .andExpect(classValidationError(ValidCreateMonitor.DEFAULT_MESSAGE));
  }
  @Test
  public void testCreateMonitor_NeitherResourceIdNorLabels() throws Exception {
    DetailedMonitorInput create = setupCreateMonitorTest();
    String tenantId = RandomStringUtils.randomAlphabetic(8);
    String url = String.format("/api/tenant/%s/monitors", tenantId);
    create.setDetails(new LocalMonitorDetails().setPlugin(new Mem()))
        .setLabelSelector(null)
        .setResourceId("");

    mockMvc.perform(post(url)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isBadRequest())
        .andExpect(classValidationError(ValidCreateMonitor.DEFAULT_MESSAGE));
  }

  @Test
  public void testUpdateMonitor_WithLabelsOnly() throws Exception {
    UpdateMonitorTestSetup updateMonitorTestSetup = new UpdateMonitorTestSetup().invoke();
    String url = updateMonitorTestSetup.getUrl();
    DetailedMonitorInput update = updateMonitorTestSetup.getUpdate();
    update.setResourceId("");

    mockMvc.perform(put(url)
        .content(objectMapper.writeValueAsString(update))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isOk())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }


  @Test
  public void testUpdateMonitor_NeitherLabelsNorResourceId() throws Exception {
    UpdateMonitorTestSetup updateMonitorTestSetup = new UpdateMonitorTestSetup().invoke();
    String url = updateMonitorTestSetup.getUrl();
    DetailedMonitorInput update = updateMonitorTestSetup.getUpdate();
    update.setResourceId("");
    update.setLabelSelector(null);

    mockMvc.perform(put(url)
        .content(objectMapper.writeValueAsString(update))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isOk())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }
  @Test
  public void testUpdateMonitor_BothLabelsAndResourceId() throws Exception {
    UpdateMonitorTestSetup updateMonitorTestSetup = new UpdateMonitorTestSetup().invoke();
    String url = updateMonitorTestSetup.getUrl();
    DetailedMonitorInput update = updateMonitorTestSetup.getUpdate();

    mockMvc.perform(put(url)
        .content(objectMapper.writeValueAsString(update))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isBadRequest())
        .andExpect(classValidationError(ValidUpdateMonitor.DEFAULT_MESSAGE));
  }

  @Test
  public void testCreateMonitor_intervalParsing_valid() throws Exception {
    final String content = readContent("MonitorApiControllerTest/create_monitor_duration.json");

    final Monitor stubMonitorResp = new Monitor()
        .setId(UUID.randomUUID())
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);
    when(monitorManagement.createMonitor(any(), any()))
        .thenReturn(stubMonitorResp);

    mockMvc.perform(post("/api/tenant/t-1/monitors")
        .content(content)
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isCreated())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

    verify(monitorManagement)
        .createMonitor("t-1",
            new MonitorCU()
                .setInterval(Duration.ofSeconds(30))
                .setLabelSelector(Map.of("agent_environment", "localdev"))
                .setSelectorScope(ConfigSelectorScope.LOCAL)
                .setMonitorType(MonitorType.cpu)
                .setAgentType(AgentType.TELEGRAF)
                .setPluginMetadataFields(Collections.emptyList())
                .setContent(readContent("MonitorApiControllerTest/converted_monitor_duration.json"))
        );
  }

  @Test
  public void testCreateMonitor_intervalParsing_invalidWithNumber() throws Exception {
    final String content = readContent("MonitorApiControllerTest/create_monitor_duration_number.json");

    mockMvc.perform(post("/api/tenant/t-1/monitors")
        .content(content)
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isBadRequest())
        .andExpect(httpMessageNotReadable(
            "Cannot deserialize value of type `java.time.Duration` from String \"30\""));

  }

  private class UpdateMonitorTestSetup {

    private String url;
    private DetailedMonitorInput update;

    String getUrl() {
      return url;
    }

    DetailedMonitorInput getUpdate() {
      return update;
    }

    UpdateMonitorTestSetup invoke() {
      Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
      monitor.setSelectorScope(ConfigSelectorScope.LOCAL);
      monitor.setAgentType(AgentType.TELEGRAF);
      monitor.setContent("{\"type\":\"mem\"}");
      when(monitorManagement.updateMonitor(anyString(), any(), any()))
          .thenReturn(monitor);

      String tenantId = monitor.getTenantId();
      UUID id = monitor.getId();
      url = String.format("/api/tenant/%s/monitors/%s", tenantId, id);

      update = podamFactory.manufacturePojo(DetailedMonitorInput.class);
      update.setDetails(new LocalMonitorDetails().setPlugin(new Mem()));
      return this;
    }
  }
}
