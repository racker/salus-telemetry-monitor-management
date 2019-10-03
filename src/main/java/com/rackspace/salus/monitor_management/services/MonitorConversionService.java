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
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.model.ApplicableAgentType;
import com.rackspace.salus.monitor_management.web.model.ApplicableMonitorType;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.MonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RemotePlugin;
import com.rackspace.salus.policy.manage.web.model.MonitorMetadataPolicyDTO;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import java.io.IOException;
import java.io.InvalidClassException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
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
  private final MonitorRepository monitorRepository;
  private final MetadataUtils metadataUtils;

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  public MonitorConversionService(ObjectMapper objectMapper, MonitorConversionProperties properties,
      PolicyApi policyApi,
      MonitorRepository monitorRepository,
      MetadataUtils metadataUtils) {
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.monitorRepository = monitorRepository;
    this.metadataUtils = metadataUtils;
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

  public MonitorCU convertFromInput(String tenantId, UUID monitorId,
      DetailedMonitorInput input) {
    return convertFromInput(tenantId, monitorId, input, false);
  }

  public MonitorCU convertFromInput(String tenantId, UUID monitorId,
                                    DetailedMonitorInput input, boolean patchOperation) {

    validateInterval(input.getInterval());

    final MonitorCU monitor = new MonitorCU()
        .setMonitorName(input.getName())
        .setLabelSelector(input.getLabelSelector())
        .setLabelSelectorMethod(input.getLabelSelectorMethod())
        .setResourceId(input.getResourceId())
        .setInterval(input.getInterval());

    // Policy monitors should not use metadata
    if (!tenantId.equals(Monitor.POLICY_TENANT) && monitorId != null) {
      // Get the previous metadata used
      // This will be used in setMetadataFields to help identify which values need to be replaced
      monitorRepository.findById(monitorId).ifPresent(m ->
        monitor.setPluginMetadataFields(m.getPluginMetadataFields()));
    }

    final MonitorDetails details = input.getDetails();

    // these will evaluate false when details is null during an update
    if (details instanceof LocalMonitorDetails) {
      monitor.setSelectorScope(ConfigSelectorScope.LOCAL);

      final LocalPlugin plugin = ((LocalMonitorDetails) details).getPlugin();
      populateMonitorType(monitor, plugin);
      populateAgentConfigContent(tenantId, input, monitor, plugin, patchOperation);
    } else if (details instanceof RemoteMonitorDetails) {
      final RemoteMonitorDetails remoteMonitorDetails = (RemoteMonitorDetails) details;

      monitor.setSelectorScope(ConfigSelectorScope.REMOTE);
      monitor.setZones(remoteMonitorDetails.getMonitoringZones());

      final RemotePlugin plugin = remoteMonitorDetails.getPlugin();
      populateMonitorType(monitor, plugin);
      populateAgentConfigContent(tenantId, input, monitor, plugin, patchOperation);
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

  private void populateMonitorType(MonitorCU monitor, Object plugin) {
    final ApplicableMonitorType applicableMonitorType = plugin.getClass()
        .getAnnotation(ApplicableMonitorType.class);
    if (applicableMonitorType == null) {
      log.warn("monitorClass={} is missing ApplicableMonitorType", plugin.getClass());
      throw new IllegalStateException("Missing ApplicableMonitorType");
    }
    monitor.setMonitorType(applicableMonitorType.value());
  }

  private void populateAgentConfigContent(String tenantId, DetailedMonitorInput input, MonitorCU monitor,
                                          Object plugin, boolean patchOperation) {
    final ApplicableAgentType applicableAgentType = plugin.getClass()
        .getAnnotation(ApplicableAgentType.class);
    if (applicableAgentType == null) {
      log.warn("pluginClass={} of monitor={} is missing ApplicableAgentType",
          plugin.getClass(), input);
      throw new IllegalStateException("Missing ApplicableAgentType");
    }

    monitor.setAgentType(applicableAgentType.value());

    // Policy monitors should not use metadata
    if (!tenantId.equals(Monitor.POLICY_TENANT)) {
      metadataUtils.setMetadataFieldsForPlugin(tenantId, monitor, plugin, patchOperation);
    }

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

  /**
   * Gets the content field from a monitor, modifies it based on the new value
   * in the provided policy, and then returns the new content string.
   *
   * @param monitor The monitor whose content should be updated.
   * @param policy The policy whose value should be input into the monitor content.
   * @return The new monitor content containing the policy value.
   * @throws InvalidClassException If the plugin's class could not be handled.
   * @throws JsonProcessingException If the plugin cannot be converted back into a string.
   */
  public String updateMonitorContentWithPolicy(Monitor monitor, MonitorMetadataPolicyDTO policy)
      throws InvalidClassException, JsonProcessingException {
    DetailedMonitorInput input = new DetailedMonitorInput(convertToOutput(monitor));
    MonitorDetails details = input.getDetails();

    Object plugin;
    if (details instanceof LocalMonitorDetails) {
      plugin = ((LocalMonitorDetails) details).getPlugin();
      MetadataUtils.updateMetadataValue(plugin, policy);
    } else if (details instanceof RemoteMonitorDetails) {
      plugin = ((RemoteMonitorDetails) details).getPlugin();
      MetadataUtils.updateMetadataValue(plugin, policy);
    } else {
      throw new InvalidClassException(String.format(
          "Unexpected class found when extracting monitor plugin content=%s",
          monitor.getContent()));
    }

    return objectMapper.writeValueAsString(plugin);
  }
}
