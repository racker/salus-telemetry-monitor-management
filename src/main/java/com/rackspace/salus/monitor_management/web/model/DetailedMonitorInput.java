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

import com.rackspace.salus.monitor_management.web.model.validator.ValidCreateMonitor;
import com.rackspace.salus.monitor_management.web.model.validator.ValidUpdateMonitor;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import com.rackspace.salus.telemetry.model.ValidLabelKeys;
import io.swagger.annotations.ApiModelProperty;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@ValidCreateMonitor(groups = {ValidationGroups.Create.class, ValidationGroups.Patch.class})
@ValidUpdateMonitor(groups = {ValidationGroups.Update.class})
public class DetailedMonitorInput {
  String name;

  /**
   * This key-value mapping of labels specifies what resources will be monitored by this monitor.
   * For a resource to be selected, it must contain at least all of the labels given here.
   */
  @ValidLabelKeys
  Map<String,String> labelSelector;

  LabelSelectorMethod labelSelectorMethod;

  String resourceId;

  /**
   * Resource IDs specified will be excluded from binding of this monitor. This can be used in
   * combination with more specific monitors that select the resource IDs excluded here.
   */
  Set<String> excludedResourceIds;

  Duration interval;

  @ApiModelProperty(value="details", required=true, example="\"details\":{ \"type\": \"local|remote\",\"plugin\":{ \"type\":\"cpu\", \"collectCpuTime\": false, \"percpu\": false,\"reportActive\": false, \"totalcpu\": true}}")
  @NotNull(groups = {ValidationGroups.Create.class, ValidationGroups.Patch.class})
  @Valid
  MonitorDetails details;

  public DetailedMonitorInput(DetailedMonitorOutput output) {
    this.name = output.getName();
    this.labelSelector = output.getLabelSelector();
    this.labelSelectorMethod = output.getLabelSelectorMethod();
    this.resourceId = output.getResourceId();
    this.excludedResourceIds = output.getExcludedResourceIds();
    this.interval = output.getInterval();
    this.details = output.getDetails();
  }
}
