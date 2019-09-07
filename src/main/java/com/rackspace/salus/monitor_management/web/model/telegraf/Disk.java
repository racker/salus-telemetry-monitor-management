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

package com.rackspace.salus.monitor_management.web.model.telegraf;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rackspace.salus.monitor_management.web.model.ApplicableAgentType;
import com.rackspace.salus.monitor_management.web.model.ApplicableMonitorType;
import com.rackspace.salus.monitor_management.web.model.Plugin;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.MonitorType;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true)
@ApplicableAgentType(AgentType.TELEGRAF)
@ApplicableMonitorType(MonitorType.disk)
public class Disk extends Plugin {
  List<String> mountPoints;
  @JsonProperty(defaultValue = "[\"tmpfs\", \"devtmpfs\", \"devfs\", \"iso9660\", \"overlay\", \"aufs\", \"squashfs\"]")
  List<String> ignoreFs = Arrays.asList("tmpfs", "devtmpfs", "devfs", "iso9660", "overlay", "aufs", "squashfs");
}
