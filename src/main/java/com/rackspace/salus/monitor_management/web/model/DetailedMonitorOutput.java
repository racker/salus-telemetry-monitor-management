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


import com.fasterxml.jackson.annotation.JsonView;
import com.rackspace.salus.common.web.View;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import io.swagger.annotations.ApiModelProperty;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import lombok.Data;

@Data
public class DetailedMonitorOutput {
    String id;
    String name;
    Map<String,String> labelSelector;
    LabelSelectorMethod labelSelectorMethod;
    String resourceId;
    Set<String> excludedResourceIds;
    Duration interval;
    @ApiModelProperty(value="details", required=true, example="\"details\":{ \"type\": \"local|remote\", \"plugin\":{ \"type\":\"cpu\", \"collectCpuTime\": false, \"percpu\": false, \"reportActive\": false, \"totalcpu\": true} }")
    MonitorDetails details;

    @JsonView(View.Admin.class)
    String policyId;
    String createdTimestamp;
    String updatedTimestamp;

    Map<String,String> summary;
}
