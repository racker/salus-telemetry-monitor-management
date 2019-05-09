package com.rackspace.salus.monitor_management.web.controller;

import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.monitor_management.errors.ZoneAlreadyExists;
import com.rackspace.salus.monitor_management.entities.Zone;
import com.rackspace.salus.monitor_management.services.ZoneManagement;
import com.rackspace.salus.monitor_management.web.client.ZoneApi;
import com.rackspace.salus.monitor_management.web.model.ZoneCreate;
import com.rackspace.salus.monitor_management.web.model.ZoneDTO;
import com.rackspace.salus.monitor_management.web.model.ZoneUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        Optional<Zone> zone = zoneManagement.getZone(tenantId, name);
        return zone.orElseThrow(() -> new NotFoundException(String.format("No zone found for %s on tenant %s",
                name, tenantId)))
                .toDTO();
    }

    @PostMapping("/tenant/{tenantId}/zones")
    @ResponseStatus(HttpStatus.CREATED)
    public ZoneDTO create(@PathVariable String tenantId, @Valid @RequestBody ZoneCreate zone)
            throws ZoneAlreadyExists {
        return zoneManagement.createZone(tenantId, zone).toDTO();
    }

    @PutMapping("/tenant/{tenantId}/zones/{name}")
    public ZoneDTO update(@PathVariable String tenantId, @PathVariable String name, @Valid @RequestBody ZoneUpdate zone) {
        return zoneManagement.updateZone(tenantId, name, zone).toDTO();
    }

    @DeleteMapping("/tenant/{tenantId}/zones/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String tenantId, @PathVariable String name) {
        zoneManagement.removeZone(tenantId, name);
    }

    @GetMapping("/tenant/{tenantId}/zones")
    public List<ZoneDTO> getAvailableZones(@PathVariable String tenantId) {
        return zoneManagement.getAvailableZonesForTenant(tenantId)
                .stream()
                .map(Zone::toDTO)
                .collect(Collectors.toList());
    }
}