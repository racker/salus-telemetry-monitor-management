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

import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.policy.manage.web.model.MonitorMetadataPolicyDTO;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.MetadataField;
import com.rackspace.salus.telemetry.model.TargetClassName;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MetadataUtils {

  private final PolicyApi policyApi;

  public MetadataUtils(PolicyApi policyApi) {
    this.policyApi = policyApi;
  }

  /**
   * Gets all fields that have MetadataField annotation and are set to null.
   *
   * @param object The object to inspect for metadata fields.
   * @return The list of metadata fields found.
   */
  static List<String> getMetadataFieldsForCreate(Object object) {
    List<String> metadataFields = new ArrayList<>();
    for (Field f : object.getClass().getDeclaredFields()) {
      try {
        f.setAccessible(true); // since these fields are private, we must set this first
        if (f.getAnnotationsByType(MetadataField.class).length > 0 && f.get(object) == null) {
          metadataFields.add(f.getName());
        }
      } catch(IllegalAccessException|IllegalArgumentException e) {
        log.warn("Failed to get value of field={} from object={}, e", f.getName(), object, e);
      }
    }
    return metadataFields;
  }

  /**
   * Gets all fields that have MetadataField annotation and are set to null
   * and all fields that have the MetadataField annotation, were previously using the metadata value
   * and still have the same value as the corresponding policy.
   *
   * @param object The object to inspect for metadata fields.
   * @param previousMetadataFields A list of fields that were previously using metadata policy values.
   * @param policies A map of relevant policies.
   * @return The list of metadata fields found.
   */
  @SuppressWarnings("unchecked")
  static List<String> getMetadataFieldsForUpdate(Object object, List<String> previousMetadataFields, Map<String, MonitorMetadataPolicyDTO> policies) {
    List<String> metadataFields = new ArrayList<>();
    for (Field f : object.getClass().getDeclaredFields()) {
      try {
        f.setAccessible(true); // since these fields are private, we must set this first
        if (f.getAnnotationsByType(MetadataField.class).length > 0) {
          if (f.get(object) == null) {
            metadataFields.add(f.getName());
          } else if (previousMetadataFields.contains(f.getName()) && policies.containsKey(f.getName())) {
            // if the object was previously using metadata for this field and the value still
            // matches the value in the policy, assume the field should still be using policies.
            MonitorMetadataPolicyDTO policy = policies.get(f.getName());
            switch (policy.getValueType()) {
              case STRING:
                String stringValue = (String) f.get(object);
                if (stringValue.equals(policy.getValue())) {
                  metadataFields.add(f.getName());
                }
                break;
              case STRING_LIST:
                List<String> listValue = (List<String>) f.get(object);
                List<String> policyValue = Arrays.asList(policy.getValue().split("\\s*,\\s*"));
                if (listValue.equals(policyValue)) {
                  metadataFields.add(f.getName());
                }
                break;
              case INT:
                int intValue = (int) f.get(object);
                if (intValue == Integer.parseInt(policy.getValue())) {
                  metadataFields.add(f.getName());
                }
                break;
              case DURATION:
                Duration durationValue = (Duration) f.get(object);
                if (durationValue.getSeconds() == Long.parseLong(policy.getValue())) {
                  metadataFields.add(f.getName());
                }
                break;
              default:
                log.warn("Failed to handle policy with valueType={}", policy.getValueType());
            }
          }
        }
      } catch(IllegalAccessException|IllegalArgumentException e) {
        log.warn("Failed to get value of field={} from object={}, e", f.getName(), object, e);
      }
    }
    return metadataFields;
  }

  /**
   * Updates the object in place, setting policy metadata values to the relevant metadata fields.
   *
   * @param object The object to update.
   * @param metadataFields The metadata fields that should attempt to be updated.
   * @param policyMetadata The relevant policies for this object.
   */
  static void setNewMetadataValues(Object object, List<String> metadataFields, Map<String, MonitorMetadataPolicyDTO> policyMetadata) {
    for (String key : metadataFields) {
      if (policyMetadata.containsKey(key)) {
        MonitorMetadataPolicyDTO policy = policyMetadata.get(key);
        updateMetadataValue(object, policy);
      }
    }
  }

  /**
   * Updates a single field on an object to the value in the policy.
   * @param object The object to update.
   * @param policy The policy corresponding to the field that will be updated.
   */
  public static void updateMetadataValue(Object object, MonitorMetadataPolicyDTO policy) {
    try {
      Field f = object.getClass().getDeclaredField(policy.getKey());
      f.setAccessible(true);
      switch (policy.getValueType()) {
        case STRING:
          f.set(object, policy.getValue());
          break;
        case STRING_LIST:
          List<String> listValue = Arrays.asList(policy.getValue().split("\\s*,\\s*"));
          f.set(object, new ArrayList<>(listValue));
          break;
        case INT:
          f.set(object, Integer.parseInt(policy.getValue()));
          break;
        case DURATION:
          f.set(object, Duration.ofSeconds(Long.parseLong(policy.getValue())));
          break;
      }
    } catch (IllegalAccessException|NoSuchFieldException e) {
      log.warn("Failed to set policy metadata on field {}, {}", policy.getKey(), e);
    }
  }

  /**
   * Updates a monitors metadata policy fields in place.
   *
   * Determines which fields of the Monitor may use metadata policies, retrieves the effective
   * policies for the provided tenant, then sets the fields to the policy values.
   *
   * NOTE: This method is essentially the same as setMetadataFieldsForPlugin but must use
   * some different methods due to object differences.  If this method is changed
   * setMetadataFieldsForPlugin must be updated similarly.
   *
   * @param tenantId The tenant id the monitor is created under.
   * @param monitor The monitor object to update.
   */
  public void setMetadataFieldsForMonitor(String tenantId, Monitor monitor, boolean patchOperation) {
    Map<String, MonitorMetadataPolicyDTO> policyMetadata = null;
    List<String> metadataFields;
    TargetClassName className = TargetClassName.getTargetClassName(monitor);

    if (!patchOperation &&
        monitor.getMonitorMetadataFields() != null &&
        !monitor.getMonitorMetadataFields().isEmpty()) {
      policyMetadata = policyApi.getEffectiveMonitorMetadataMap(tenantId, className, monitor.getMonitorType());
      metadataFields = MetadataUtils
          .getMetadataFieldsForUpdate(monitor, monitor.getMonitorMetadataFields(), policyMetadata);
    } else {
      metadataFields = MetadataUtils.getMetadataFieldsForCreate(monitor);
    }

    // Store the list of fields that are using metadata policies.
    monitor.setMonitorMetadataFields(metadataFields);

    if (metadataFields.isEmpty()) {
      log.debug("No unset metadata fields were found on monitor={}", monitor);
      return;
    }

    if (policyMetadata == null) {
      // this api request is avoided if there are no metadata fields to set
      policyMetadata = policyApi.getEffectiveMonitorMetadataMap(tenantId, className, monitor.getMonitorType());
    }

    log.debug("Setting policy metadata on {} fields for tenant {}", metadataFields.size(), tenantId);
    MetadataUtils.setNewMetadataValues(monitor, metadataFields, policyMetadata);
  }

  /**
   * Updates a plugins metadata policy fields in place.
   *
   * Determines which fields of the Monitor may use metadata policies, retrieves the effective
   * policies for the provided tenant, then sets the fields to the policy values.
   *
   * NOTE: This method is essentially the same as setMetadataFieldsForMonitor but must use
   * some different methods due to object differences.  If this method is changed
   * setMetadataFieldsForMonitor must be updated similarly.
   *
   * @param tenantId The tenant id the monitor is created under.
   * @param monitor The parent MonitorCU object being constructed.
   * @param plugin The plugin to set metadata values on.
   */
  public void setMetadataFieldsForPlugin(String tenantId, MonitorCU monitor, Object plugin, boolean patchOperation) {
    Map<String, MonitorMetadataPolicyDTO> policyMetadata = null;
    List<String> metadataFields;
    TargetClassName className = TargetClassName.getTargetClassName(plugin);

    if (!patchOperation &&
        monitor.getPluginMetadataFields() != null &&
        !monitor.getPluginMetadataFields().isEmpty()) {
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