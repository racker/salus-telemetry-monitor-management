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
package com.rackspace.salus.monitor_management.web.controller;

import com.rackspace.salus.monitor_management.entities.Monitor;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePublic;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.monitor_management.errors.ZoneAlreadyExists;
import com.rackspace.salus.monitor_management.entities.Zone;
import com.rackspace.salus.monitor_management.services.ZoneManagement;
import com.rackspace.salus.monitor_management.web.client.ZoneApi;
import com.rackspace.salus.monitor_management.web.model.MonitorDTO;
import com.rackspace.salus.monitor_management.web.model.ZoneCreatePrivate;
import com.rackspace.salus.monitor_management.web.model.ZoneDTO;
import com.rackspace.salus.monitor_management.web.model.ZoneUpdate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
public class ZoneApiController implements ZoneApi {
    private ZoneManagement zoneManagement;

    public ZoneApiController(ZoneManagement zoneManagement) {
        this.zoneManagement = zoneManagement;
    }

    @Override
    @GetMapping("/tenant/{tenantId}/zones/{name}")
    public ZoneDTO getByZoneName(@PathVariable String tenantId, @PathVariable String name) {
        Optional<Zone> zone = zoneManagement.getPrivateZone(tenantId, name);
        return zone.orElseThrow(() -> new NotFoundException(String.format("No zone found for %s on tenant %s",
                name, tenantId)))
                .toDTO();
    }

    @PostMapping("/tenant/{tenantId}/zones")
    @ResponseStatus(HttpStatus.CREATED)
    public ZoneDTO create(@PathVariable String tenantId, @Valid @RequestBody ZoneCreatePrivate zone)
            throws ZoneAlreadyExists {
        return zoneManagement.createPrivateZone(tenantId, zone).toDTO();
    }

    @PostMapping("/admin/zones")
    @ResponseStatus(HttpStatus.CREATED)
    public ZoneDTO create(@Valid @RequestBody ZoneCreatePublic zone)
        throws ZoneAlreadyExists {
        return zoneManagement.createPublicZone(zone).toDTO();
    }

    @PutMapping("/tenant/{tenantId}/zones/{name}")
    public ZoneDTO update(@PathVariable String tenantId, @PathVariable String name, @Valid @RequestBody ZoneUpdate zone) {
        return zoneManagement.updateZone(tenantId, name, zone).toDTO();
    }

    @DeleteMapping("/tenant/{tenantId}/zones/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String tenantId, @PathVariable String name) {
        zoneManagement.removePrivateZone(tenantId, name);
    }

    @DeleteMapping("/admin/zones/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) {
        zoneManagement.removePublicZone(name);
    }

    @GetMapping("/tenant/{tenantId}/zones")
    public List<ZoneDTO> getAvailableZones(@PathVariable String tenantId) {
        return zoneManagement.getAvailableZonesForTenant(tenantId)
                .stream()
                .map(Zone::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/tenant/{tenantId}/monitorsByZone/{zone}")
    public List<MonitorDTO> getMonitorsForZone(@PathVariable String tenantId, @PathVariable String zone) {
        return zoneManagement.getMonitorsForZone(tenantId, zone).stream()
            .map(Monitor::toDTO)
            .collect(Collectors.toList());
    }
}