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

import com.fasterxml.jackson.annotation.JsonView;
import com.rackspace.salus.telemetry.entities.Zone;
import com.rackspace.salus.common.web.View;
import com.rackspace.salus.telemetry.model.ZoneState;
import java.time.format.DateTimeFormatter;
import lombok.Data;
import java.util.List;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ZoneDTO {
    String name;
    long pollerTimeout;
    String provider;
    String providerRegion;
    boolean isPublic;
    List<String> sourceIpAddresses;
    String createdTimestamp;
    String updatedTimestamp;

    @JsonView(View.Internal.class)
    ZoneState state;

    public ZoneDTO(Zone zone) {
        this.name = zone.getName();
        this.pollerTimeout = zone.getPollerTimeout().getSeconds();
        this.provider = zone.getProvider();
        this.providerRegion = zone.getProviderRegion();
        this.isPublic = zone.isPublic();
        this.sourceIpAddresses = zone.getSourceIpAddresses();
        this.state = zone.getState();
        this.createdTimestamp = DateTimeFormatter.ISO_INSTANT.format(zone.getCreatedTimestamp());
        this.updatedTimestamp = DateTimeFormatter.ISO_INSTANT.format(zone.getUpdatedTimestamp());
    }
}
