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

import com.rackspace.salus.telemetry.model.MonitorType;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApiModel(parent=MonitorDetails.class)
@Data @EqualsAndHashCode(callSuper = false)
public class LocalMonitorDetails extends MonitorDetails {
  @NotNull @Valid
  LocalPlugin plugin;

  public MonitorType getType() {
    final ApplicableMonitorType applicableMonitorType = plugin.getClass()
        .getAnnotation(ApplicableMonitorType.class);
    if (applicableMonitorType == null) {
      log.warn("monitorClass={} is missing ApplicableMonitorType", plugin.getClass());
      throw new IllegalStateException("Missing ApplicableMonitorType");
    }
    return applicableMonitorType.value();
  }
}
