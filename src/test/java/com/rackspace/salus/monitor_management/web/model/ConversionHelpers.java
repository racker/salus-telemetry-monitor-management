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

package com.rackspace.salus.monitor_management.web.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ConversionHelpers {

  // A timestamp to be used in tests that translates to "1970-01-02T03:46:40Z"
  private static final Instant DEFAULT_TIMESTAMP = Instant.ofEpochSecond(100000);

  public static <T> T assertCommon(DetailedMonitorOutput result,
                                   Monitor monitor, Class<T> pluginClass, String scenario,
                                   Map<String, Object> summary) {
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(monitor.getId().toString());
    assertThat(result.getName()).isEqualTo(scenario);
    assertThat(result.getLabelSelector()).isEqualTo(monitor.getLabelSelector());
    assertThat(result.getDetails()).isInstanceOf(LocalMonitorDetails.class);
    assertThat(result.getSummary()).isEqualTo(summary);

    final LocalPlugin plugin = ((LocalMonitorDetails) result.getDetails()).getPlugin();
    assertThat(plugin).isInstanceOf(pluginClass);
    //noinspection unchecked
    return ((T) plugin);
  }

  public static <T> T assertCommonRemote(DetailedMonitorOutput result,
                                         Monitor monitor, Class<T> pluginClass, String scenario,
                                         Map<String, Object> summary) {
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(monitor.getId().toString());
    assertThat(result.getName()).isEqualTo(scenario);
    assertThat(result.getLabelSelector()).isEqualTo(monitor.getLabelSelector());
    assertThat(result.getDetails()).isInstanceOf(RemoteMonitorDetails.class);
    assertThat(result.getSummary()).isEqualTo(summary);

    final RemotePlugin plugin = ((RemoteMonitorDetails) result.getDetails()).getPlugin();
    assertThat(plugin).isInstanceOf(pluginClass);
    //noinspection unchecked
    return ((T) plugin);
  }

  public static Monitor createMonitor(String content, String scenario, AgentType agentType,
                               ConfigSelectorScope scope) {
    Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", scenario);

    final UUID monitorId = UUID.randomUUID();

    return new Monitor()
        .setId(monitorId)
        .setMonitorName(scenario)
        .setAgentType(agentType)
        .setSelectorScope(scope)
        .setLabelSelector(labels)
        .setContent(content)
        .setCreatedTimestamp(DEFAULT_TIMESTAMP)
        .setUpdatedTimestamp(DEFAULT_TIMESTAMP);
  }

  /**
   * Since {@link Map#of(Object, Object)} doesn't allow for null values we need a helper method
   * to create expected summary objects for default plugin cases.
   */
  public static Map<String,Object> nullableSummaryValue(String key) {
    final Map<String,Object> summary = new HashMap<>(1);
    summary.put(key, null);
    return summary;
  }
}
