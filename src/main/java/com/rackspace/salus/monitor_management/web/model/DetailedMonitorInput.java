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

import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(discriminator="details", subTypes={LocalMonitorDetails.class, RemoteMonitorDetails.class})
public class DetailedMonitorInput {
  String name;

  /**
   * This key-value mapping of labels specifies what resources will be monitored by this monitor.
   * For a resource to be selected, it must contain at least all of the labels given here.
   */
  @NotEmpty
  Map<String,String> labelSelector;

  @ApiModelProperty(value="details", required=true, example="\"details\":{ \"type\": \"local|remote\",\"plugin\":{ \"type\":\"cpu\", \"collectCpuTime\": false, \"percpu\": false,\"reportActive\": false, \"totalcpu\": true}}")
  @NotNull @Valid
  MonitorDetails details;
}
