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
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.policy.manage.web.model.MonitorMetadataPolicyDTO;
import com.rackspace.salus.telemetry.model.TargetClassName;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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
  private final PolicyApi policyApi;
  private final MonitorRepository monitorRepository;

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  public MonitorConversionService(ObjectMapper objectMapper, MonitorConversionProperties properties,
      PolicyApi policyApi,
      MonitorRepository monitorRepository) {
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.policyApi = policyApi;
    this.monitorRepository = monitorRepository;
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

  public MonitorCU convertFromInput(String tenantId, UUID monitorId, DetailedMonitorInput input) {

    validateInterval(input.getInterval());

    final MonitorCU monitor = new MonitorCU()
        .setMonitorName(input.getName())
        .setLabelSelector(input.getLabelSelector())
        .setLabelSelectorMethod(input.getLabelSelectorMethod())
        .setResourceId(input.getResourceId())
        .setInterval(input.getInterval());

    if (monitorId != null) {
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
      populateAgentConfigContent(tenantId, input, monitor, plugin);
    } else if (details instanceof RemoteMonitorDetails) {
      final RemoteMonitorDetails remoteMonitorDetails = (RemoteMonitorDetails) details;

      monitor.setSelectorScope(ConfigSelectorScope.REMOTE);
      monitor.setZones(remoteMonitorDetails.getMonitoringZones());

      final RemotePlugin plugin = remoteMonitorDetails.getPlugin();
      populateMonitorType(monitor, plugin);
      populateAgentConfigContent(tenantId, input, monitor, plugin);
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
                                          Object plugin) {
    final ApplicableAgentType applicableAgentType = plugin.getClass()
        .getAnnotation(ApplicableAgentType.class);
    if (applicableAgentType == null) {
      log.warn("pluginClass={} of monitor={} is missing ApplicableAgentType",
          plugin.getClass(), input);
      throw new IllegalStateException("Missing ApplicableAgentType");
    }

    monitor.setAgentType(applicableAgentType.value());
    setMetadataFields(tenantId, monitor, plugin);

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
   * Updates a plugins metadata policy fields in place.
   *
   * Determines which fields of the Monitor may use metadata policies, retrieves the effective
   * policies for the provided tenant, then sets the fields to the policy values.
   *
   * @param tenantId The tenant id the monitor is created under.
   * @param monitor The parent MonitorCU object being constructed.
   * @param plugin The plugin to set metadata values on.
   */
  private void setMetadataFields(String tenantId, MonitorCU monitor, Object plugin) {
    Map<String, MonitorMetadataPolicyDTO> policyMetadata = null;
    List<String> metadataFields;
    TargetClassName className = TargetClassName.getTargetClassName(plugin);

    if (monitor.getPluginMetadataFields() != null && !monitor.getPluginMetadataFields().isEmpty()) {
      policyMetadata = policyApi.getEffectiveMonitorMetadataMap(tenantId, className, monitor.getMonitorType());
      metadataFields = MetadataUtils
          .getMetadataFieldsForUpdate(plugin, monitor.getPluginMetadataFields(), policyMetadata);
    } else {
      metadataFields = MetadataUtils.getMetadataFieldsForCreate(plugin);
    }

    // Store the list of fields that are using metadata policies.
    monitor.setPluginMetadataFields(metadataFields);

    if (metadataFields.isEmpty()) {
      log.debug("No unset metadata fields were found on monitor={}", monitor);
      return;
    }

    if (policyMetadata == null) {
      // this api request is avoided if there are no metadata fields to set
      policyMetadata = policyApi.getEffectiveMonitorMetadataMap(tenantId, className, monitor.getMonitorType());
    }

    log.debug("Setting policy metadata on {} fields for tenant {}", metadataFields.size(), tenantId);
    MetadataUtils.setNewMetadataValues(plugin, metadataFields, policyMetadata);
  }
}
