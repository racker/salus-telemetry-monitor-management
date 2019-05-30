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
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import java.util.UUID;
import lombok.Data;

/**
 * Conveys the binding of a monitor to a resource and for remote monitors,
 * where <code>selectorScope</code> is {@link ConfigSelectorScope#REMOTE},
 * it also conveys the binding to a zone.
 */
@Data
public class BoundMonitorDTO {
  UUID monitorId;
  @JsonInclude(Include.NON_EMPTY)
  String zoneName;
  String resourceTenant;
  String resourceId;
  ConfigSelectorScope selectorScope;
  AgentType agentType;
  String renderedContent;
  String envoyId;
}
