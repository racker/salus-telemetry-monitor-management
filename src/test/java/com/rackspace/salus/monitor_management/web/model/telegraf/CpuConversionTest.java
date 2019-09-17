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

import static com.rackspace.salus.monitor_management.web.model.telegraf.ConversionHelpers.assertCommon;
import static com.rackspace.salus.monitor_management.web.model.telegraf.ConversionHelpers.createMonitor;
import static com.rackspace.salus.test.JsonTestUtils.readContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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
public class CpuConversionTest {
  @Configuration
  public static class TestConfig { }

  @MockBean
  PolicyApi policyApi;

  @MockBean
  MonitorRepository monitorRepository;

  @Autowired
  MonitorConversionService conversionService;

  @Autowired
  MetadataUtils metadataUtils;

  @Test
  public void convertToOutput_cpu() throws IOException {
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_cpu.json");

    Monitor monitor = createMonitor(content, "convertToOutput", AgentType.TELEGRAF,
        ConfigSelectorScope.LOCAL
    );

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    final Cpu cpuPlugin = assertCommon(result, monitor, Cpu.class, "convertToOutput");

    assertThat(cpuPlugin.isCollectCpuTime()).isTrue();
    assertThat(cpuPlugin.isPercpu()).isFalse();
    assertThat(cpuPlugin.isReportActive()).isFalse();
    assertThat(cpuPlugin.isTotalcpu()).isTrue();
  }

  @Test
  public void convertToOutput_cpu_defaults() {
    final String content = "{\"type\": \"cpu\"}";

    Monitor monitor = createMonitor(content, "convertToOutput_defaults", AgentType.TELEGRAF,
        ConfigSelectorScope.LOCAL
    );

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    final Cpu cpuPlugin = assertCommon(result, monitor, Cpu.class, "convertToOutput_defaults");

    assertThat(cpuPlugin.isCollectCpuTime()).isFalse();
    assertThat(cpuPlugin.isPercpu()).isFalse();
    assertThat(cpuPlugin.isReportActive()).isFalse();
    assertThat(cpuPlugin.isTotalcpu()).isTrue();
  }

  @Test
  public void convertFromInput_cpu() throws JSONException, IOException {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertFromInput");

    final LocalMonitorDetails details = new LocalMonitorDetails();
    final Cpu plugin = new Cpu();
    plugin.setPercpu(false);
    plugin.setCollectCpuTime(true);
    plugin.setTotalcpu(true);
    plugin.setReportActive(false);
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
    assertThat(result.getSelectorScope()).isEqualTo(ConfigSelectorScope.LOCAL);
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_cpu.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

}
