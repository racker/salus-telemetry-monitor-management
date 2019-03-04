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

import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import lombok.Data;


// This annotation doesn't work with podam
//import javax.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.NotBlank;

import java.io.Serializable;
import java.util.Map;
import com.rackspace.salus.telemetry.model.AgentType;

import javax.validation.constraints.NotNull;

@Data
public class MonitorCreate implements Serializable {

    String monitorName;

    Map<String,String> labels;

    @NotBlank
    String content;

    @NotNull
    AgentType agentType;

    String targetTenant;

    ConfigSelectorScope selectorScope = ConfigSelectorScope.ALL_OF;

}
