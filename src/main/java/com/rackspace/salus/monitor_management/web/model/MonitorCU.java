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

import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.Data;

// This annotation doesn't work with podam
//import javax.validation.constraints.NotBlank;


/**
 * The CU in MonitorCU stand for Create and Update from the standard CRUD acronym.
 * We are not necessarily happy with this name so we are up for suggestions.
 *
 * This Object is meant for handling Creation and Update events for Monitors.
 * Update events shouldn't be allowed to update the AgentType or the ConfigSelectorScope.
 */
@Data
public class MonitorCU implements Serializable {

    String monitorName;

    Map<String,String> labelSelector;

    LabelSelectorMethod labelSelectorMethod;

    String content;

    AgentType agentType;

    ConfigSelectorScope selectorScope = ConfigSelectorScope.LOCAL;

    List<String> zones;

    String resourceId;

    Duration interval;
}
