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

package com.rackspace.salus.monitor_management.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.config.MonitorConversionProperties;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.monitor_management.web.model.ApplicableAgentType;
import com.rackspace.salus.monitor_management.web.model.ApplicableMonitorType;
import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.monitor_management.web.model.MonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.RemotePlugin;
import com.rackspace.salus.monitor_management.web.model.SummaryField;
import com.rackspace.salus.monitor_management.web.model.ValidationGroups;
import com.rackspace.salus.policy.manage.web.model.MonitorMetadataPolicyDTO;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import java.io.IOException;
import java.io.InvalidClassException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.json.JsonException;
import javax.json.JsonPatch;
import javax.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
  private final PatchHelper patchHelper;

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  public MonitorConversionService(ObjectMapper objectMapper, MonitorConversionProperties properties,
      MonitorRepository monitorRepository,
      MetadataUtils metadataUtils,
      PatchHelper patchHelper) {
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.monitorRepository = monitorRepository;
    this.metadataUtils = metadataUtils;
    this.patchHelper = patchHelper;
  }

  /**
   * Converts a monitor object to an equivalent {@link DetailedMonitorInput}
   *
   * @param monitor The monitor to convert.
   * @return The corresponding input object.
   */
  private DetailedMonitorInput convertToInput(Monitor monitor) {
    final DetailedMonitorInput detailedMonitorInput = new DetailedMonitorInput()
        .setName(monitor.getMonitorName())
        .setInterval(monitor.getInterval())
        .setLabelSelectorMethod(monitor.getLabelSelectorMethod())
        .setResourceId(monitor.getResourceId())
        .setExcludedResourceIds(monitor.getExcludedResourceIds())
        .setDetails(convertContentToDetails(monitor));

    if (StringUtils.isNotBlank(monitor.getResourceId())) {
      detailedMonitorInput.setLabelSelector(null);
    } else {
      detailedMonitorInput.setLabelSelector(monitor.getLabelSelector());
    }

    return detailedMonitorInput;
  }

  public DetailedMonitorOutput convertToOutput(Monitor monitor) {
    final DetailedMonitorOutput detailedMonitorOutput = new DetailedMonitorOutput()
        .setId(monitor.getId().toString())
        .setName(monitor.getMonitorName())
        .setLabelSelectorMethod(monitor.getLabelSelectorMethod())
        .setResourceId(monitor.getResourceId())
        .setExcludedResourceIds(monitor.getExcludedResourceIds())
        .setInterval(monitor.getInterval())
        .setDetails(convertContentToDetails(monitor))
        .setPolicy(monitor.getPolicyId() != null)
        .setCreatedTimestamp(DateTimeFormatter.ISO_INSTANT.format(monitor.getCreatedTimestamp()))
        .setUpdatedTimestamp(DateTimeFormatter.ISO_INSTANT.format(monitor.getUpdatedTimestamp()));

    // ElementCollections return an empty collection if it was previously set to null
    // We want this to display null to a customer instead of {}.
    // This helps keep all API responses consistent.
    if (StringUtils.isNotBlank(monitor.getResourceId())) {
      detailedMonitorOutput.setLabelSelector(null);
    } else {
      detailedMonitorOutput.setLabelSelector(monitor.getLabelSelector());
    }

    detailedMonitorOutput.setSummary(buildSummaryFromDetails(detailedMonitorOutput.getDetails()));

    return detailedMonitorOutput;
  }

  private Map<String, String> buildSummaryFromDetails(MonitorDetails details) {
    if (details instanceof LocalMonitorDetails) {
      return buildSummaryFromPlugin(((LocalMonitorDetails) details).getPlugin());
    } else if (details instanceof RemoteMonitorDetails) {
      return buildSummaryFromPlugin(((RemoteMonitorDetails) details).getPlugin());
    } {
      throw new IllegalStateException(String.format("Unexpected MonitorDetails type: %s", details.getClass()));
    }
  }

  private Map<String, String> buildSummaryFromPlugin(Object plugin) {
    final Map<String, String> summary = new HashMap<>();

    final Class<?> pluginClass = plugin.getClass();

    for (Field field : pluginClass.getDeclaredFields()) {
      if (field.isAnnotationPresent(SummaryField.class)) {
        final String fieldName = field.getName();

        field.setAccessible(true);

        try {
          final Object value = field.get(plugin);
          summary.put(fieldName, value != null ? value.toString() : null);
        } catch (IllegalAccessException e) {
          log.warn("Unable to read summary field {} from {}", fieldName, plugin);
        }
      }
    }

    return summary;
  }

  /**
   * Helper method for updates to perform a patch.
   *
   * @param tenantId  The tenant the monitor relates to.
   * @param monitorId The id of the monitor if an update is being performed.
   * @param monitor   The existing monitor object that will be updated
   * @param patch     The individual changes to be made to the Monitor
   * @return A MonitorCU object used to construct a Monitor
   */
  public MonitorCU convertFromPatchInput(String tenantId, UUID monitorId,
                                        Monitor monitor, JsonPatch patch)
      throws IllegalArgumentException {
    // To apply a patch we must convert the existing monitor to a DetailedMonitorInput
    // then write any new values we've received to that.
    DetailedMonitorInput input = convertToInput(monitor);

    DetailedMonitorInput patchedInput;
    //noinspection TryWithIdenticalCatches
    try {
      patchedInput = patchHelper.patch(
          patch,
          input,
          DetailedMonitorInput.class,
          ValidationGroups.Patch.class);
    } catch (ConstraintViolationException e) {
      throw new IllegalArgumentException(e.getMessage());
    } catch (JsonException e) {
      // This can occur when the PATCH "test" operation fails or if an invalid value is provided.
      // A 422 is more accurate for the "test" failure, but there is not a way to distinguish
      // between them other than the message contents, so we'll stick with a 400.
      throw new IllegalArgumentException(e.getMessage());
    }

    return convertFromInput(tenantId, monitorId, patchedInput, true);
  }

  /**
   * Helper method for updates that defaults to a non-patch update.
   *
   * @param tenantId  The tenant the monitor relates to.
   * @param monitorId The id of the monitor if an update is being performed.
   * @param input     The details provided to set on the Monitor
   *
   * @return A MonitorCU object used to construct a Monitor
   */
  public MonitorCU convertFromInput(String tenantId, UUID monitorId,
      DetailedMonitorInput input) {
    return convertFromInput(tenantId, monitorId, input, false);
  }

  /**
   * Generates a monitor's string content from an input plugin object.
   *
   * @param tenantId       The tenant the monitor relates to.
   * @param monitorId      The id of the monitor if an update is being performed.
   * @param input          The details provided to set on the Monitor
   * @param patchOperation Whether a patch or update/put is being performed. True for patch.
   *                       null values are ignored for updates but utilized in patches.
   *
   * @return A MonitorCU object used to construct a Monitor
   */
  public MonitorCU convertFromInput(String tenantId, UUID monitorId,
                                    DetailedMonitorInput input, boolean patchOperation) {

    validateInterval(input.getInterval());

    final MonitorCU monitor = new MonitorCU()
        .setMonitorName(input.getName())
        .setLabelSelector(input.getLabelSelector())
        .setLabelSelectorMethod(input.getLabelSelectorMethod())
        .setResourceId(input.getResourceId())
        .setExcludedResourceIds(input.getExcludedResourceIds())
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
    metadataUtils.setMetadataFieldsForPlugin(tenantId, monitor, plugin, patchOperation);

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

  private MonitorDetails convertContentToDetails(Monitor monitor) {
    final ConfigSelectorScope selectorScope = monitor.getSelectorScope();

    if (selectorScope == ConfigSelectorScope.LOCAL) {
      final LocalMonitorDetails monitorDetails = new LocalMonitorDetails();
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
      return monitorDetails;

    } else if (selectorScope == ConfigSelectorScope.REMOTE) {
      final RemoteMonitorDetails monitorDetails = new RemoteMonitorDetails();

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
      return monitorDetails;
    } else {
      // this will only ever get hit by tests that do not provide the full monitor info
      return null;
    }
  }

  public Object getPluginFromMonitor(Monitor monitor) throws InvalidClassException {
    DetailedMonitorInput input = new DetailedMonitorInput(convertToOutput(monitor));
    MonitorDetails details = input.getDetails();

    Object plugin;
    if (details instanceof LocalMonitorDetails) {
      plugin = ((LocalMonitorDetails) details).getPlugin();
    } else if (details instanceof RemoteMonitorDetails) {
      plugin = ((RemoteMonitorDetails) details).getPlugin();
    } else {
      throw new InvalidClassException(String.format(
          "Unexpected class found when extracting monitor plugin content=%s",
          monitor.getContent()));
    }

    return plugin;
  }

  public void refreshClonedPlugin(String tenantId, Monitor monitor) {
    Object plugin;
    try {
      plugin = getPluginFromMonitor(monitor);
    } catch (InvalidClassException e) {
      log.error("Failed to refresh cloned plugin for monitor={}", monitor, e);
      return;
    }

    metadataUtils.setMetadataFieldsForClonedPlugin(tenantId, monitor, plugin);
    try {
      monitor.setContent(objectMapper.writeValueAsString(plugin));
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize plugin details of monitor={}", monitor, e);
      throw new IllegalStateException("Failed to serialize plugin details");
    }
  }

  public BoundMonitorDTO convertToBoundMonitorDTO(BoundMonitor boundMonitor) {
    final BoundMonitorDTO dto = new BoundMonitorDTO(boundMonitor);
    final DetailedMonitorOutput detailedMonitorOutput = convertToOutput(boundMonitor.getMonitor());
    dto.setMonitorSummary(buildSummaryFromDetails(detailedMonitorOutput.getDetails()));
    return dto;
  }
}
