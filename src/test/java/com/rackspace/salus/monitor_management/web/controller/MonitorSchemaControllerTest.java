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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rackspace.salus.monitor_management.services.SchemaService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = MonitorSchemaController.class)
@Import({SchemaService.class})
public class MonitorSchemaControllerTest {
  @Autowired
  MockMvc mockMvc;

  @Test
  public void testGetMonitorPluginsSchema() throws Exception {
    final String expectedSubset = readContent(
        "MonitorSchemaControllerTest/monitor_plugins_schema_partial.json");

    mockMvc.perform(get(
        "/schema/monitor-plugins",
        "t-1"
    ).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(
            content().json(expectedSubset, false));

  }

  @Test
  public void testGetMonitorsSchema() throws Exception {
    final String expectedSubset = readContent(
        "MonitorSchemaControllerTest/monitors_schema_partial.json");

    mockMvc.perform(get(
        "/schema/monitors",
        "t-1"
    ).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(
            content().json(expectedSubset, false));

  }

}