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

package com.rackspace.salus.monitor_management.web.model.telegraf;

import static com.rackspace.salus.test.JsonTestUtils.readContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.rackspace.salus.monitor_management.entities.Monitor;
import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@JsonTest
@Import({MonitorConversionService.class})
public class NetConversionTest {
  @Configuration
  public static class TestConfig { }

  // A timestamp to be used in tests that translates to "1970-01-02T03:46:40Z"
  private static final Instant DEFAULT_TIMESTAMP = Instant.ofEpochSecond(100000);

  @Autowired
  MonitorConversionService conversionService;

  @Test
  public void convertFromInput() throws JSONException, IOException {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");

    final Net plugin = new Net()
        .setInterfaces(Arrays.asList("eth*", "enp0s[0-1]"))
        .setIgnoreProtocolStats(true);

    final LocalMonitorDetails details = new LocalMonitorDetails()
        .setPlugin(plugin);

    final DetailedMonitorInput input = new DetailedMonitorInput()
        .setLabelSelector(labels)
        .setDetails(details);
    final MonitorCU result = conversionService.convertFromInput(input);

    assertThat(result).isNotNull();
    assertThat(result.getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(result.getSelectorScope()).isEqualTo(ConfigSelectorScope.LOCAL);
    final String content = readContent("/ConversionTests/net.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

  @Test
  public void convertToOutput() throws IOException {
    Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");

    final String content = readContent("/ConversionTests/net.json");

    final UUID monitorId = UUID.randomUUID();

    Monitor monitor = new Monitor()
        .setId(monitorId)
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.LOCAL)
        .setLabelSelector(labels)
        .setContent(content)
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(monitorId.toString());
    assertThat(result.getDetails()).isInstanceOf(LocalMonitorDetails.class);

    final LocalMonitorDetails monitorDetails = (LocalMonitorDetails) result.getDetails();
    final LocalPlugin plugin = monitorDetails.getPlugin();
    assertThat(plugin).isInstanceOf(Net.class);

    final Net expected = new Net()
        .setInterfaces(Arrays.asList("eth*", "enp0s[0-1]"))
        .setIgnoreProtocolStats(true);
    assertThat(plugin).isEqualTo(expected);
  }

}