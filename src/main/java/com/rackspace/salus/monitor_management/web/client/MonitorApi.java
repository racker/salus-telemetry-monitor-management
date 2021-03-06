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

package com.rackspace.salus.monitor_management.web.client;

import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.TestMonitorInput;
import com.rackspace.salus.monitor_management.web.model.TestMonitorResult;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import java.util.List;
import java.util.Map;
import org.springframework.util.MultiValueMap;

public interface MonitorApi {

  List<BoundMonitorDTO> getBoundMonitors(String envoyId,
                                         Map<AgentType, String> installedAgentVersions);

  DetailedMonitorOutput getMonitorTemplateById(String monitorId);

  DetailedMonitorOutput createMonitor(String tenantId, DetailedMonitorInput input, MultiValueMap<String, String> headers);

  TestMonitorResult performTestMonitor(String tenantId, TestMonitorInput input);

  String translateMonitorContent(AgentType agentType, String agentVersion,
                                 MonitorType monitorType,
                                 ConfigSelectorScope scope,
                                 String content);
}
