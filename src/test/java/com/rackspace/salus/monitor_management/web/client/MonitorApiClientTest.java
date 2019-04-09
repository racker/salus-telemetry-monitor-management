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
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

@RunWith(SpringRunner.class)
@RestClientTest
public class MonitorApiClientTest {

  @Autowired
  MockRestServiceServer mockServer;

  @Autowired
  RestTemplateBuilder restTemplateBuilder;

  @Autowired
  ObjectMapper objectMapper;

  private RestTemplate restTempate;
  private MonitorApiClient monitorApiClient;

  @Before
  public void setUp() throws Exception {
    restTempate = restTemplateBuilder.build();
    monitorApiClient = new MonitorApiClient(restTempate);
  }

  @Test
  public void getBoundMonitors() throws JsonProcessingException {

    final UUID id1 = UUID.fromString("16caf730-48e8-47ba-0001-aa9babba8953");
    final UUID id2 = UUID.fromString("16caf730-48e8-47ba-0002-aa9babba8953");
    final UUID id3 = UUID.fromString("16caf730-48e8-47ba-0003-aa9babba8953");

    final List<BoundMonitor> givenBoundMonitors = Arrays.asList(
        new BoundMonitor()
            .setMonitorId(id1)
            .setRenderedContent("{\"instance\":1, \"state\":1}"),
        new BoundMonitor()
            .setMonitorId(id2)
            .setRenderedContent("{\"instance\":2, \"state\":1}"),
        new BoundMonitor()
            .setMonitorId(id3)
            .setRenderedContent("{\"instance\":3, \"state\":1}")
    );

    mockServer.expect(requestTo("/api/boundMonitors/e-1"))
        .andRespond(withSuccess(objectMapper.writeValueAsString(givenBoundMonitors), MediaType.APPLICATION_JSON));

    final List<BoundMonitor> boundMonitors = monitorApiClient.getBoundMonitors("e-1");
    assertThat(boundMonitors, equalTo(givenBoundMonitors));
  }
}