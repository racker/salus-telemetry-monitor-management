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

package com.rackspace.salus.monitor_management.web.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonView;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.common.web.View;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Conveys the binding of a monitor to a resource and for remote monitors,
 * where <code>selectorScope</code> is {@link ConfigSelectorScope#REMOTE},
 * it also conveys the binding to a zone.
 */
@Data
@NoArgsConstructor
public class BoundMonitorDTO {

  UUID monitorId;
  MonitorType monitorType;
  @JsonView(View.Admin.class)
  String tenantId;
  @JsonInclude(Include.NON_EMPTY)
  String zoneName;
  String resourceId;
  Duration interval;
  ConfigSelectorScope selectorScope;
  AgentType agentType;
  String renderedContent;
  String envoyId;
  String createdTimestamp;
  String updatedTimestamp;

  public BoundMonitorDTO(BoundMonitor boundMonitor) {
    this.monitorId = boundMonitor.getMonitor().getId();
    this.monitorType = boundMonitor.getMonitor().getMonitorType();
    this.tenantId = boundMonitor.getTenantId();
    this.zoneName = boundMonitor.getZoneName();
    this.resourceId = boundMonitor.getResourceId();
    this.interval = boundMonitor.getMonitor().getInterval();
    this.selectorScope = boundMonitor.getMonitor().getSelectorScope();
    this.agentType = boundMonitor.getMonitor().getAgentType();
    this.renderedContent = boundMonitor.getRenderedContent();
    this.envoyId = boundMonitor.getEnvoyId();
    this.createdTimestamp = DateTimeFormatter.ISO_INSTANT.format(boundMonitor.getCreatedTimestamp());
    this.updatedTimestamp = DateTimeFormatter.ISO_INSTANT.format(boundMonitor.getUpdatedTimestamp());
  }
}
