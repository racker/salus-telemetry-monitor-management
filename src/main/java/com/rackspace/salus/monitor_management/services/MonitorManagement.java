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

import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import com.rackspace.salus.monitor_management.repositories.BoundMonitorRepository;
import com.rackspace.salus.monitor_management.repositories.MonitorRepository;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.telemetry.errors.AlreadyExistsException;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.MonitorEvent;
import com.rackspace.salus.telemetry.messaging.OperationType;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.Monitor;
import com.rackspace.salus.telemetry.model.Monitor_;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.Resource;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;


@Slf4j
@Service
public class MonitorManagement {

    private final BoundMonitorRepository boundMonitorRepository;
    private final ZoneStorage zoneStorage;
    private final MonitorEventProducer monitorEventProducer;
    private final MonitorContentRenderer monitorContentRenderer;
    private final ZonesProperties zonesProperties;

    private final MonitorRepository monitorRepository;

    private final RestTemplate restTemplate;

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
                             RestTemplateBuilder restTemplateBuilder,
                             ServicesProperties servicesProperties,
                             ZonesProperties zonesProperties,
                             JdbcTemplate jdbcTemplate) {
        this.monitorRepository = monitorRepository;
        this.entityManager = entityManager;
        this.envoyResourceManagement = envoyResourceManagement;
        this.boundMonitorRepository = boundMonitorRepository;
        this.zoneStorage = zoneStorage;
        this.monitorEventProducer = monitorEventProducer;
        this.monitorContentRenderer = monitorContentRenderer;
        this.zonesProperties = zonesProperties;
        this.restTemplate = restTemplateBuilder.rootUri(servicesProperties.getResourceManagementUrl()).build();
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
                .setLabels(newMonitor.getLabels())
                .setContent(newMonitor.getContent())
                .setAgentType(newMonitor.getAgentType())
                .setSelectorScope(newMonitor.getSelectorScope())
                .setZones(newMonitor.getZones());

        monitorRepository.save(monitor);
        distributeNewMonitor(monitor);
        return monitor;
    }

    void distributeNewMonitor(Monitor monitor) {
        final List<String> resourceIds = getResourcesWithLabels(
            monitor.getTenantId(), monitor.getLabels());

        log.debug("Distributing new monitor={} to resources={}", monitor, resourceIds);

        final List<BoundMonitor> boundMonitors = new ArrayList<>();

        if (monitor.getSelectorScope() == ConfigSelectorScope.ALL_OF) {
            // AGENT MONITOR

            for (String resourceId : resourceIds) {
                final ResourceInfo resourceInfo = envoyResourceManagement
                    .getOne(monitor.getTenantId(), resourceId)
                    .join();

                boundMonitors.add(
                    bindAgentMonitor(monitor, resourceId, resourceInfo.getEnvoyId())
                );
            }

        } else {
            // REMOTE MONITOR

            final List<String> zones = determineMonitoringZones(monitor);

            for (String resourceId : resourceIds) {
                for (String zone : zones) {
                    boundMonitors.add(
                        bindRemoteMonitor(monitor, resourceId, zone)
                    );
                }
            }

        }

        if (!boundMonitors.isEmpty()) {
            log.debug("Saving boundMonitors={} from monitor={}", boundMonitors, monitor);
            boundMonitorRepository.saveAll(boundMonitors);

            sendMonitorBoundEvents(OperationType.CREATE, boundMonitors);
        }
        else {
            log.debug("No monitors were bound from monitor={}", monitor);
        }
    }

    private void sendMonitorBoundEvents(OperationType operationType,
                                        List<BoundMonitor> boundMonitors) {
        final Set<MonitorBoundEvent> events = boundMonitors.stream()
            .filter(boundMonitor -> boundMonitor.getEnvoyId() != null)
            .map(boundMonitor -> new MonitorBoundEvent()
            .setEnvoyId(boundMonitor.getEnvoyId())
            .setOperationType(operationType))
            .collect(Collectors.toSet());

        for (MonitorBoundEvent event : events) {
            monitorEventProducer.sendMonitorEvent(event);
        }

    }

    private BoundMonitor bindAgentMonitor(Monitor monitor, String resourceId, String envoyId) {
        return new BoundMonitor()
            .setMonitorId(monitor.getId())
            .setResourceId(resourceId)
            .setEnvoyId(envoyId)
            .setAgentType(monitor.getAgentType())
            .setRenderedContent(renderMonitorContent(monitor))
            .setTargetTenant("")
            .setZone("");
    }

    private BoundMonitor bindRemoteMonitor(Monitor monitor, String resourceId, String zone) {
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
            .setZone(zone)
            .setMonitorId(monitor.getId())
            .setResourceId(resourceId)
            .setEnvoyId(envoyId)
            .setAgentType(monitor.getAgentType())
            .setTargetTenant(monitor.getTenantId())
            .setRenderedContent(renderMonitorContent(monitor));
    }

    private String renderMonitorContent(Monitor monitor) {
        // TODO given the Resource, replace variable placeholders in content
        return monitor.getContent();
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
     * Get a list of resource IDs for the tenant that match the given labels
     *
     * @param tenantId tenant whose resources are to be found
     * @param labels   labels to be matched
     * @return The list found
     */
    private List<String> getResourcesWithLabels(String tenantId, Map<String, String> labels) {
        List<String> emptyList = new ArrayList<>();
        String endpoint = "/api/tenant/{tenantId}/resourceLabels";
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(endpoint);
        for (Map.Entry<String, String> e : labels.entrySet()) {
            uriComponentsBuilder.queryParam(e.getKey(), e.getValue());
        }
        String uriString = uriComponentsBuilder.buildAndExpand(tenantId).toUriString();
        ResponseEntity<List<Resource>> resp = restTemplate.exchange(uriString, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Resource>>() {
                });
        if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
            log.error("get failed on: " + uriString, resp.getStatusCode());
            return emptyList;
        }
        return resp.getBody().stream()
            .map(Resource::getResourceId)
            .collect(Collectors.toList());
    }

    /**
     * Send a kafka event announcing the monitor operation.  Finds the envoys that match labels in the
     * current monitor as well as the ones that match the old labels, and sends and event to each envoy.
     *
     * @param monitor       the monitor to create the event for.
     * @param operationType the crud operation that occurred on the monitor
     * @param oldLabels     the old labels that were on the monitor before if this is an update operation
     */
    void publishMonitor(Monitor monitor, OperationType operationType, Map<String, String> oldLabels) {
        List<String> resources = getResourcesWithLabels(monitor.getTenantId(), monitor.getLabels());
        List<String> oldResources = new ArrayList<>();
        if (oldLabels != null && !oldLabels.equals(monitor.getLabels())) {
            oldResources = getResourcesWithLabels(monitor.getTenantId(), oldLabels);
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
        Map<String, String> oldLabels = monitor.getLabels();
        PropertyMapper map = PropertyMapper.get();
        map.from(updatedValues.getLabels())
                .whenNonNull()
                .to(monitor::setLabels);
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
    public void handleResourceEvent(ResourceEvent event) {
        Resource r = event.getResource();
        ResourceInfo resourceInfo = envoyResourceManagement
                .getOne(r.getTenantId(), r.getResourceId()).join();
        if (resourceInfo == null) {
            return;
        }

        List<Monitor> oldMonitors = null;
        List<Monitor> monitors = getMonitorsFromLabels(event.getResource().getLabels(), event.getResource().getTenantId());
        if (monitors == null) {
            monitors = new ArrayList<>();
        }
        if (event.getOldLabels() != null && !event.getOldLabels().equals(event.getResource().getLabels())) {
            oldMonitors = getMonitorsFromLabels(event.getOldLabels(), event.getResource().getTenantId());
        }
        if (oldMonitors != null) {
            monitors.addAll(oldMonitors);
        }
        Map<UUID, Monitor> monitorMap = new HashMap<>();
        // Eliminate duplicate monitors
        for (Monitor m : monitors) {
            monitorMap.put(m.getId(), m);
        }
        for (UUID id : monitorMap.keySet()) {
            Monitor m = monitorMap.get(id);

            // Make sure to send the event to Kafka
            sendMonitorEvent(m, event.getOperation(), resourceInfo.getEnvoyId());
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
        StringBuilder builder = new StringBuilder("SELECT * FROM monitors JOIN monitor_labels AS ml WHERE monitors.id = ml.id AND monitors.id IN ");
        builder.append("(SELECT id from monitor_labels WHERE monitors.id IN (SELECT id FROM monitors WHERE tenant_id = :tenantId) AND ");
        builder.append("monitors.id IN (SELECT search_labels.id FROM (SELECT id, COUNT(*) AS count FROM monitor_labels GROUP BY id) AS total_labels JOIN (SELECT id, COUNT(*) AS count FROM monitor_labels WHERE ");
        int i = 0;
        labels.size();
        for(Map.Entry<String, String> entry : labels.entrySet()) {
            if(i > 0) {
                builder.append(" OR ");
            }
            builder.append("(labels = :label"+ i +" AND labels_key = :labelKey" + i + ")");
            paramSource.addValue("label"+i, entry.getValue());
            paramSource.addValue("labelKey"+i, entry.getKey());
            i++;
        }
        builder.append(" GROUP BY id) AS search_labels WHERE total_labels.id = search_labels.id AND search_labels.count >= total_labels.count GROUP BY search_labels.id)");

        builder.append(") ORDER BY monitors.id");
        paramSource.addValue("i", i);

        NamedParameterJdbcTemplate namedParameterTemplate = new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource());
        List<Monitor> monitors = new ArrayList<>();
        namedParameterTemplate.query(builder.toString(), paramSource, (resultSet)->{
            String prevId = "";
            Monitor prevMonitor = null;

            do{
                if(resultSet.getString("id").compareTo(prevId) == 0) {
                    prevMonitor.getLabels().put(
                            resultSet.getString("labels_key"),
                            resultSet.getString("labels"));
                }else {
                    Map<String, String> theseLabels = new HashMap<>();
                    theseLabels.put(
                            resultSet.getString("labels_key"),
                            resultSet.getString("labels"));
                    Monitor m = new Monitor()
                            .setId(UUID.fromString(resultSet.getString("id")))
                            .setTenantId(resultSet.getString("tenant_id"))
                            .setContent(resultSet.getString("content"))
                            .setMonitorName(resultSet.getString("monitor_name"))
                            .setSelectorScope(ConfigSelectorScope.valueOf(resultSet.getString("selector_scope")))
                            .setAgentType(AgentType.valueOf(resultSet.getString("agent_type")))
                            .setLabels(theseLabels);
                    prevId = resultSet.getString("id");
                    prevMonitor = m;
                    monitors.add(m);

                }
            } while(resultSet.next());
        });

        return monitors;
    }
}
