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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rackspace.salus.common.util.SpringResourceUtils;
import com.rackspace.salus.monitor_management.entities.MonitorTranslationOperator;
import com.rackspace.salus.monitor_management.services.MonitorContentTranslationService;
import com.rackspace.salus.monitor_management.web.model.MonitorTranslationOperatorCreate;
import com.rackspace.salus.monitor_management.web.model.translators.RenameFieldTranslator;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = MonitorTranslationApiController.class)
public class MonitorTranslationApiControllerTest {

  private static final String FROM_FIELD = "from-field";
  private static final String TO_FIELD = "to-field";

  @Autowired
  MockMvc mockMvc;

  @MockBean
  MonitorContentTranslationService monitorContentTranslationService;

  @Test
  public void testGetAll() throws Exception {
    List<MonitorTranslationOperator> content = List.of(
        buildOperator(
            ">= 1.12.0", MonitorType.cpu, ConfigSelectorScope.LOCAL,
            UUID.fromString("00000001-ffcf-40d2-8684-95cb0362ae6d")
        ),
        buildOperator(
            "< 1.12.0", MonitorType.http_response,
            ConfigSelectorScope.REMOTE,
            UUID.fromString("00000002-ffcf-40d2-8684-95cb0362ae6d")
        )
    );
    Page<MonitorTranslationOperator> page = new PageImpl<>(content, PageRequest.of(1, 2), 10);
    when(monitorContentTranslationService.getAll(any()))
        .thenReturn(page);

    final String respJson = SpringResourceUtils
        .readContent("MonitorTranslationApiControllerTest/getAll_resp.json");

    mockMvc
        .perform(
            get("/api/admin/monitor-translations")
                .accept(MediaType.APPLICATION_JSON)
                .param("page", "1")
                .param("size", "2")
        )
        .andExpect(status().isOk())
        .andExpect(content().json(respJson, true));

    verify(monitorContentTranslationService).getAll(argThat(pageable -> {
      assertThat(pageable.getPageSize()).isEqualTo(2);
      assertThat(pageable.getPageNumber()).isEqualTo(1);
      return true;
    }));
  }

  @Test
  public void testGetById() throws Exception {
    final UUID id = UUID.fromString("00000001-ffcf-40d2-8684-95cb0362ae6d");

    MonitorTranslationOperator operator =
        buildOperator(
            ">= 1.12.0", MonitorType.cpu, ConfigSelectorScope.LOCAL, id
        );

    when(monitorContentTranslationService.getById(any()))
        .thenReturn(operator);

    final String respJson = SpringResourceUtils
        .readContent("MonitorTranslationApiControllerTest/getById_resp.json");

    mockMvc
        .perform(
            get("/api/admin/monitor-translations/{id}", id.toString())
                .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andExpect(content().json(respJson, true));

    verify(monitorContentTranslationService).getById(id);
  }

  @Test
  public void testGetById_notFound() throws Exception {
    final UUID id = UUID.fromString("00000001-ffcf-40d2-8684-95cb0362ae6d");

    when(monitorContentTranslationService.getById(any()))
        .thenThrow(new NotFoundException("not found"));

    mockMvc
        .perform(
            get("/api/admin/monitor-translations/{id}", id.toString())
                .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isNotFound());

    verify(monitorContentTranslationService).getById(id);
  }

  @Test
  public void testCreate() throws Exception {
    final UUID id = UUID.fromString("00000001-ffcf-40d2-8684-95cb0362ae6d");

    MonitorTranslationOperator operator =
        buildOperator(
            ">= 1.12.0", MonitorType.cpu, ConfigSelectorScope.LOCAL, id
        );

    when(monitorContentTranslationService.create(any()))
        .thenReturn(operator);

    final String reqJson = SpringResourceUtils
        .readContent("MonitorTranslationApiControllerTest/create_req.json");
    final String respJson = SpringResourceUtils
        .readContent("MonitorTranslationApiControllerTest/create_resp.json");

    mockMvc
        .perform(
            post("/api/admin/monitor-translations")
                .content(reqJson)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isCreated())
        .andExpect(content().json(respJson, true));

    verify(monitorContentTranslationService).create(
        new MonitorTranslationOperatorCreate()
        .setAgentType(AgentType.TELEGRAF)
        .setAgentVersions(">= 1.12.0")
        .setMonitorType(MonitorType.cpu)
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setTranslatorSpec(
            new RenameFieldTranslator()
            .setFrom(FROM_FIELD)
            .setTo(TO_FIELD)
        )
    );
  }

  @Test
  public void testDelete() throws Exception {
    final UUID id = UUID.fromString("00000001-ffcf-40d2-8684-95cb0362ae6d");

    mockMvc
        .perform(delete("/api/admin/monitor-translations/{id}", id.toString()))
        .andExpect(status().isNoContent());

    verify(monitorContentTranslationService).delete(id);
  }

  @Test
  public void testDelete_notFound() throws Exception {
    final UUID id = UUID.fromString("00000001-ffcf-40d2-8684-95cb0362ae6d");

    doThrow(new NotFoundException("not found"))
        .when(monitorContentTranslationService).delete(any());

    mockMvc
        .perform(delete("/api/admin/monitor-translations/{id}", id.toString()))
        .andExpect(status().isNotFound());

    verify(monitorContentTranslationService).delete(id);
  }

  private MonitorTranslationOperator buildOperator(String agentVersions,
                                                   MonitorType monitorType,
                                                   ConfigSelectorScope selectorScope, UUID id) {
    return new MonitorTranslationOperator()
        .setAgentType(AgentType.TELEGRAF)
        .setAgentVersions(agentVersions)
        .setMonitorType(monitorType)
        .setSelectorScope(selectorScope)
        .setId(id)
        .setTranslatorSpec(new RenameFieldTranslator()
            .setFrom(FROM_FIELD)
            .setTo(TO_FIELD)
        );
  }
}