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

import com.rackspace.salus.policy.manage.web.model.MonitorMetadataPolicyDTO;
import com.rackspace.salus.telemetry.model.MetadataField;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetadataUtils {

  public static List<String> getMetadataFieldsForCreate(Object object) {
    List<String> unsetMetadataFields = new ArrayList<>();
    for (Field f : object.getClass().getDeclaredFields()) {
      try {
        f.setAccessible(true); // since these fields are private, we must set this first
        if (f.getAnnotationsByType(MetadataField.class).length > 0 && f.get(object) == null) {
          unsetMetadataFields.add(f.getName());
        }
      } catch(IllegalAccessException|IllegalArgumentException e) {
        log.warn("Failed to get value of field={} from object={}, e", f.getName(), object, e);
      }
    }
    return unsetMetadataFields;
  }

  public static List<String> getMetadataFieldsForUpdate(Object object, List<String> previousMetadataFields, Map<String, MonitorMetadataPolicyDTO> policies) {
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

  public static void setNewMetadataValues(Object object, List<String> metadataFields, Map<String, MonitorMetadataPolicyDTO> policyMetadata) {
    for (String key : metadataFields) {
      if (policyMetadata.containsKey(key)) {
        MonitorMetadataPolicyDTO policy = policyMetadata.get(key);
        updateMetadataValue(object, policy);
      }
    }
  }

  public static void updateMetadataValue(Object object, MonitorMetadataPolicyDTO policy) {
    try {
      Field f = object.getClass().getDeclaredField(policy.getKey());
      f.setAccessible(true);
      switch (policy.getValueType()) {
        case STRING:
          f.set(object, policy.getValue());
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
}
