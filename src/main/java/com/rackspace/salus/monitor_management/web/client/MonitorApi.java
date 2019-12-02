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

package com.rackspace.salus.monitor_management.web.client;

import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.telemetry.model.AgentType;
import java.util.List;
import java.util.Map;
import org.springframework.util.MultiValueMap;

public interface MonitorApi {

  List<BoundMonitorDTO> getBoundMonitors(String envoyId,
                                         Map<AgentType, String> installedAgentVersions);

  DetailedMonitorOutput getPolicyMonitorById(String monitorId);

  DetailedMonitorOutput createMonitor(String tenantId, DetailedMonitorInput input, MultiValueMap<String, String> headers);
}
