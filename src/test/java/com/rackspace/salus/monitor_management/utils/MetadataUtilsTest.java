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

package com.rackspace.salus.monitor_management.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.rackspace.salus.monitor_management.web.model.telegraf.Ping;
import com.rackspace.salus.policy.manage.web.model.MonitorMetadataPolicyDTO;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.MetadataValueType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class MetadataUtilsTest {

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Test
  public void getMetadataFieldsForCreate_Ping() {
    assertThat(MetadataUtils.getMetadataFieldsForCreate(new Ping())
        .containsAll(List.of(
            "count",
            "pingInterval",
            "timeout",
            "deadline",
            "interfaceOrAddress"
        )));
  }

  @Test
  public void getMetadataFieldsForUpdate_Ping() {
    Ping ping = new Ping()
        .setCount(3)
        .setTimeout(10)
        .setPingInterval(20);

    Map<String, MonitorMetadataPolicyDTO> policies = Map.of(
        "count", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("count")
            .setValueType(MetadataValueType.INT)
            .setValue("5"),
        "timeout", (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("timeout")
            .setValueType(MetadataValueType.INT)
            .setValue("10")
    );

    // count has a different value than the policy.  even though it was previously using metadata it is now excluded
    // timeout has the same value as the metadata and was previously set using it, so it remains as metadata
    // pingInterval was not previously using metadata so it is still not included in the results
    // deadline is set to null so it will be set to metadata
    // interfaceOrAddress is set to null so it will be set to metadata
    assertThat(MetadataUtils.getMetadataFieldsForUpdate(ping, List.of("count", "timeout"), policies))
        .containsAll(List.of("timeout", "deadline", "interfaceOrAddress"));
  }

  @Test
  public void setNewMetadataValues_monitor() {
    Monitor monitor = new Monitor();
    List<String> metadataFields = List.of("interval");
    Map<String, MonitorMetadataPolicyDTO> policyMetadata = Map.of("interval",
        (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("interval")
            .setValueType(MetadataValueType.DURATION)
            .setValue("12"));

    MetadataUtils.setNewMetadataValues(monitor, metadataFields, policyMetadata);
    assertThat(monitor.getInterval()).isEqualTo(Duration.ofSeconds(12));
  }

  @Test
  public void setNewMetadataValues_monitor_wrongValueType() {
    Monitor monitor = new Monitor();
    List<String> metadataFields = List.of("interval");
    Map<String, MonitorMetadataPolicyDTO> policyMetadata = Map.of("interval",
        (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("interval")
            .setValueType(MetadataValueType.INT)
            .setValue("12"));

    exceptionRule.expect(IllegalArgumentException.class);
    exceptionRule.expectMessage("Can not set java.time.Duration field "
        + "com.rackspace.salus.telemetry.entities.Monitor.interval to java.lang.Integer");

    MetadataUtils.setNewMetadataValues(monitor, metadataFields, policyMetadata);
  }

  @Test
  public void setNewMetadataValues_monitor_noRelevantMetadata() {
    Monitor monitor = new Monitor();
    List<String> metadataFields = List.of("interval");
    Map<String, MonitorMetadataPolicyDTO> policyMetadata = Map.of("timer",
        (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("interval")
            .setValueType(MetadataValueType.DURATION)
            .setValue("12"));

    MetadataUtils.setNewMetadataValues(monitor, metadataFields, policyMetadata);

    assertThat(monitor.getInterval()).isNull();
  }

  @Test
  public void setNewMetadataValues_plugin() {
    Ping plugin = new Ping();
    List<String> metadataFields = List.of("pingInterval");
    Map<String, MonitorMetadataPolicyDTO> policyMetadata = Map.of("pingInterval",
        (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
            .setKey("pingInterval")
            .setValueType(MetadataValueType.INT)
            .setValue("67"));

    MetadataUtils.setNewMetadataValues(plugin, metadataFields, policyMetadata);
    assertThat(plugin.getPingInterval()).isEqualTo(67);
  }

  @Test
  public void setUpdateMetadataValue_plugin() {
    Ping plugin = new Ping();
    MonitorMetadataPolicyDTO policy = (MonitorMetadataPolicyDTO) new MonitorMetadataPolicyDTO()
        .setKey("pingInterval")
        .setValueType(MetadataValueType.INT)
        .setValue("61");

    MetadataUtils.updateMetadataValue(plugin, policy);
    assertThat(plugin.getPingInterval()).isEqualTo(61);
  }
}
