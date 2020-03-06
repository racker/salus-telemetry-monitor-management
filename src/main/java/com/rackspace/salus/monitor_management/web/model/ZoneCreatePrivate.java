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

import com.rackspace.salus.monitor_management.web.validator.ValidCidrList;
import com.rackspace.salus.telemetry.etcd.types.PrivateZoneName;
import com.rackspace.salus.telemetry.model.ZoneState;
import java.io.Serializable;
import java.util.List;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

@Data
public class ZoneCreatePrivate implements Serializable {

    @NotBlank
    @Pattern(regexp = "^[a-z0-9_]+$", message = "Only lowercase alphanumeric and underscore characters can be used")
    @PrivateZoneName
    String name;

    String provider;

    String providerRegion;

    @ValidCidrList
    List<String> sourceIpAddresses;

    ZoneState state = ZoneState.ACTIVE; // Can we do active for private, inactive for public?

    @Min(value = 30L, message = "The timeout must not be less than 30s")
    @Max(value = 1800L, message = "The timeout must not be more than 1800s (30m)")
    Long pollerTimeout = 120L;
}