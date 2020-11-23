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

package com.rackspace.salus.monitor_management.web.client;

import static com.rackspace.salus.common.util.SpringResourceUtils.readContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.TestMonitorInput;
import com.rackspace.salus.monitor_management.web.model.TestMonitorResult;
import com.rackspace.salus.monitor_management.web.model.TranslateMonitorContentRequest;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.repositories.TenantMetadataRepository;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@RestClientTest
public class MonitorApiClientTest {

  @TestConfiguration
  public static class ExtraTestConfig {

    @Bean
    public MonitorApiClient monitorApiClient(RestTemplateBuilder restTemplateBuilder) {
      return new MonitorApiClient(restTemplateBuilder.build());
    }
  }

  private final PodamFactory podamFactory = new PodamFactoryImpl();

  @Autowired
  MockRestServiceServer mockServer;

  @Autowired
  MonitorApiClient monitorApiClient;

  @Autowired
  ObjectMapper objectMapper;

  @MockBean
  TenantMetadataRepository tenantMetadataRepository;

  @Test
  public void testQueryBoundMonitors() throws IOException {

    final UUID id1 = UUID.fromString("16caf730-48e8-47ba-0001-aa9babba8953");
    final UUID id2 = UUID.fromString("16caf730-48e8-47ba-0002-aa9babba8953");
    final UUID id3 = UUID.fromString("16caf730-48e8-47ba-0003-aa9babba8953");

    final List<BoundMonitorDTO> givenBoundMonitors = Arrays.asList(
        new BoundMonitorDTO()
            .setMonitorId(id1)
            .setRenderedContent("{\"instance\":1, \"state\":1}"),
        new BoundMonitorDTO()
            .setMonitorId(id2)
            .setRenderedContent("{\"instance\":2, \"state\":1}"),
        new BoundMonitorDTO()
            .setMonitorId(id3)
            .setRenderedContent("{\"instance\":3, \"state\":1}")
    );

    final String expectedReqJson = readContent(
        "MonitorApiClientTest/testQueryBoundMonitors_req.json");

    mockServer.expect(requestTo("/api/admin/bound-monitors"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json(expectedReqJson, true))
        .andRespond(withSuccess(objectMapper.writeValueAsString(givenBoundMonitors),
            MediaType.APPLICATION_JSON));

    final Map<AgentType, String> installedAgentVersions = Map.of(AgentType.TELEGRAF, "1.12.0");
    final List<BoundMonitorDTO> boundMonitors =
        monitorApiClient.getBoundMonitors("e-1", installedAgentVersions);
    assertThat(boundMonitors).isEqualTo(givenBoundMonitors);
  }

  @Test
  public void testGetMonitorTemplate() throws JsonProcessingException {
    DetailedMonitorOutput monitor = podamFactory.manufacturePojo(DetailedMonitorOutput.class);

    mockServer.expect(requestTo(String.format("/api/admin/monitor-templates/%s", monitor.getId())))
        .andRespond(
            withSuccess(objectMapper.writeValueAsString(monitor), MediaType.APPLICATION_JSON));

    DetailedMonitorOutput result = monitorApiClient.getMonitorTemplateById(monitor.getId());
    assertThat(result).isEqualTo(monitor);
  }

  @Test
  public void testGetMonitorTemplate_doesntExist()  {
    mockServer.expect(requestTo("/api/admin/monitor-templates/id"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    DetailedMonitorOutput result = monitorApiClient.getMonitorTemplateById("id");
    assertThat(result).isNull();
  }

  @Test
  public void testPerformTestMonitor() throws IOException {
    TestMonitorInput testMonitorInput = objectMapper
        .readValue(readContent("MonitorApiClientTest/testPerformTestMonitor_req.json"),
            TestMonitorInput.class);

    String tenantId = RandomStringUtils.randomAlphabetic(8);
    TestMonitorResult testMonitorResult = podamFactory.manufacturePojo(TestMonitorResult.class);

    mockServer.expect(requestToUriTemplate("/api/tenant/{tenantId}/test-monitor", tenantId))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json(objectMapper.writeValueAsString(testMonitorInput)))
        .andRespond(
            withSuccess(objectMapper.writeValueAsString(testMonitorResult),
                MediaType.APPLICATION_JSON));

    TestMonitorResult testMonitorResultActual = monitorApiClient
        .performTestMonitor(tenantId, testMonitorInput);
    assertThat(testMonitorResult).isEqualTo(testMonitorResultActual);
  }

  @Test
  public void testTranslateMonitorContent() throws JsonProcessingException {

    final TranslateMonitorContentRequest request = new TranslateMonitorContentRequest()
        .setAgentType(AgentType.TELEGRAF)
        .setAgentVersion("1.13.2")
        .setMonitorType(MonitorType.cpu)
        .setScope(ConfigSelectorScope.LOCAL)
        .setContent("original content");

    mockServer.expect(requestTo("/api/admin/translate-monitor-content"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json(objectMapper.writeValueAsString(request)))
        .andRespond(withSuccess("converted content", MediaType.TEXT_PLAIN));

    final String result = monitorApiClient
        .translateMonitorContent(AgentType.TELEGRAF, "1.13.2",
            MonitorType.cpu, ConfigSelectorScope.LOCAL,
            "original content");

    assertThat(result).isEqualTo("converted content");
  }
}