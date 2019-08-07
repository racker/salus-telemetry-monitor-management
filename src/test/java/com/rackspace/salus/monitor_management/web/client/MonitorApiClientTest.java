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

package com.rackspace.salus.monitor_management.web.client;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.telemetry.model.PagedContent;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
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

  private PodamFactory podamFactory = new PodamFactoryImpl();

  @Autowired
  MockRestServiceServer mockServer;

  @Autowired
  MonitorApiClient monitorApiClient;

  @Autowired
  ObjectMapper objectMapper;

  @Test
  public void getBoundMonitors() throws JsonProcessingException {

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
    final PagedContent<BoundMonitorDTO> resultPage = PagedContent.fromPage(
        new PageImpl<>(givenBoundMonitors, Pageable.unpaged(), givenBoundMonitors.size()));

    mockServer.expect(requestTo("/api/admin/bound-monitors/e-1?size=2147483647"))
        .andRespond(withSuccess(objectMapper.writeValueAsString(resultPage), MediaType.APPLICATION_JSON));

    final List<BoundMonitorDTO> boundMonitors = monitorApiClient.getBoundMonitors("e-1");
    assertThat(boundMonitors, equalTo(givenBoundMonitors));
  }

  @Test
  public void testGetPolicyMonitor() throws JsonProcessingException {
    DetailedMonitorOutput monitor = podamFactory.manufacturePojo(DetailedMonitorOutput.class);

    mockServer.expect(requestTo(String.format("/api/admin/policy-monitors/%s", monitor.getId())))
        .andRespond(withSuccess(objectMapper.writeValueAsString(monitor), MediaType.APPLICATION_JSON));

    DetailedMonitorOutput result = monitorApiClient.getPolicyMonitorById(monitor.getId());
    assertThat(result, equalTo(monitor));
  }

  @Test
  public void testGetPolicyMonitor_doesntExist() throws JsonProcessingException {
    mockServer.expect(requestTo("/api/admin/policy-monitors/id"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    DetailedMonitorOutput result = monitorApiClient.getPolicyMonitorById("id");
    assertThat(result, nullValue());
  }
}