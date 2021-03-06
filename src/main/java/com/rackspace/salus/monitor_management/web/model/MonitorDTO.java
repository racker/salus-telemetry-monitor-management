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

import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@NoArgsConstructor
public class MonitorDTO {
  UUID id;

  String monitorName;

  Map<String,String> labelSelector;

  LabelSelectorMethod labelSelectorMethod;

  String tenantId;

  String content;

  AgentType agentType;

  ConfigSelectorScope selectorScope;

  List<String> zones;

  String resourceId;

  Set<String> excludedResourceIds;

  Map<String,String> metadata;

  String createdTimestamp;

  String updatedTimestamp;

  public MonitorDTO(Monitor monitor) {
    this.id = monitor.getId();
    this.monitorName = monitor.getMonitorName();

    // ElementCollections return an empty collection if it was previously set to null
    // We want this to display null to a customer instead of {}.
    // This helps keep all API responses consistent.
    if (StringUtils.isNotBlank(monitor.getResourceId())) {
      this.labelSelector = null;
    } else {
      this.labelSelector = monitor.getLabelSelector();
    }

    this.labelSelectorMethod = monitor.getLabelSelectorMethod();
    this.resourceId = monitor.getResourceId();
    this.excludedResourceIds = monitor.getExcludedResourceIds();
    this.tenantId = monitor.getTenantId();
    this.content = monitor.getContent();
    this.agentType = monitor.getAgentType();
    this.selectorScope = monitor.getSelectorScope();
    this.zones = monitor.getZones();
    this.metadata = monitor.getMetadata();
    this.createdTimestamp = DateTimeFormatter.ISO_INSTANT.format(monitor.getCreatedTimestamp());
    this.updatedTimestamp = DateTimeFormatter.ISO_INSTANT.format(monitor.getUpdatedTimestamp());
  }
}
