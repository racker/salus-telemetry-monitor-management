package com.rackspace.salus.monitor_management.services;

import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.monitor_management.errors.ZoneAlreadyExists;
import com.rackspace.salus.monitor_management.entities.Zone;
import com.rackspace.salus.monitor_management.web.model.ZoneCreate;
import com.rackspace.salus.monitor_management.web.model.ZoneUpdate;
import com.rackspace.salus.monitor_management.repositories.ZoneRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ZoneManagement {
    private final ZoneRepository zoneRepository;
    private final ZoneStorage zoneStorage;
    private final MonitorManagement monitorManagement;

    static final String PUBLIC = "_PUBLIC_";

    @Autowired
    public ZoneManagement(ZoneRepository zoneRepository, ZoneStorage zoneStorage, @Lazy MonitorManagement monitorManagement) {
        this.zoneRepository = zoneRepository;
        this.zoneStorage = zoneStorage;
        this.monitorManagement = monitorManagement;
    }

    public Optional<Zone> getZone(String tenantId, String name) {
        return zoneRepository.findByTenantIdAndName(tenantId, name);
    }

    /**
     * Store a new zone in the database.
     * @param tenantId The tenant to create the zone for.
     * @param newZone The zone parameters to store.
     * @return The newly created resource.
     * @throws ZoneAlreadyExists If the zone name already exists for the tenant.
     */
    public Zone createZone(String tenantId, @Valid ZoneCreate newZone) throws ZoneAlreadyExists {
        if (exists(tenantId, newZone.getName())) {
            throw new ZoneAlreadyExists(String.format("Zone already exists with name %s on tenant %s",
                    newZone.getName(), tenantId));
        }

        Zone zone = new Zone()
                .setTenantId(tenantId)
                .setName(newZone.getName())
                .setEnvoyTimeout(Duration.ofSeconds(newZone.getPollerTimeout()));

        zoneRepository.save(zone);

        return zone;
    }

    public Zone updateZone(String tenantId, String name, @Valid ZoneUpdate updatedZone) {
        Zone zone = getZone(tenantId, name).orElseThrow(() ->
                new NotFoundException(String.format("No zone found named %s on tenant %s",
                        name, tenantId)));

        zone.setEnvoyTimeout(Duration.ofSeconds(updatedZone.getPollerTimeout()));
        zoneRepository.save(zone);

        // TBD: need to restart watchers?

        return zone;
    }

    public void removeZone(String tenantId, String name) {
        Zone zone = getZone(tenantId, name).orElseThrow(() ->
                new NotFoundException(String.format("No zone found named %s on tenant %s",
                        name, tenantId)));

        int monitors = monitorManagement.getMonitorsForZone(tenantId, name).size();
        if(monitors > 0) {
            throw new IllegalArgumentException(
                    String.format("Cannot remove zone with configured monitors. Found %s.", monitors));
        }

        long activeEnvoys = getActiveEnvoyCountForZone(zone);
        log.debug("Found {} active envoys for zone {}", activeEnvoys, name);
        if (activeEnvoys > 0) {
            throw new IllegalArgumentException(
                    String.format("Cannot remove zone with connected pollers. Found %d.", activeEnvoys));
        }

        zoneRepository.deleteById(zone.getId());

        // TBD: remove expected entries in etcd?
    }

    private long getActiveEnvoyCountForZone(Zone zone) {
        ResolvedZone resolvedZone;
        if (zone.getTenantId().equals(PUBLIC)) {
            resolvedZone = ResolvedZone.createPublicZone(zone.getName());
        } else {
            resolvedZone = ResolvedZone.createPrivateZone(zone.getTenantId(), zone.getName());
        }

        return zoneStorage.getActiveEnvoyCountForZone(resolvedZone).join();
    }

    private List<Zone> getZonesByTenant(String tenantId) {
        return zoneRepository.findAllByTenantId(tenantId);
    }

    private List<Zone> getAllPublicZones() {
        return getZonesByTenant(PUBLIC);
    }

    public List<Zone> getAvailableZonesForTenant(String tenantId) {
        List<Zone> availableZones = new ArrayList<>();
        availableZones.addAll(getAllPublicZones());
        availableZones.addAll(getZonesByTenant(tenantId));

        return availableZones;
    }

    /**
     * Tests whether the zone exists on the given tenant.
     * @param tenantId The tenant owning the zone.
     * @param zoneName The unique value representing the zone.
     * @return True if the zone exists on the tenant, otherwise false.
     */
    private boolean exists(String tenantId, String zoneName) {
        return zoneRepository.existsByTenantIdAndName(tenantId, zoneName);
    }
}
