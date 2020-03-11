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

package com.rackspace.salus.monitor_management.web.model.telegraf;

import static org.assertj.core.api.Assertions.assertThat;

import com.rackspace.salus.common.util.SpringResourceUtils;
import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RemotePlugin;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@JsonTest
@Import({MonitorConversionService.class, MetadataUtils.class})
public class SmtpConversionTest {
  @Configuration
  public static class TestConfig { }

  @MockBean
  PatchHelper patchHelper;

  @MockBean
  PolicyApi policyApi;

  @MockBean
  MonitorRepository monitorRepository;

  @Autowired
  MonitorConversionService conversionService;

  @Autowired
  MetadataUtils metadataUtils;

  @Test
  public void convertToOutput() throws IOException {
    Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertToOutput");

    final String content = SpringResourceUtils.readContent(
        "/ConversionTests/MonitorConversionServiceTest_smtp.json");
    final UUID monitorId = UUID.randomUUID();

    Monitor monitor = new Monitor()
        .setId(monitorId)
        .setMonitorName("name-a")
        .setAgentType(AgentType.TELEGRAF)
        .setSelectorScope(ConfigSelectorScope.REMOTE)
        .setLabelSelector(labels)
        .setContent(content)
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH);

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(monitorId.toString());
    assertThat(result.getName()).isEqualTo("name-a");
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getDetails()).isInstanceOf(RemoteMonitorDetails.class);

    final RemoteMonitorDetails localMonitorDetails = (RemoteMonitorDetails) result.getDetails();
    final RemotePlugin plugin = localMonitorDetails.getPlugin();
    assertThat(plugin).isInstanceOf(Smtp.class);

    final Smtp smtpPlugin = (Smtp) plugin;
    assertThat(smtpPlugin.getHost()).isEqualTo("localhost");
    assertThat(smtpPlugin.getPort()).isEqualTo(25);
    assertThat(smtpPlugin.getTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(smtpPlugin.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(smtpPlugin.getEhlo()).isEqualTo("example.com");
    assertThat(smtpPlugin.getFrom()).isEqualTo("me@example.com");
    assertThat(smtpPlugin.getTo()).isEqualTo("you@example.com");
    assertThat(smtpPlugin.getBody()).isEqualTo("test body");
    assertThat(smtpPlugin.getStarttls()).isEqualTo(true);
    assertThat(smtpPlugin.getTlsCa()).isEqualTo("/etc/telegraf/ca.pem");
    assertThat(smtpPlugin.getTlsCert()).isEqualTo("/etc/telegraf/cert.pem");
    assertThat(smtpPlugin.getTlsKey()).isEqualTo("/etc/telegraf/key.pem");
    assertThat(smtpPlugin.getInsecureSkipVerify()).isEqualTo(false);
  }

  @Test
  public void convertFromInput() throws JSONException, IOException {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertFromInput_http");

    final RemoteMonitorDetails details = new RemoteMonitorDetails();
    final Smtp plugin = new Smtp();
    plugin.setHost("localhost");
    plugin.setPort(25);
    plugin.setTimeout(Duration.ofSeconds(5));
    plugin.setReadTimeout(Duration.ofSeconds(30));
    plugin.setEhlo("example.com");
    plugin.setFrom("me@example.com");
    plugin.setTo("you@example.com");
    plugin.setBody("test body");
    plugin.setStarttls(true);
    plugin.setTlsCa("/etc/telegraf/ca.pem");
    plugin.setTlsCert("/etc/telegraf/cert.pem");
    plugin.setTlsKey("/etc/telegraf/key.pem");
    plugin.setInsecureSkipVerify(false);
    details.setPlugin(plugin);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setName("name-a")
        .setLabelSelector(labels)
        .setDetails(details);
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);

    assertThat(result).isNotNull();
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getAgentType()).isEqualTo(AgentType.TELEGRAF);
    assertThat(result.getMonitorName()).isEqualTo("name-a");
    assertThat(result.getSelectorScope()).isEqualTo(ConfigSelectorScope.REMOTE);
    final String content = SpringResourceUtils.readContent(
        "/ConversionTests/MonitorConversionServiceTest_smtp.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }
}
