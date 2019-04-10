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

package com.rackspace.salus.monitor_management.services;;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.web.model.*;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.Monitor;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * This service enables the public API to interface with the backend REST services for monitor management.
 */
@Service
@Slf4j
public class MonitorConversionService {

  private final ObjectMapper objectMapper;

  @Autowired
  public MonitorConversionService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public DetailedMonitorOutput convertToOutput(Monitor monitor) {
    final DetailedMonitorOutput detailedMonitorOutput = new DetailedMonitorOutput()
        .setId(monitor.getId().toString())
        .setName(monitor.getMonitorName())
        .setLabels(monitor.getLabels());

    final AgentType agentType = monitor.getAgentType();
    final ConfigSelectorScope selectorScope = monitor.getSelectorScope();

    // NOTE: this conditional needs to be updated when support for remote monitors is added to backend
    if (selectorScope == ConfigSelectorScope.ALL_OF && agentType == AgentType.TELEGRAF) {
      final LocalMonitorDetails monitorDetails = new LocalMonitorDetails();
      detailedMonitorOutput.setDetails(monitorDetails);

      final LocalPlugin localPlugin;

      try {
        localPlugin = objectMapper
            .readValue(monitor.getContent(), LocalPlugin.class);
      } catch (IOException e) {
        log.warn("Failed to deserialize LocalPlugin for monitor={}", monitor, e);
        throw new IllegalStateException("Failed to deserialize LocalPlugin");
      }

      final ApplicableAgentType applicableAgentType = localPlugin.getClass()
          .getAnnotation(ApplicableAgentType.class);
      if (applicableAgentType == null) {
        log.warn("The deserialized plugin={} from monitor={} was missing ApplicableAgentType", localPlugin, monitor);
        throw new IllegalStateException("Missing ApplicableAgentType");
      }

      if (applicableAgentType.value() != monitor.getAgentType()) {
        log.warn("The deserialized plugin={} has wrong agentType from monitor={}", localPlugin, monitor);
        throw new IllegalStateException("Inconsistent AgentType");
      }

      monitorDetails.setPlugin(localPlugin);
    }

    return detailedMonitorOutput;
  }

  public MonitorCU convertFromInput(DetailedMonitorInput create) {
    final MonitorCU monitor = new MonitorCU()
        .setMonitorName(create.getName())
        .setLabels(create.getLabels());

    final MonitorDetails details = create.getDetails();

    // NOTE: this conditional needs to be updated when support for remote monitors is added to backend
    if (details instanceof LocalMonitorDetails) {
      monitor.setSelectorScope(ConfigSelectorScope.ALL_OF);

      final LocalPlugin plugin = ((LocalMonitorDetails) details).getPlugin();
      final ApplicableAgentType applicableAgentType = plugin.getClass()
          .getAnnotation(ApplicableAgentType.class);
      if (applicableAgentType == null) {
        log.warn("While creating, pluginClass={} of monitor={} is missing ApplicableAgentType",
            plugin.getClass(), create);
        throw new IllegalStateException("Missing ApplicableAgentType");
      }

      monitor.setAgentType(applicableAgentType.value());

      try {
        monitor.setContent(
            objectMapper.writeValueAsString(plugin)
        );
      } catch (JsonProcessingException e) {
        log.warn("While creating, failed to serialize plugin details of monitor={}", create, e);
        throw new IllegalStateException("Failed to serialize plugin details");
      }
    }

    return monitor;
  }
}
