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

import static com.rackspace.salus.common.util.SpringResourceUtils.readContent;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rackspace.salus.monitor_management.services.TestMonitorService;
import com.rackspace.salus.monitor_management.web.model.TestMonitorResult;
import com.rackspace.salus.monitor_management.web.model.TestMonitorResult.TestMonitorResultData;
import com.rackspace.salus.telemetry.model.SimpleNameTagValueMetric;
import com.rackspace.salus.telemetry.repositories.TenantMetadataRepository;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@RunWith(SpringRunner.class)
@WebMvcTest(TestMonitorApiController.class)
public class TestMonitorApiControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private TestMonitorService testMonitorService;

  @MockBean
  TenantMetadataRepository tenantMetadataRepository;

  @Test
  public void testPerformTestMonitor_success() throws Exception {

    when(testMonitorService.performTestMonitorOnResource(any(), any(), any(), any()))
        .thenReturn(completedFuture(
            new TestMonitorResult()
                .setErrors(List.of())
                .setData(new TestMonitorResultData().setMetrics(
                    List.of(
                        new SimpleNameTagValueMetric()
                            .setName("cpu")
                            .setTags(Map.of("cpu", "cpu1"))
                            .setFvalues(Map.of("usage", 1.45))
                    )
                ))
        ));

    final MvcResult mvcResult = mvc.perform(
        post("/api/tenant/t-1/test-monitor")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"resourceId\": \"r-1\", \"details\": {\"type\": \"local\", \"plugin\": {\"type\": \"cpu\"}}}")
    )
        .andExpect(request().asyncStarted())
        .andReturn();

    final String expectedRespJson = readContent(
        "TestMonitorApiControllerTest/resp_no_errors.json");

    mvc.perform(asyncDispatch(mvcResult))
        .andExpect(status().isOk())
        .andExpect(content().json(expectedRespJson, true));

  }

  @Test
  public void testPerformTestMonitor_partialError() throws Exception {

    when(testMonitorService.performTestMonitorOnResource(any(), any(), any(), any()))
        .thenReturn(completedFuture(
            new TestMonitorResult()
                // include an error
                .setErrors(List.of("error-1"))
                .setData(new TestMonitorResultData().setMetrics(
                    List.of(
                        new SimpleNameTagValueMetric()
                            .setName("cpu")
                            .setTags(Map.of("cpu", "cpu1"))
                            .setFvalues(Map.of("usage", 1.45))
                    )
                ))

        ));

    final MvcResult mvcResult = mvc.perform(
        post("/api/tenant/t-1/test-monitor")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"resourceId\": \"r-1\", \"details\": {\"type\": \"local\", \"plugin\": {\"type\": \"cpu\"}}}")
    )
        .andExpect(request().asyncStarted())
        .andReturn();

    final String expectedRespJson = readContent(
        "TestMonitorApiControllerTest/resp_with_errors.json");

    mvc.perform(asyncDispatch(mvcResult))
        // due to 1 or more errors in response
        .andExpect(status().isPartialContent())
        .andExpect(content().json(expectedRespJson, true));

  }

  @Test
  public void testPerformTestMonitor_onlyErrors() throws Exception {

    when(testMonitorService.performTestMonitorOnResource(any(), any(), any(), any()))
        .thenReturn(completedFuture(
            new TestMonitorResult()
                .setErrors(List.of("timed out"))
        ));

    final MvcResult mvcResult = mvc.perform(
        post("/api/tenant/t-1/test-monitor")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"resourceId\": \"r-1\", \"details\": {\"type\": \"local\", \"plugin\": {\"type\": \"cpu\"}}}")
    )
        .andExpect(request().asyncStarted())
        .andReturn();

    final String expectedRespJson = readContent("TestMonitorApiControllerTest/resp_timeout.json");

    mvc.perform(asyncDispatch(mvcResult))
        // due to errors and no metrics in response
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().json(expectedRespJson, true));

  }
}