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

package com.rackspace.salus.monitor_management.services;

import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.createPrivateZone;
import static com.rackspace.salus.telemetry.etcd.types.ResolvedZone.createPublicZone;

import com.google.common.collect.Streams;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import com.rackspace.salus.monitor_management.entities.Monitor;
import com.rackspace.salus.monitor_management.entities.Zone;
import com.rackspace.salus.monitor_management.repositories.BoundMonitorRepository;
import com.rackspace.salus.monitor_management.repositories.MonitorRepository;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.telemetry.errors.AlreadyExistsException;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.Resource;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;


@Slf4j
@Service
public class MonitorManagement {

    private final BoundMonitorRepository boundMonitorRepository;
    private final ZoneStorage zoneStorage;
    private final MonitorEventProducer monitorEventProducer;
    private final MonitorContentRenderer monitorContentRenderer;
    private final ResourceApi resourceApi;
    private final ZoneManagement zoneManagement;
    private final ZonesProperties zonesProperties;

    private final MonitorRepository monitorRepository;

    @PersistenceContext
    private final EntityManager entityManager;

    private final EnvoyResourceManagement envoyResourceManagement;

    JdbcTemplate jdbcTemplate;

    @Autowired
    public MonitorManagement(MonitorRepository monitorRepository, EntityManager entityManager,
                             EnvoyResourceManagement envoyResourceManagement,
                             BoundMonitorRepository boundMonitorRepository,
                             ZoneStorage zoneStorage,
                             MonitorEventProducer monitorEventProducer,
                             MonitorContentRenderer monitorContentRenderer,
                             ResourceApi resourceApi,
                             ZoneManagement zoneManagement, ZonesProperties zonesProperties,
                             JdbcTemplate jdbcTemplate) {
        this.monitorRepository = monitorRepository;
        this.entityManager = entityManager;
        this.envoyResourceManagement = envoyResourceManagement;
        this.boundMonitorRepository = boundMonitorRepository;
        this.zoneStorage = zoneStorage;
        this.monitorEventProducer = monitorEventProducer;
        this.monitorContentRenderer = monitorContentRenderer;
        this.resourceApi = resourceApi;
        this.zoneManagement = zoneManagement;
        this.zonesProperties = zonesProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Gets an individual monitor object by the public facing id.
     *
     * @param tenantId The tenant owning the monitor.
     * @param id       The unique value representing the monitor.
     * @return The monitor object.
     */
    public Optional<Monitor> getMonitor(String tenantId, UUID id) {
        return monitorRepository.findById(id).filter(m -> m.getTenantId().equals(tenantId));
    }

    /**
     * Get a selection of monitor objects across all accounts.
     *
     * @param page The slice of results to be returned.
     * @return The monitors found that match the page criteria.
     */
    public Page<Monitor> getAllMonitors(Pageable page) {
        return monitorRepository.findAll(page);
    }

    /**
     * Same as {@link #getAllMonitors(Pageable page) getAllMonitors} except restricted to a single tenant.
     *
     * @param tenantId The tenant to select monitors from.
     * @param page     The slice of results to be returned.
     * @return The monitors found for the tenant that match the page criteria.
     */
    public Page<Monitor> getMonitors(String tenantId, Pageable page) {
        return monitorRepository.findByTenantId(tenantId, page);
    }

    /**
     * Get all monitors as a stream
     *
     * @return Stream of monitors.
     */
    public Stream<Monitor> getMonitorsAsStream() {
        return Streams.stream(monitorRepository.findAll());
    }

    /**
     * Create a new monitor in the database.
     *
     * @param tenantId   The tenant to create the entity for.
     * @param newMonitor The monitor parameters to store.
     * @return The newly created monitor.
     */
    public Monitor createMonitor(String tenantId, @Valid MonitorCU newMonitor) throws IllegalArgumentException, AlreadyExistsException {
        log.debug("Creating monitor={} for tenant={}", newMonitor, tenantId);

        validateMonitoringZones(tenantId, newMonitor.getZones());

        Monitor monitor = new Monitor()
                .setTenantId(tenantId)
                .setMonitorName(newMonitor.getMonitorName())
                .setLabelSelector(newMonitor.getLabelSelector())
                .setContent(newMonitor.getContent())
                .setAgentType(newMonitor.getAgentType())
                .setSelectorScope(newMonitor.getSelectorScope())
                .setZones(newMonitor.getZones());

        monitorRepository.save(monitor);
        final List<BoundMonitor> boundMonitors = bindMonitor(monitor);
        sendMonitorBoundEvents(boundMonitors);
        return monitor;
    }

    private void validateMonitoringZones(String tenantId, List<String> providedZones) throws IllegalArgumentException {
        if (providedZones == null || providedZones.isEmpty()) {
            return;
        }
        List<String> availableZones = zoneManagement.getAvailableZonesForTenant(tenantId)
                .stream().map(Zone::getName).collect(Collectors.toList());

        List<String> invalidZones = providedZones.stream()
                .filter(z -> !availableZones.contains(z))
                .collect(Collectors.toList());

        if (!invalidZones.isEmpty()) {
            throw new IllegalArgumentException(String.format("Invalid zone(s) provided: %s",
                    String.join(",", invalidZones)));
        }
    }

    List<BoundMonitor> bindMonitor(Monitor monitor) {
        return bindMonitor(monitor, determineMonitoringZones(monitor));
    }

    List<BoundMonitor> bindMonitor(Monitor monitor,
                                   List<String> zones) {
        final List<Resource> resources = resourceApi.getResourcesWithLabels(
            monitor.getTenantId(), monitor.getLabelSelector());

        log.debug("Distributing new monitor={} to resources={}", monitor, resources);

        final List<BoundMonitor> boundMonitors = new ArrayList<>();

        if (monitor.getSelectorScope() == ConfigSelectorScope.LOCAL) {
            // AGENT MONITOR

            for (Resource resource : resources) {
                final ResourceInfo resourceInfo = envoyResourceManagement
                    .getOne(monitor.getTenantId(), resource.getResourceId())
                    .join();

                boundMonitors.add(
                    bindAgentMonitor(monitor, resource, resourceInfo.getEnvoyId())
                );
            }

        } else {
            // REMOTE MONITOR

            for (Resource resource : resources) {
                for (String zone : zones) {
                    boundMonitors.add(
                        bindRemoteMonitor(monitor, resource, zone)
                    );
                }
            }

        }

        if (!boundMonitors.isEmpty()) {
            log.debug("Saving boundMonitors={} from monitor={}", boundMonitors, monitor);
            boundMonitorRepository.saveAll(boundMonitors);

        }
        else {
            log.debug("No monitors were bound from monitor={}", monitor);
        }

        return boundMonitors;
    }

    private void sendMonitorBoundEvent(String envoyId) {
        log.debug("Publishing MonitorBoundEvent for envoy={}", envoyId);
        monitorEventProducer.sendMonitorEvent(
            new MonitorBoundEvent()
                .setEnvoyId(envoyId)
        );
    }

    /**
     * This method sends at most one {@link MonitorBoundEvent} for each envoy ID referenced
     * within the given collection of {@link BoundMonitor}s
     * @param boundMonitors the {@link BoundMonitor}s to evaluate
     */
    void sendMonitorBoundEvents(List<BoundMonitor> boundMonitors) {
        // Reduce the given bound monitors into one distinct event per envoy ID and send it
        boundMonitors.stream()
            // ...extract envoy ID
            .map(BoundMonitor::getEnvoyId)
            // ...only assigned ones
            .filter(Objects::nonNull)
            // ...remove dupes
            .distinct()
            // ...create events
            .map(envoyId -> new MonitorBoundEvent()
                .setEnvoyId(envoyId))
            .forEach(monitorEventProducer::sendMonitorEvent);
    }

    private BoundMonitor bindAgentMonitor(Monitor monitor, Resource resource, String envoyId) {
        return new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId(resource.getResourceId())
            .setEnvoyId(envoyId)
            .setRenderedContent(renderMonitorContent(monitor, resource))
            .setZoneName("");
    }

    private BoundMonitor bindRemoteMonitor(Monitor monitor, Resource resource, String zone) {
        final ResolvedZone resolvedZone = resolveZone(monitor.getTenantId(), zone);

        final Optional<EnvoyResourcePair> result = zoneStorage.findLeastLoadedEnvoy(resolvedZone).join();

        final String envoyId;
        if (result.isPresent()) {
            envoyId = result.get().getEnvoyId();
            zoneStorage.incrementBoundCount(resolvedZone, resource.getResourceId())
            .join();
        }
        else {
            envoyId = null;
        }

        return new BoundMonitor()
            .setZoneName(zone)
            .setMonitor(monitor)
            .setResourceId(resource.getResourceId())
            .setEnvoyId(envoyId)
            .setRenderedContent(renderMonitorContent(monitor, resource));
    }

    private static ResolvedZone getResolvedZoneOfBoundMonitor(BoundMonitor boundMonitor) {
        final String zoneTenantId = boundMonitor.getMonitor().getTenantId();
        final String zoneName = boundMonitor.getZoneName();

        if (zoneTenantId.equals(ResolvedZone.PUBLIC)) {
            return ResolvedZone.createPublicZone(zoneName);
        }
        else {
            return ResolvedZone.createPrivateZone(zoneTenantId, zoneName);
        }
    }

    public void handleNewEnvoyInZone(@Nullable String zoneTenantId, String zoneName) {
        log.debug("Locating bound monitors without assigned envoy with zoneName={} and zoneTenantId={}",
            zoneName, zoneTenantId);

        final ResolvedZone resolvedZone = resolveZone(zoneTenantId, zoneName);

        final List<BoundMonitor> onesWithoutEnvoy;
        if (resolvedZone.isPublicZone()) {
            onesWithoutEnvoy = boundMonitorRepository.findAllWithoutEnvoyInPublicZone(zoneName);
        }
        else {
            onesWithoutEnvoy = boundMonitorRepository.findAllWithoutEnvoyInPrivateZone(zoneTenantId, zoneName);
        }

        log.debug("Found bound monitors without envoy: {}", onesWithoutEnvoy);

        final List<BoundMonitor> assigned = new ArrayList<>(onesWithoutEnvoy.size());

        for (BoundMonitor boundMonitor : onesWithoutEnvoy) {

            final Optional<EnvoyResourcePair> result = zoneStorage.findLeastLoadedEnvoy(resolvedZone).join();
            if (result.isPresent()) {
                boundMonitor.setEnvoyId(result.get().getEnvoyId());
                assigned.add(boundMonitor);
                zoneStorage.incrementBoundCount(resolvedZone, result.get().getResourceId());
            }
        }

        if (!assigned.isEmpty()) {
            log.debug("Assigned existing bound monitors to envoys: {}", assigned);

            boundMonitorRepository.saveAll(assigned);

            sendMonitorBoundEvents(assigned);
        }
    }

    public void handleEnvoyResourceChangedInZone(@Nullable String tenantId,
                                                 String zoneName, String resourceId,
                                                 String fromEnvoyId, String toEnvoyId) {
        log.debug("Moving bound monitors to new envoy for same resource");

        final ResolvedZone resolvedZone = resolveZone(tenantId, zoneName);

        final List<BoundMonitor> boundToPrev;
        if ( resolvedZone.isPublicZone()) {
            boundToPrev = boundMonitorRepository.findAllWithEnvoyInPublicZone(
                zoneName,
                fromEnvoyId
            );
        }
        else {
            boundToPrev = boundMonitorRepository.findAllWithEnvoyInPrivateZone(
                tenantId,
                zoneName,
                fromEnvoyId
            );
        }

        if (!boundToPrev.isEmpty()) {
            log.debug("Re-assigning bound monitors={} to envoy={}", boundToPrev, toEnvoyId);
            for (BoundMonitor boundMonitor : boundToPrev) {
                boundMonitor.setEnvoyId(toEnvoyId);
            }

            boundMonitorRepository.saveAll(boundToPrev);


            zoneStorage.incrementBoundCount(
                createPrivateZone(tenantId, zoneName),
                resourceId,
                boundToPrev.size()
            );

            sendMonitorBoundEvent(toEnvoyId);
        }
        else {
            log.debug("No bound monitors were previously assigned to envoy={}", fromEnvoyId);
        }
    }

    private String renderMonitorContent(Monitor monitor,
                                        Resource resource) {
        return monitorContentRenderer.render(monitor.getContent(), resource);
    }

    private ResolvedZone resolveZone(String tenantId, String zone) {
        if (zone.startsWith(ResolvedZone.PUBLIC_PREFIX)) {
            return createPublicZone(zone);
        }
        else {
            return createPrivateZone(tenantId, zone);
        }
    }

    private List<String> determineMonitoringZones(Monitor monitor) {
        if (monitor.getSelectorScope() != ConfigSelectorScope.REMOTE) {
            return Collections.emptyList();
        }
        if (monitor.getZones() == null || monitor.getZones().isEmpty()) {
            return zonesProperties.getDefaultZones();
        }
        return monitor.getZones();
    }

    /**
     * Update an existing monitor.
     *
     * @param tenantId      The tenant to create the entity for.
     * @param id            The id of the existing monitor.
     * @param updatedValues The new monitor parameters to store.
     * @return The newly updated monitor.
     */
    public Monitor updateMonitor(String tenantId, UUID id, @Valid MonitorCU updatedValues) {
      Monitor monitor = getMonitor(tenantId, id).orElseThrow(() ->
          new NotFoundException(String.format("No monitor found for %s on tenant %s",
              id, tenantId)));

        validateMonitoringZones(tenantId, updatedValues.getZones());

        final List<BoundMonitor> boundMonitorsChanges = new ArrayList<>();

        if (updatedValues.getLabelSelector() != null &&
            !updatedValues.getLabelSelector().equals(monitor.getLabelSelector())) {
            // Process potential changes to resource selection and therefore bindings
            // ...only need to process removed and new bindings

            boundMonitorsChanges.addAll(
                processMonitorLabelSelectorModified(monitor, updatedValues.getLabelSelector())
            );

            monitor.setLabelSelector(updatedValues.getLabelSelector());
        }
        else if (monitor.getLabelSelector() != null) {
            // JPA's EntityManager is a little strange with re-saving (aka merging) an entity
            // that has a field of type Map. It wants to clear the loaded map value, which is
            // disallowed by the org.hibernate.collection.internal.PersistentMap it uses for
            // retrieved maps.
            monitor.setLabelSelector(new HashMap<>(monitor.getLabelSelector()));
        }

        if (updatedValues.getContent() != null &&
            !updatedValues.getContent().equals(monitor.getContent())) {
            // Process potential changes to bound resource rendered content
            // ...only need to process changed bindings

            boundMonitorsChanges.addAll(
                processMonitorContentModified(monitor, updatedValues.getContent())
            );

            monitor.setContent(updatedValues.getContent());
        }

        if (zonesChanged(updatedValues.getZones(), monitor.getZones())) {
            // Process potential changes to bound zones

            boundMonitorsChanges.addAll(
                processMonitorZonesModified(monitor, updatedValues.getZones())
            );

            // give JPA a modifiable copy of the given list
            monitor.setZones(new ArrayList<>(updatedValues.getZones()));
        }
        else if (monitor.getZones() != null){
            // See above regarding:
            // JPA's EntityManager is a little strange with re-saving (aka merging) an entity
            monitor.setZones(new ArrayList<>(monitor.getZones()));
        }

        PropertyMapper map = PropertyMapper.get();
        map.from(updatedValues.getMonitorName())
                .whenNonNull()
                .to(monitor::setMonitorName);
        monitorRepository.save(monitor);

        sendMonitorBoundEvents(boundMonitorsChanges);

        return monitor;
    }

    private static boolean zonesChanged(List<String> updatedZones, List<String> prevZones) {
        return updatedZones != null &&
            ( updatedZones.size() != prevZones.size() ||
            !updatedZones.containsAll(prevZones));
    }

    private List<BoundMonitor> processMonitorZonesModified(Monitor monitor,
                                                           List<String> updatedZones) {

        // determine new zones
        final List<String> newZones = new ArrayList<>(updatedZones);
        // ...by removing zones on currently stored monitor
        newZones.removeAll(monitor.getZones());

        // determine old zones
        final List<String> oldZones = new ArrayList<>(monitor.getZones());
        // ...by removing the ones still in the update
        oldZones.removeAll(updatedZones);

        final List<BoundMonitor> changed = new ArrayList<>(
            // this will also delete the unbound bindings
            unbindByMonitorAndZone(monitor.getId(), oldZones)
        );

        changed.addAll(
            // this will also save the new bindings
            bindMonitor(monitor, newZones)
        );

        return changed;
    }

    private List<BoundMonitor> processMonitorContentModified(Monitor monitor,
                                                             String updatedContent) {
        final List<BoundMonitor> boundMonitors = boundMonitorRepository
            .findAllByMonitor_Id(monitor.getId());

        final MultiValueMap<String/*resourceId*/, BoundMonitor> groupedByResourceId = new LinkedMultiValueMap<>();
        for (BoundMonitor boundMonitor : boundMonitors) {
            groupedByResourceId.add(boundMonitor.getResourceId(), boundMonitor);
        }

        final List<BoundMonitor> modified = new ArrayList<>();

        for (Entry<String, List<BoundMonitor>> resourceEntry : groupedByResourceId.entrySet()) {

            final String tenantId = monitor.getTenantId();
            final String resourceId = resourceEntry.getKey();
            final Resource resource = resourceApi
                .getByResourceId(tenantId, resourceId);

            if (resource != null) {
                final String newContent = monitorContentRenderer.render(updatedContent, resource);

                for (BoundMonitor boundMonitor : resourceEntry.getValue()) {
                    if (!newContent.equals(boundMonitor.getRenderedContent())) {
                        boundMonitor.setRenderedContent(newContent);
                        modified.add(boundMonitor);
                    }
                }
            }
            else {
                log.warn("Failed to find resourceId={} during processing of monitor={}",
                    resourceId, monitor);
            }
        }

        if (!modified.isEmpty()) {
            log.debug("Saving bound monitors with re-rendered content: {}", modified);
            boundMonitorRepository.saveAll(modified);
        }

        return modified;
    }

    private List<BoundMonitor> processMonitorLabelSelectorModified(Monitor monitor,
                                                                   Map<String, String> updatedLabelSelector) {

      final Set<String> boundResourceIds =
          boundMonitorRepository.findResourceIdsBoundToMonitor(monitor.getId());

        final List<Resource> selectedResources = resourceApi
            .getResourcesWithLabels(monitor.getTenantId(), updatedLabelSelector);

        final Set<String> selectedResourceIds = selectedResources.stream()
            .map(Resource::getResourceId)
            .collect(Collectors.toSet());

        final List<String> resourceIdsToUnbind = new ArrayList<>(boundResourceIds);
        resourceIdsToUnbind.removeAll(selectedResourceIds);

        // process un-bindings
        final List<BoundMonitor> boundMonitorsChanges =
            new ArrayList<>(
                unbindByResourceId(monitor.getId(), resourceIdsToUnbind)
            );

        // process new bindings
        selectedResources.stream()
            .filter(resource -> !boundResourceIds.contains(resource.getResourceId()))
            .forEach(resource -> {

                boundMonitorsChanges.addAll(
                    upsertBindingToResource(
                        Collections.singletonList(monitor),
                        resource
                    )
                );

            });

        return boundMonitorsChanges;
    }


    /**
     * Delete a monitor.
     *
     * @param tenantId The tenant the monitor belongs to.
     * @param id       The id of the monitor.
     */
    public void removeMonitor(String tenantId, UUID id) {
        Monitor monitor = getMonitor(tenantId, id).orElseThrow(() ->
          new NotFoundException(String.format("No monitor found for %s on tenant %s",
              id, tenantId)));

        // need to unbind before deleting monitor since BoundMonitor references Monitor
        final List<BoundMonitor> unbound = unbindByMonitorId(Collections.singletonList(id));

        sendMonitorBoundEvents(unbound);

        monitorRepository.delete(monitor);
    }

    /**
     * Find all monitors associated with a changed resource, and notify the corresponding envoy of the changes.
     * Monitors are found that correspond to both the new labels as well as any old ones so that
     * all the corresponding monitors can be updated for a resource.
     *
     * @param event the new resource event.
     */
    void handleResourceEvent(ResourceEvent event) {
        final String tenantId = event.getTenantId();
        final String resourceId = event.getResourceId();

        final List<UUID> boundMonitorIds =
            boundMonitorRepository.findMonitorsBoundToResource(tenantId, resourceId);

        // monitorIdsToUnbind := boundMonitorIds \setminus selectedMonitorIds
        // ...so start with populating with boundMonitorIds
        final Set<UUID> monitorIdsToUnbind = new HashSet<>(boundMonitorIds);

        final List<Monitor> selectedMonitors;
        final Resource resource = resourceApi.getByResourceId(tenantId, resourceId);
        if (resource != null) {
            // resource created or updated

            selectedMonitors = getMonitorsFromLabels(resource.getLabels(), tenantId);

            final List<UUID> selectedMonitorIds = selectedMonitors.stream()
                .map(Monitor::getId)
                .collect(Collectors.toList());

            // ...the setminus operation upon monitorIdsToUnbind
            monitorIdsToUnbind.removeAll(selectedMonitorIds);
        }
        else {
            // resource deleted

            selectedMonitors = Collections.emptyList();
            // ...and monitorIdsToUnbind remains ALL of the currently bound
        }

        List<BoundMonitor> changes = new ArrayList<>(
            unbindByMonitorId(monitorIdsToUnbind)
        );

        if (!selectedMonitors.isEmpty()) {
            changes.addAll(
                upsertBindingToResource(selectedMonitors, resource)
            );
        }

        sendMonitorBoundEvents(changes);
    }

    List<BoundMonitor> upsertBindingToResource(List<Monitor> monitors,
                                                       Resource resource) {

        final ResourceInfo resourceInfo = envoyResourceManagement
            .getOne(resource.getTenantId(), resource.getResourceId())
            .join();

        final List<BoundMonitor> boundMonitors = new ArrayList<>();

        for (Monitor monitor : monitors) {
            final List<BoundMonitor> existing = boundMonitorRepository
                .findAllByMonitor_IdAndResourceId(monitor.getId(), resource.getResourceId());

            if (existing.isEmpty()) {
                // need to create new bindings

                if (monitor.getSelectorScope() == ConfigSelectorScope.LOCAL) {
                    // agent/local monitor
                    boundMonitors.add(
                        bindAgentMonitor(monitor, resource,
                            resourceInfo != null ? resourceInfo.getEnvoyId() : null)
                    );
                } else {
                    // remote monitor
                    final List<String> zones = determineMonitoringZones(monitor);

                    for (String zone : zones) {
                        boundMonitors.add(
                            bindRemoteMonitor(monitor, resource, zone)
                        );
                    }
                }
            } else {
                // existing bindings need to be tested and updated for rendered content changes

                final String newRenderedContent = renderMonitorContent(monitor, resource);

                boundMonitors.addAll(
                    existing.stream()
                        // rendered content change?
                        .filter(existingBind ->
                            !existingBind.getRenderedContent().equals(newRenderedContent))
                        // for those that changed, modify entity
                        .peek(existingBind -> existingBind.setRenderedContent(newRenderedContent))
                        // and add all of these to list to save and return
                        .collect(Collectors.toList())
                );
            }
        }

        log.debug("Saving boundMonitors={} due to binding of monitors={} to resource={}",
            boundMonitors, monitors, resource);
        boundMonitorRepository.saveAll(boundMonitors);

        return boundMonitors;
    }

    List<BoundMonitor> unbindByMonitorId(Collection<UUID> monitorIdsToUnbind) {
        if (monitorIdsToUnbind.isEmpty()) {
            return Collections.emptyList();
        }

        final List<BoundMonitor> boundMonitors =
            boundMonitorRepository.findAllByMonitor_IdIn(monitorIdsToUnbind);

        log.debug("Unbinding {} from monitorIds={}",
            boundMonitors, monitorIdsToUnbind);
        boundMonitorRepository.deleteAll(boundMonitors);
        decrementBoundCounts(boundMonitors);

        return boundMonitors;
    }

    private List<BoundMonitor> unbindByResourceId(UUID monitorId,
                                                  List<String> resourceIdsToUnbind) {
        if (resourceIdsToUnbind.isEmpty()) {
            return Collections.emptyList();
        }

        final List<BoundMonitor> boundMonitors = boundMonitorRepository
            .findAllByMonitor_IdAndResourceIdIn(monitorId, resourceIdsToUnbind);

        log.debug("Unbinding {} from monitorId={} resourceIds={}", boundMonitors,
            monitorId, resourceIdsToUnbind);
        boundMonitorRepository.deleteAll(boundMonitors);
        decrementBoundCounts(boundMonitors);

        return boundMonitors;
    }

    private List<BoundMonitor> unbindByMonitorAndZone(UUID monitorId, List<String> zones) {

        final List<BoundMonitor> needToDelete = boundMonitorRepository
            .findAllByMonitor_IdAndZoneNameIn(monitorId, zones);

        log.debug("Unbinding monitorId={} from zones={}: {}", monitorId, zones, needToDelete);
        boundMonitorRepository.deleteAll(needToDelete);

        decrementBoundCounts(needToDelete);

        return needToDelete;
    }

    private void decrementBoundCounts(List<BoundMonitor> needToDelete) {
        for (BoundMonitor boundMonitor : needToDelete) {
            if (boundMonitor.getEnvoyId() != null &&
                boundMonitor.getMonitor().getSelectorScope() == ConfigSelectorScope.REMOTE) {

                zoneStorage.decrementBoundCount(getResolvedZoneOfBoundMonitor(boundMonitor),
                    boundMonitor.getResourceId()
                );

            }
        }
    }

    /**
     * takes in a Mapping of labels for a tenant, builds and runs the query to match to those labels
     * @param labels the labels that we need to match to
     * @param tenantId The tenant associated to the resource
     * @return the list of Monitor's that match the labels
     */
    public List<Monitor> getMonitorsFromLabels(Map<String, String> labels, String tenantId) throws IllegalArgumentException {
        if(labels.size() == 0) {
            throw new IllegalArgumentException("Labels must be provided for search");
        }

        MapSqlParameterSource paramSource = new MapSqlParameterSource();
        paramSource.addValue("tenantId", tenantId);
        StringBuilder builder = new StringBuilder("SELECT monitors.id FROM monitors JOIN monitor_label_selectors AS ml WHERE monitors.id = ml.monitor_id AND monitors.id IN ");
        builder.append("(SELECT monitor_id from monitor_label_selectors WHERE monitors.id IN (SELECT id FROM monitors WHERE tenant_id = :tenantId) AND ");
        builder.append("monitors.id IN (SELECT search_labels.monitor_id FROM (SELECT monitor_id, COUNT(*) AS count FROM monitor_label_selectors GROUP BY monitor_id) AS total_labels JOIN (SELECT monitor_id, COUNT(*) AS count FROM monitor_label_selectors WHERE ");
        int i = 0;
        labels.size();
        for(Map.Entry<String, String> entry : labels.entrySet()) {
            if(i > 0) {
                builder.append(" OR ");
            }
            builder.append("(label_selector = :label"+ i +" AND label_selector_key = :labelKey" + i + ")");
            paramSource.addValue("label"+i, entry.getValue());
            paramSource.addValue("labelKey"+i, entry.getKey());
            i++;
        }
        builder.append(" GROUP BY monitor_id) AS search_labels WHERE total_labels.monitor_id = search_labels.monitor_id AND search_labels.count >= total_labels.count GROUP BY search_labels.monitor_id)");

        builder.append(") ORDER BY monitors.id");
        paramSource.addValue("i", i);

        NamedParameterJdbcTemplate namedParameterTemplate = new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource());
        final List<UUID> monitorIds = namedParameterTemplate.query(builder.toString(), paramSource,
            (resultSet, rowIndex) -> UUID.fromString(resultSet.getString(1))
        );

        final List<Monitor> monitors = new ArrayList<>();
        // use JPA to retrieve and resolve the Monitor objects and then convert Iterable result to list
        for (Monitor monitor : monitorRepository.findAllById(monitorIds)) {
            monitors.add(monitor);
        }
        return monitors;
    }

    /**
     * Unassigns the old envoy from all relevant bound monitors,
     * then attempts to reassign them to a different envoy if one is available.
     * @param zoneTenantId The tenantId of the resolved zone.
     * @param zoneName The name of the resolved zone.
     * @param envoyId The envoy id that has disconnected.
     */
    public void handleExpiredEnvoy(String zoneTenantId, String zoneName, String envoyId) {
        log.debug("Reassigning bound monitors for disconnected envoy={} with zoneName={} and zoneTenantId={}",
            envoyId, zoneName, zoneTenantId);
        List<BoundMonitor> boundMonitors = boundMonitorRepository.findAllByEnvoyId(envoyId);
        if (boundMonitors.isEmpty()) {
            return;
        }
        for (BoundMonitor boundMonitor : boundMonitors) {
            boundMonitor.setEnvoyId(null);
        }

        boundMonitorRepository.saveAll(boundMonitors);
        handleNewEnvoyInZone(zoneTenantId, zoneName);
    }
}