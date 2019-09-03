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

package com.rackspace.salus.monitor_management.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.config.MonitorConversionProperties;
import com.rackspace.salus.monitor_management.web.model.ApplicableAgentType;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.MonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RemotePlugin;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * This service enables the public API to interface with the backend REST services for monitor management.
 */
@Service
@Slf4j
@EnableConfigurationProperties(MonitorConversionProperties.class)
public class MonitorConversionService {

  private final ObjectMapper objectMapper;
  private final MonitorConversionProperties properties;

  @Autowired
  public MonitorConversionService(ObjectMapper objectMapper, MonitorConversionProperties properties) {
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  public DetailedMonitorOutput convertToOutput(Monitor monitor) {
    final DetailedMonitorOutput detailedMonitorOutput = new DetailedMonitorOutput()
        .setId(monitor.getId().toString())
        .setName(monitor.getMonitorName())
        .setLabelSelector(monitor.getLabelSelector())
        .setLabelSelectorMethod(monitor.getLabelSelectorMethod())
        .setResourceId(monitor.getResourceId())
        .setInterval(monitor.getInterval())
        .setCreatedTimestamp(DateTimeFormatter.ISO_INSTANT.format(monitor.getCreatedTimestamp()))
        .setUpdatedTimestamp(DateTimeFormatter.ISO_INSTANT.format(monitor.getUpdatedTimestamp()));

    final ConfigSelectorScope selectorScope = monitor.getSelectorScope();

    if (selectorScope == ConfigSelectorScope.LOCAL) {
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

      assertPluginAgentType(monitor, localPlugin);

      monitorDetails.setPlugin(localPlugin);
    } else if (selectorScope == ConfigSelectorScope.REMOTE) {
      final RemoteMonitorDetails monitorDetails = new RemoteMonitorDetails();
      detailedMonitorOutput.setDetails(monitorDetails);

      monitorDetails.setMonitoringZones(monitor.getZones());

      final RemotePlugin remotePlugin;

      try {
        remotePlugin = objectMapper.readValue(monitor.getContent(), RemotePlugin.class);
      } catch (IOException e) {
        log.warn("Failed to deserialize RemotePlugin for monitor={}", monitor, e);
        throw new IllegalStateException("Failed to deserialize RemotePlugin");
      }

      assertPluginAgentType(monitor, remotePlugin);

      monitorDetails.setPlugin(remotePlugin);
    }

    return detailedMonitorOutput;
  }

  public MonitorCU convertFromInput(DetailedMonitorInput input) {

    validateInterval(input.getInterval());

    final MonitorCU monitor = new MonitorCU()
        .setMonitorName(input.getName())
        .setMonitorType(input.getDetails().getType())
        .setLabelSelector(input.getLabelSelector())
        .setLabelSelectorMethod(input.getLabelSelectorMethod())
        .setResourceId(input.getResourceId())
        .setInterval(input.getInterval());

    final MonitorDetails details = input.getDetails();

    // these will evaluate false when details is null during an update
    if (details instanceof LocalMonitorDetails) {
      monitor.setSelectorScope(ConfigSelectorScope.LOCAL);

      final LocalPlugin plugin = ((LocalMonitorDetails) details).getPlugin();
      populateAgentConfigContent(input, monitor, plugin);
    } else if (details instanceof RemoteMonitorDetails) {
      final RemoteMonitorDetails remoteMonitorDetails = (RemoteMonitorDetails) details;

      monitor.setSelectorScope(ConfigSelectorScope.REMOTE);
      monitor.setZones(remoteMonitorDetails.getMonitoringZones());

      final RemotePlugin plugin = remoteMonitorDetails.getPlugin();
      populateAgentConfigContent(input, monitor, plugin);
    }

    // Set an appropriate default interval based on the type, if an interval wasn't provided by the user
    if (monitor.getInterval() == null) {
      monitor.setInterval(
          monitor.getSelectorScope() == ConfigSelectorScope.LOCAL ?
              properties.getDefaultLocalInterval() :
              properties.getDefaultRemoteInterval()
      );
    }

    return monitor;
  }

  private void validateInterval(Duration interval) {
    final Duration minInterval = properties.getMinimumAllowedInterval();
    if (interval != null && interval.compareTo(minInterval) < 0) {
      throw new IllegalArgumentException(
          String.format("Interval cannot be less than %s", minInterval));
    }
  }

  private void populateAgentConfigContent(DetailedMonitorInput input, MonitorCU monitor,
                                          Object plugin) {
    final ApplicableAgentType applicableAgentType = plugin.getClass()
        .getAnnotation(ApplicableAgentType.class);
    if (applicableAgentType == null) {
      log.warn("pluginClass={} of monitor={} is missing ApplicableAgentType",
          plugin.getClass(), input);
      throw new IllegalStateException("Missing ApplicableAgentType");
    }

    monitor.setAgentType(applicableAgentType.value());

    try {
      monitor.setContent(
          objectMapper.writeValueAsString(plugin)
      );
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize plugin details of monitor={}", input, e);
      throw new IllegalStateException("Failed to serialize plugin details");
    }
  }

  private void assertPluginAgentType(Monitor monitor, Object plugin) {
    final ApplicableAgentType applicableAgentType = plugin.getClass()
        .getAnnotation(ApplicableAgentType.class);
    if (applicableAgentType == null) {
      log.warn("The deserialized plugin={} from monitor={} was missing ApplicableAgentType", plugin, monitor);
      throw new IllegalStateException("Missing ApplicableAgentType");
    }

    if (applicableAgentType.value() != monitor.getAgentType()) {
      log.warn("The deserialized plugin={} has wrong agentType from monitor={}", plugin, monitor);
      throw new IllegalStateException("Inconsistent AgentType");
    }
  }
}
