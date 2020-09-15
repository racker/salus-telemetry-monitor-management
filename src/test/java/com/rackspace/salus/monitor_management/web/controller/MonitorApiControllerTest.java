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

package com.rackspace.salus.monitor_management.web.controller;

import static com.rackspace.salus.common.util.SpringResourceUtils.readContent;
import static com.rackspace.salus.monitor_management.web.converter.PatchHelper.JSON_PATCH_TYPE;
import static com.rackspace.salus.telemetry.entities.Monitor.POLICY_TENANT;
import static com.rackspace.salus.test.WebTestUtils.classValidationError;
import static com.rackspace.salus.test.WebTestUtils.httpMessageNotReadable;
import static com.rackspace.salus.test.WebTestUtils.validationError;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.config.JsonConfig;
import com.rackspace.salus.monitor_management.services.MonitorContentTranslationService;
import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.services.MonitorManagement;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.monitor_management.web.model.AgentConfigRequest;
import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import com.rackspace.salus.monitor_management.web.model.CloneMonitorRequest;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.Protocol;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RenderedMonitorTemplate;
import com.rackspace.salus.monitor_management.web.model.TranslateMonitorContentRequest;
import com.rackspace.salus.monitor_management.web.model.telegraf.Mem;
import com.rackspace.salus.monitor_management.web.model.telegraf.NetResponse;
import com.rackspace.salus.monitor_management.web.model.telegraf.Ping;
import com.rackspace.salus.monitor_management.web.validator.ValidCreateMonitor;
import com.rackspace.salus.monitor_management.web.validator.ValidUpdateMonitor;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.entities.MonitorTranslationOperator;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.PagedContent;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import com.rackspace.salus.telemetry.repositories.TenantMetadataRepository;
import com.rackspace.salus.telemetry.web.TenantVerification;
import com.rackspace.salus.telemetry.web.TenantVerificationWebConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.springframework.data.domain.Pageable;
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
@Import({MonitorConversionService.class, MetadataUtils.class, PatchHelper.class, JsonConfig.class,
    TenantVerificationWebConfig.class, SimpleMeterRegistry.class})
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

  @MockBean
  TenantMetadataRepository tenantMetadataRepository;

  @MockBean
  MonitorContentTranslationService monitorContentTranslationService;

  @Autowired
  PatchHelper patchHelper;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  MonitorConversionService monitorConversionService;

  @Autowired
  MetadataUtils metadataUtils;

  @Test
  public void testTenantVerification_Success() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic(8);
    UUID id = UUID.randomUUID();
    String url = String.format("/api/tenant/%s/monitors/%s", tenantId, id);
    String errorMsg = String.format("No monitor found for %s on tenant %s", id, tenantId);

    when(monitorManagement.getMonitor(anyString(), any()))
        .thenReturn(Optional.empty());
    when(tenantMetadataRepository.existsByTenantId(tenantId))
        .thenReturn(true);

    mockMvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)
        // header must be set to trigger tenant verification
        .header(TenantVerification.HEADER_TENANT, tenantId))
        .andExpect(status().isNotFound())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message", is(errorMsg)));

    verify(tenantMetadataRepository).existsByTenantId(tenantId);
  }

  @Test
  public void testTenantVerification_Fail() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic(8);
    UUID id = UUID.randomUUID();
    String url = String.format("/api/tenant/%s/monitors/%s", tenantId, id);

    when(monitorManagement.getMonitor(anyString(), any()))
        .thenReturn(Optional.empty());
    when(tenantMetadataRepository.existsByTenantId(tenantId))
        .thenReturn(false);

    mockMvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)
        // header must be set to trigger tenant verification
        .header(TenantVerification.HEADER_TENANT, tenantId))
        .andExpect(status().isNotFound())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message", is(TenantVerification.ERROR_MSG)));

    verify(tenantMetadataRepository).existsByTenantId(tenantId);
  }

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
    create.setExcludedResourceIds(null);

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
            .setTarget("my.test.url.com")))
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
            .setTarget(null)))
        .setResourceId("");

    mockMvc.perform(post(url)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isBadRequest())
        .andExpect(validationError("details.plugin.target", "must not be empty"));
  }

  @Test
  public void testCreateRemoteNetResponseMonitor_NoPort() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic(8);
    String url = String.format("/api/tenant/%s/monitors", tenantId);
    DetailedMonitorInput create = podamFactory.manufacturePojo(DetailedMonitorInput.class);
    create.setDetails(new RemoteMonitorDetails()
        .setMonitoringZones(Collections.singletonList("myzone"))
        .setPlugin(new NetResponse()
            // If no port is set validation should fail
            .setProtocol(Protocol.tcp)
            .setHost(RandomStringUtils.randomAlphabetic(10))))
        .setResourceId("");

    mockMvc.perform(post(url)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isBadRequest())
        .andExpect(validationError("details.plugin.port", "must not be null"));
  }

  @Test
  public void testCreateRemoteNetResponseMonitor_InvalidPort() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic(8);
    String url = String.format("/api/tenant/%s/monitors", tenantId);
    DetailedMonitorInput create = podamFactory.manufacturePojo(DetailedMonitorInput.class);
    create.setDetails(new RemoteMonitorDetails()
        .setMonitoringZones(Collections.singletonList("myzone"))
        .setPlugin(new NetResponse()
            // If an invalid port is set validation should fail
            .setPort(123456789)
            .setProtocol(Protocol.tcp)
            .setHost(RandomStringUtils.randomAlphabetic(10))))
        .setResourceId("");

    mockMvc.perform(post(url)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isBadRequest())
        .andExpect(validationError("details.plugin.port", "must be less than or equal to 65535"));
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
    update.setExcludedResourceIds(null);
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

    // Basic verification that the expected method was called
    // Uses arg matcher since we do not need to validate the monitorCU here.
    verify(monitorManagement).updateMonitor(
        argThat(t -> t.equals(tenantId)),
        argThat(i -> i.equals(id)),
        argThat(monitorCU -> true));
  }

  @Test
  public void testPatchMonitor() throws Exception {
    Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
    monitor.setId(UUID.randomUUID());
    monitor.setSelectorScope(ConfigSelectorScope.LOCAL);
    monitor.setAgentType(AgentType.TELEGRAF);
    monitor.setContent("{\"type\":\"mem\"}");
    // ensure only one of these is set
    monitor.setResourceId(RandomStringUtils.randomAlphabetic(10));
    monitor.setLabelSelector(null);
    monitor.setExcludedResourceIds(null);

    when(monitorManagement.getMonitor(anyString(), any()))
        .thenReturn(Optional.of(monitor));
    when(monitorManagement.updateMonitor(anyString(), any(), any(), anyBoolean()))
        .thenReturn(monitor);

    String tenantId = monitor.getTenantId();
    UUID id = monitor.getId();
    String url = String.format("/api/tenant/%s/monitors/%s", tenantId, id);

    // send an update with a new name and a null interval
    String update = "[{\"op\":\"replace\",\"path\": \"/name\",\"value\":\"newName\"},"
        + "{\"op\":\"replace\",\"path\": \"/interval\",\"value\":null}]";

    mockMvc.perform(patch(url)
        .content(update)
        .contentType(MediaType.valueOf(JSON_PATCH_TYPE))
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

    // Basic verification that the expected method was called with the patchOperation value provided.
    // In this case mockito had issues when argThat was used to validate params, so I opted
    // for even more generic validation.
    verify(monitorManagement).updateMonitor(anyString(), any(), any(), anyBoolean());
  }

  @Test
  public void testPatchNonExistentMonitor() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic(10);
    UUID id = UUID.randomUUID();
    String url = String.format("/api/tenant/%s/monitors/%s", tenantId, id);

    // send an update with a new name
    String update = "[{\"op\":\"replace\",\"path\": \"/name\",\"value\":\"newName\"}]";

    mockMvc.perform(patch(url)
        .content(update)
        .contentType(MediaType.valueOf(JSON_PATCH_TYPE))
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isNotFound())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

    verify(monitorManagement).getMonitor(tenantId, id);
    verifyNoMoreInteractions(monitorManagement);
  }

  @Test
  public void testPatchPolicyMonitor() throws Exception {
    Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
    monitor.setId(UUID.randomUUID());
    monitor.setTenantId(POLICY_TENANT);
    monitor.setSelectorScope(ConfigSelectorScope.LOCAL);
    monitor.setAgentType(AgentType.TELEGRAF);
    monitor.setContent("{\"type\":\"mem\"}");
    // ensure only one of these is set
    monitor.setResourceId(null);
    monitor.setLabelSelector(Map.of("os", "linux"));
    monitor.setInterval(Duration.ofSeconds(60));

    when(monitorManagement.getPolicyMonitor(any()))
        .thenReturn(Optional.of(monitor));
    when(monitorManagement.updatePolicyMonitor(any(), any(), anyBoolean()))
        .thenReturn(monitor);

    UUID id = monitor.getId();
    String url = String.format("/api/admin/policy-monitors/%s", id);

    // send an update with a null name and a new interval
    String update = "[{\"op\":\"replace\",\"path\": \"/name\",\"value\":null},"
        + "{\"op\":\"replace\",\"path\": \"/interval\",\"value\":234}]";

    mockMvc.perform(patch(url)
        .content(update)
        .contentType(MediaType.valueOf(JSON_PATCH_TYPE))
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

    // Basic verification that the expected method was called with the patchOperation value provided.
    // In this case mockito had issues when argThat was used to validate params, so I opted
    // for even more generic validation.
    verify(monitorManagement).updatePolicyMonitor(any(), any(), anyBoolean());
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
  public void testQueryBoundMonitors() throws Exception {
    final List<BoundMonitor> boundMonitors = List.of(
        podamFactory.manufacturePojo(BoundMonitor.class)
    );
    when(monitorManagement.getAllBoundMonitorsByEnvoyId(any()))
        .thenReturn(boundMonitors);

    final List<BoundMonitorDTO> boundMonitorDtos = List.of(
        podamFactory.manufacturePojo(BoundMonitorDTO.class)
    );
    when(monitorContentTranslationService.translateBoundMonitors(any(), any()))
        .thenReturn(boundMonitorDtos);

    final String reqBody = readContent("MonitorApiControllerTest/query_bound_monitors.json");

    mockMvc.perform(post("/api/admin/bound-monitors")
        .content(reqBody)
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()", is(1)))
        .andExpect(jsonPath("$[0].monitorId",
            is(boundMonitorDtos.get(0).getMonitorId().toString())));

    verify(monitorManagement).getAllBoundMonitorsByEnvoyId("e-1");

    verify(monitorContentTranslationService)
        .translateBoundMonitors(boundMonitors, Map.of(AgentType.TELEGRAF, "1.12.0"));
  }

  @Test
  public void testGetBoundMonitorsForTenant() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic(8);

    final List<BoundMonitor> boundMonitors = List.of(
        podamFactory.manufacturePojo(BoundMonitor.class)
    );
    for (BoundMonitor boundMonitor : boundMonitors) {
      boundMonitor.setTenantId(tenantId);
      boundMonitor.getMonitor().setTenantId(tenantId);
      boundMonitor.getMonitor().setSelectorScope(ConfigSelectorScope.LOCAL);
      boundMonitor.getMonitor().setAgentType(AgentType.TELEGRAF);
      boundMonitor.getMonitor().setMonitorType(MonitorType.disk);
      boundMonitor.getMonitor().setContent("{\"type\":\"disk\",\"mount\":\"/usr\"}");
    }

    when(monitorManagement.getAllBoundMonitorsByTenantId(any(), any()))
        .thenReturn(new PageImpl<>(boundMonitors));

    final List<BoundMonitorDTO> boundMonitorDtos = List.of(
        podamFactory.manufacturePojo(BoundMonitorDTO.class)
    );
    when(monitorContentTranslationService.translateBoundMonitors(any(), any()))
        .thenReturn(boundMonitorDtos);

    mockMvc.perform(get("/api/tenant/{tenant}/bound-monitors", tenantId)
        .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements", is(1)))
        .andExpect(jsonPath("$.content.length()", is(1)))
        .andExpect(jsonPath("$.content[0].monitorId",
            is(boundMonitors.get(0).getMonitor().getId().toString())))
        .andExpect(jsonPath("$.content[0].monitorType",
            is("disk")))
        .andExpect(jsonPath("$.content[0].monitorName",
            is(boundMonitors.get(0).getMonitor().getMonitorName())))
        .andExpect(jsonPath("$.content[0].monitorSummary.mount",
            is("/usr")))
    ;

    verify(monitorManagement).getAllBoundMonitorsByTenantId(eq(tenantId), any());
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
    create.setExcludedResourceIds(null);
    return create;
  }

  @Test
  public void testCreateMonitor_ResourceIdAndNullLabels() throws Exception {
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
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setAgentType(AgentType.TELEGRAF)
        .setContent("{\"type\":\"cpu\"}")
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
                .setPluginMetadataFields(List.of("percpu", "collectCpuTime", "reportActive"))
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

  @Test
  public void testCloneMonitor() throws Exception {
    Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
    monitor.setSelectorScope(ConfigSelectorScope.LOCAL);
    monitor.setAgentType(AgentType.TELEGRAF);
    monitor.setContent("{\"type\":\"mem\"}");
    monitor.setLabelSelectorMethod(LabelSelectorMethod.OR);
    when(monitorManagement.cloneMonitor(anyString(), anyString(), any()))
        .thenReturn(monitor);

    String url = "/api/admin/clone-monitor";
    CloneMonitorRequest request = new CloneMonitorRequest()
        .setMonitorId(UUID.randomUUID())
        .setNewTenant(RandomStringUtils.randomAlphabetic(10))
        .setOriginalTenant(RandomStringUtils.randomAlphanumeric(10));

    mockMvc.perform(post(url)
        .content(objectMapper.writeValueAsString(request))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isCreated())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  @Test
  public void testSearchMonitor() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic(8);

    Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
    monitor.setTenantId(tenantId);
    monitor.setContent("{\"type\":\"mem\"}");
    monitor.setAgentType(AgentType.TELEGRAF);
    monitor.setSelectorScope(ConfigSelectorScope.LOCAL);
    monitor.setMonitorType(MonitorType.mem);

    when(monitorManagement.getMonitorsBySearchString(anyString(), anyString(), any()))
        .thenReturn(new PageImpl<>(Collections.singletonList(monitor)));

    String url = "/api/tenant/{tenantId}/search";
    String searchCriteria = "mem";
    mockMvc.perform(get(url, tenantId)
        .param("q", searchCriteria)
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isOk())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    Pageable page = PageRequest.of(0, 20);
    verify(monitorManagement).getMonitorsBySearchString(tenantId, searchCriteria, page);

    verifyNoMoreInteractions(monitorManagement);
  }

  @Test
  public void testTranslateMonitorContent() throws Exception {
    final List<MonitorTranslationOperator> operators = List
        .of(new MonitorTranslationOperator().setName("for-testing"));

    when(monitorContentTranslationService.loadOperatorsByAgentTypeAndVersion(any()))
        .thenReturn(Map.of(AgentType.TELEGRAF, operators));

    when(monitorContentTranslationService.prepareOperatorsForMonitor(any(), any(), any()))
        .thenReturn(operators);

    when(monitorContentTranslationService.translateMonitorContent(any(), any()))
        .thenReturn("translated content");

    mockMvc.perform(post("/api/admin/translate-monitor-content")
        .content(objectMapper.writeValueAsString(
            new TranslateMonitorContentRequest()
                .setAgentType(AgentType.TELEGRAF)
                .setAgentVersion("1.13.2")
                .setMonitorType(MonitorType.cpu)
                .setScope(ConfigSelectorScope.LOCAL)
                .setContent("original content")
        ))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(content().string("translated content"));

    verify(monitorContentTranslationService).loadOperatorsByAgentTypeAndVersion(
        Map.of(AgentType.TELEGRAF, "1.13.2")
    );

    verify(monitorContentTranslationService)
        .prepareOperatorsForMonitor(operators, MonitorType.cpu, ConfigSelectorScope.LOCAL);

    verify(monitorContentTranslationService).translateMonitorContent(operators, "original content");

    verifyNoMoreInteractions(monitorContentTranslationService);
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

  @Test
  public void testGetAgentConfig() throws Exception {
    final List<MonitorTranslationOperator> operators = List
        .of(new MonitorTranslationOperator().setName("for-testing"));

    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    UUID monitorId = UUID.randomUUID();
    String resourceId = RandomStringUtils.randomAlphanumeric(10);

    RenderedMonitorTemplate renderedMonitorTemplate = podamFactory.manufacturePojo(RenderedMonitorTemplate.class);
    Monitor monitor = podamFactory.manufacturePojo(Monitor.class);
    monitor.setTenantId(tenantId);
    monitor.setId(monitorId);
    renderedMonitorTemplate.setMonitor(monitor);

    AgentConfigRequest agentConfigRequest = podamFactory.manufacturePojo(AgentConfigRequest.class);
    agentConfigRequest.setResourceId(resourceId);
    agentConfigRequest.setAgentVersion("1.13.2");

    when(monitorManagement.renderMonitorTemplate(any(), anyString(), anyString())).thenReturn(renderedMonitorTemplate);

    when(monitorContentTranslationService.loadOperatorsByAgentTypeAndVersion(any()))
        .thenReturn(Map.of(agentConfigRequest.getAgentType(), operators));

    when(monitorContentTranslationService.prepareOperatorsForMonitor(any(), any(), any()))
        .thenReturn(operators);

    when(monitorContentTranslationService.translateMonitorContent(any(), any()))
        .thenReturn("translated content");

    mockMvc.perform(post("/api/tenant/{tenantId}/monitors/{monitorId}/agent-config", tenantId, monitorId)
        .content(objectMapper.writeValueAsString(agentConfigRequest))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(content().string("translated content"));

    verify(monitorContentTranslationService).loadOperatorsByAgentTypeAndVersion(
        Map.of(agentConfigRequest.getAgentType(), agentConfigRequest.getAgentVersion())
    );

    verify(monitorContentTranslationService)
        .prepareOperatorsForMonitor(operators, renderedMonitorTemplate.getMonitor().getMonitorType(), renderedMonitorTemplate.getMonitor().getSelectorScope());

    verify(monitorContentTranslationService).translateMonitorContent(operators, renderedMonitorTemplate.getRenderedContent());

    verify(monitorManagement).renderMonitorTemplate(monitorId, resourceId, tenantId);
    verifyNoMoreInteractions(monitorContentTranslationService);
  }
}
