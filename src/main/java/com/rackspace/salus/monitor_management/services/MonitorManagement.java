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

import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import com.rackspace.salus.monitor_management.repositories.BoundMonitorRepository;
import com.rackspace.salus.monitor_management.repositories.MonitorRepository;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.telemetry.errors.AlreadyExistsException;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.MonitorEvent;
import com.rackspace.salus.telemetry.messaging.OperationType;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.Monitor;
import com.rackspace.salus.telemetry.model.Monitor_;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.Resource;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class MonitorManagement {

    private final BoundMonitorRepository boundMonitorRepository;
    private final ZoneStorage zoneStorage;
    private final MonitorEventProducer monitorEventProducer;
    private final MonitorContentRenderer monitorContentRenderer;
    private final ResourceApi resourceApi;
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
                             ZonesProperties zonesProperties,
                             JdbcTemplate jdbcTemplate) {
        this.monitorRepository = monitorRepository;
        this.entityManager = entityManager;
        this.envoyResourceManagement = envoyResourceManagement;
        this.boundMonitorRepository = boundMonitorRepository;
        this.zoneStorage = zoneStorage;
        this.monitorEventProducer = monitorEventProducer;
        this.monitorContentRenderer = monitorContentRenderer;
        this.resourceApi = resourceApi;
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
    public Monitor getMonitor(String tenantId, UUID id) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Monitor> cr = cb.createQuery(Monitor.class);
        Root<Monitor> root = cr.from(Monitor.class);
        cr.select(root).where(cb.and(
                cb.equal(root.get(Monitor_.tenantId), tenantId),
                cb.equal(root.get(Monitor_.id), id)));

        Monitor result;
        try {
            result = entityManager.createQuery(cr).getSingleResult();
        } catch (NoResultException e) {
            result = null;
        }
        return result;
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
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Monitor> cr = cb.createQuery(Monitor.class);
        Root<Monitor> root = cr.from(Monitor.class);
        cr.select(root).where(
                cb.equal(root.get(Monitor_.tenantId), tenantId));

        List<Monitor> monitors = entityManager.createQuery(cr).getResultList();

        return new PageImpl<>(monitors, page, monitors.size());
    }

    /**
     * Get all monitors as a stream
     *
     * @return Stream of monitors.
     */
    public Stream<Monitor> getMonitorsAsStream() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Monitor> cr = cb.createQuery(Monitor.class);
        Root<Monitor> root = cr.from(Monitor.class);
        cr.select(root);
        return entityManager.createQuery(cr).getResultStream();
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

      Monitor monitor = new Monitor()
                .setTenantId(tenantId)
                .setMonitorName(newMonitor.getMonitorName())
                .setLabelSelector(newMonitor.getLabelSelector())
                .setContent(newMonitor.getContent())
                .setAgentType(newMonitor.getAgentType())
                .setSelectorScope(newMonitor.getSelectorScope())
                .setZones(newMonitor.getZones());

        monitorRepository.save(monitor);
        distributeNewMonitor(monitor);
        return monitor;
    }

    void distributeNewMonitor(Monitor monitor) {
        final List<Resource> resources = resourceApi.getResourcesWithLabels(
            monitor.getTenantId(), monitor.getLabelSelector());

        log.debug("Distributing new monitor={} to resources={}", monitor, resources);

        final List<BoundMonitor> boundMonitors = new ArrayList<>();

        if (monitor.getSelectorScope() == ConfigSelectorScope.ALL_OF) {
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

            final List<String> zones = determineMonitoringZones(monitor);

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

            sendMonitorBoundEvents(boundMonitors);
        }
        else {
            log.debug("No monitors were bound from monitor={}", monitor);
        }
    }

    private void sendMonitorBoundEvent(String envoyId) {
        log.debug("Publishing MonitorBoundEvent for envoy={}", envoyId);
        monitorEventProducer.sendMonitorEvent(
            new MonitorBoundEvent()
                .setEnvoyId(envoyId)
        );
    }

    private void sendMonitorBoundEvents(List<BoundMonitor> boundMonitors) {
        // Convert and reduce the given bound monitors into one distinct event per envoy ID
        final List<MonitorBoundEvent> events = boundMonitors.stream()
            // ...only assigned ones
            .filter(boundMonitor -> boundMonitor.getEnvoyId() != null)
            // ...extract envoy ID
            .map(BoundMonitor::getEnvoyId)
            // ...remove dupes
            .distinct()
            // ...create events
            .map(envoyId -> new MonitorBoundEvent()
                .setEnvoyId(envoyId))
            .collect(Collectors.toList());

        for (MonitorBoundEvent event : events) {
            monitorEventProducer.sendMonitorEvent(event);
        }

    }

    private BoundMonitor bindAgentMonitor(Monitor monitor, Resource resource, String envoyId) {
        return new BoundMonitor()
            .setMonitor(monitor)
            .setResourceId(resource.getResourceId())
            .setEnvoyId(envoyId)
            .setRenderedContent(renderMonitorContent(monitor, resource))
            .setZoneTenantId("")
            .setZoneId("");
    }

    private BoundMonitor bindRemoteMonitor(Monitor monitor, Resource resource, String zone) {
        final ResolvedZone resolvedZone = resolveZone(monitor.getTenantId(), zone);

        final Optional<String> result = zoneStorage.findLeastLoadedEnvoy(resolvedZone).join();

        final String envoyId;
        if (result.isPresent()) {
            envoyId = result.get();
            zoneStorage.incrementBoundCount(resolvedZone, envoyId)
            .join();
        }
        else {
            envoyId = null;
        }

        return new BoundMonitor()
            .setZoneTenantId(emptyStringForNull(resolvedZone.getTenantId()))
            .setZoneId(zone)
            .setMonitor(monitor)
            .setResourceId(resource.getResourceId())
            .setEnvoyId(envoyId)
            .setRenderedContent(renderMonitorContent(monitor, resource));
    }

    List<UUID> findMonitorsBoundToResource(String tenantId, String resourceId) {
      return entityManager
          .createQuery("select distinct b.monitor.id from BoundMonitor b"
              + " where b.resourceId = :resourceId"
              + " and b.monitor.tenantId = :tenantId", UUID.class)
          .setParameter("tenantId", tenantId)
          .setParameter("resourceId", resourceId)
          .getResultList();
    }

    private static String emptyStringForNull(String input) {
        return input == null ? "" : input;
    }

    public void handleNewEnvoyInZone(@Nullable String zoneTenantId, String zoneId) {
        log.debug("Locating bound monitors without assigned envoy with zoneId={} and zoneTenantId={}",
            zoneId, zoneTenantId);

        final ResolvedZone resolvedZone = resolveZone(zoneTenantId, zoneId);

        final List<BoundMonitor> onesWithoutEnvoy = boundMonitorRepository
            .findOnesWithoutEnvoy(emptyStringForNull(zoneTenantId),  zoneId);

        log.debug("Found bound monitors without envoy: {}", onesWithoutEnvoy);

        final List<BoundMonitor> assigned = new ArrayList<>(onesWithoutEnvoy.size());

        for (BoundMonitor boundMonitor : onesWithoutEnvoy) {

            final Optional<String> result = zoneStorage.findLeastLoadedEnvoy(resolvedZone).join();
            if (result.isPresent()) {
                boundMonitor.setEnvoyId(result.get());
                assigned.add(boundMonitor);
            }
        }

        if (!assigned.isEmpty()) {
            log.debug("Assigned existing bound monitors to envoys: {}", assigned);

            boundMonitorRepository.saveAll(assigned);

            sendMonitorBoundEvents(assigned);
        }
    }

    public void handleEnvoyResourceChangedInZone(String tenantId, String zoneId, String fromEnvoyId,
                                                 String toEnvoyId) {

        final List<BoundMonitor> boundToPrev = boundMonitorRepository.findOnesWithEnvoy(
            emptyStringForNull(tenantId),
            zoneId,
            fromEnvoyId
        );

        if (!boundToPrev.isEmpty()) {
            log.debug("Re-assigning bound monitors={} to envoy={}", boundToPrev, toEnvoyId);
            for (BoundMonitor boundMonitor : boundToPrev) {
                boundMonitor.setEnvoyId(toEnvoyId);
            }

            boundMonitorRepository.saveAll(boundToPrev);

            zoneStorage.incrementBoundCount(
                new ResolvedZone().setTenantId(tenantId)
                .setId(zoneId),
                toEnvoyId,
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
        if (zone.startsWith(zonesProperties.getPublicZonePrefix())) {
            return new ResolvedZone()
                .setPublicZone(true)
                .setId(zone);
        }
        else {
            return new ResolvedZone()
                .setPublicZone(false)
                .setTenantId(tenantId)
                .setId(zone);
        }
    }

    private List<String> determineMonitoringZones(Monitor monitor) {
        if (monitor.getZones() == null || monitor.getZones().isEmpty()) {
            return zonesProperties.getDefaultZones();
        }
        return monitor.getZones();
    }

    /**
     * Send a kafka event announcing the monitor operation.  Finds the envoys that match labels in the
     * current monitor as well as the ones that match the old labels, and sends and event to each envoy.
     *
     * @param monitor       the monitor to create the event for.
     * @param operationType the crud operation that occurred on the monitor
     * @param oldLabels     the old labels that were on the monitor before if this is an update operation
     */
    private void publishMonitor(Monitor monitor, OperationType operationType,
                                Map<String, String> oldLabels) {
        List<String> resources = extractResourceIds(resourceApi.getResourcesWithLabels(monitor.getTenantId(), monitor.getLabelSelector()));
        List<String> oldResources = new ArrayList<>();
        if (oldLabels != null && !oldLabels.equals(monitor.getLabelSelector())) {
            oldResources = extractResourceIds(resourceApi.getResourcesWithLabels(monitor.getTenantId(), oldLabels));
        }
        resources.addAll(oldResources);

        final Set<String> deduped = new HashSet<>(resources);

        for (String resourceId : deduped) {
            ResourceInfo resourceInfo = envoyResourceManagement
                    .getOne(monitor.getTenantId(), resourceId).join();
            if (resourceInfo != null) {
                sendMonitorEvent(monitor, operationType, resourceInfo.getEnvoyId());
            }
        }
    }

    private List<String> extractResourceIds(List<Resource> resources) {
        return resources.stream()
            .map(Resource::getResourceId)
            .collect(Collectors.toList());
    }

    private void sendMonitorEvent(Monitor monitor,
                                  OperationType operationType,
                                  String envoyId) {
        MonitorEvent monitorEvent = new MonitorEvent()
            .setFromMonitor(monitor)
            .setOperationType(operationType)
            .setEnvoyId(envoyId);
        monitorEventProducer.sendMonitorEvent(monitorEvent);
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
        Monitor monitor = getMonitor(tenantId, id);
        if (monitor == null) {
            throw new NotFoundException(String.format("No monitor found for %s on tenant %s",
                    id, tenantId));
        }
        Map<String, String> oldLabels = monitor.getLabelSelector();
        PropertyMapper map = PropertyMapper.get();
        map.from(updatedValues.getLabelSelector())
                .whenNonNull()
                .to(monitor::setLabelSelector);
        map.from(updatedValues.getContent())
                .whenNonNull()
                .to(monitor::setContent);
        map.from(updatedValues.getMonitorName())
                .whenNonNull()
                .to(monitor::setMonitorName);
        monitorRepository.save(monitor);
        publishMonitor(monitor, OperationType.UPDATE, oldLabels);
        return monitor;
    }


    /**
     * Delete a monitor.
     *
     * @param tenantId The tenant the monitor belongs to.
     * @param id       The id of the monitor.
     */
    public void removeMonitor(String tenantId, UUID id) {
        Monitor monitor = getMonitor(tenantId, id);
        if (monitor != null) {
            monitorRepository.deleteById(monitor.getId());
            publishMonitor(monitor, OperationType.DELETE, null);
        } else {
            throw new NotFoundException(String.format("No monitor found for %s on tenant %s",
                    id, tenantId));
        }
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

        final List<UUID> boundMonitorIds = findMonitorsBoundToResource(tenantId, resourceId);

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

        List<BoundMonitor> unbound = unbindByMonitorId(monitorIdsToUnbind);

        final List<Monitor> monitorsToUpsert = selectedMonitors.stream()
            .filter(monitor -> !monitorIdsToUnbind.contains(monitor.getId()))
            .collect(Collectors.toList());

        final List<BoundMonitor> bound;
        if (!monitorsToUpsert.isEmpty()) {
            //noinspection ConstantConditions since monitorsToUpsert and selectedMonitors would be empty
            bound = upsertBindingToResource(monitorsToUpsert, resource);
        }
        else {
            bound = Collections.emptyList();
        }

        // Send MonitorBoundEvents to the distinct set of envoys affected by unbind and bind
        // changes collected above.
        Stream.concat(
            unbound.stream(),
            bound.stream()
        )
            .map(BoundMonitor::getEnvoyId)
            .filter(Objects::nonNull)
            .distinct()
            .forEach(this::sendMonitorBoundEvent);
    }

    List<BoundMonitor> upsertBindingToResource(List<Monitor> monitors,
                                                       Resource resource) {

        final ResourceInfo resourceInfo = envoyResourceManagement
            .getOne(resource.getTenantId(), resource.getResourceId())
            .join();

        final List<BoundMonitor> boundMonitors = new ArrayList<>();

        for (Monitor monitor : monitors) {
            final List<BoundMonitor> existing = boundMonitorRepository
                .findByMonitor_IdAndResourceId(monitor.getId(), resource.getResourceId());

            if (existing.isEmpty()) {
                // need to create new bindings

                if (monitor.getSelectorScope() == ConfigSelectorScope.ALL_OF) {
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

        final List<BoundMonitor> boundMonitors = entityManager.createQuery(
            "select b from BoundMonitor b where b.monitor.id in :monitorIds",
            BoundMonitor.class
        )
            .setParameter("monitorIds", monitorIdsToUnbind)
            .getResultList();

        log.debug("Unbinding {} from monitorIds={}",
            boundMonitors, monitorIdsToUnbind);
        boundMonitorRepository.deleteAll(boundMonitors);

        return boundMonitors;
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
        StringBuilder builder = new StringBuilder("SELECT monitors.id FROM monitors JOIN monitor_label_selectors AS ml WHERE monitors.id = ml.id AND monitors.id IN ");
        builder.append("(SELECT id from monitor_label_selectors WHERE monitors.id IN (SELECT id FROM monitors WHERE tenant_id = :tenantId) AND ");
        builder.append("monitors.id IN (SELECT search_labels.id FROM (SELECT id, COUNT(*) AS count FROM monitor_label_selectors GROUP BY id) AS total_labels JOIN (SELECT id, COUNT(*) AS count FROM monitor_label_selectors WHERE ");
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
        builder.append(" GROUP BY id) AS search_labels WHERE total_labels.id = search_labels.id AND search_labels.count >= total_labels.count GROUP BY search_labels.id)");

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
}
