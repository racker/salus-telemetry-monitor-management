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

import com.rackspace.salus.monitor_management.web.model.validator.ValidCidrList;
import com.rackspace.salus.telemetry.model.ZoneState;
import java.util.List;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.io.Serializable;

@Data
public class ZoneUpdate implements Serializable {

    String provider;

    String providerRegion;

    @ValidCidrList
    List<String> sourceIpAddresses;

    ZoneState state;

    @Min(value = 30L, message = "The timeout must not be less than 30s")
    @Max(value = 1800L, message = "The timeout must not be more than 1800s (30m)")
    Long pollerTimeout;
}
