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
import com.rackspace.salus.monitor_management.web.model.MonitorCreate;
import com.rackspace.salus.monitor_management.web.model.MonitorUpdate;
import com.rackspace.salus.telemetry.errors.AlreadyExistsException;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.messaging.MonitorEvent;
import com.rackspace.salus.telemetry.messaging.OperationType;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.*;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
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

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.validation.Valid;
import java.util.*;
import java.util.stream.Stream;


@Slf4j
@Service
public class MonitorManagement {
    private final MonitorEventProducer monitorEventProducer;

    private final MonitorRepository monitorRepository;

    private final RestTemplate restTemplate;

    @PersistenceContext
    private final EntityManager entityManager;

    private final EnvoyResourceManagement envoyResourceManagement;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    public MonitorManagement(MonitorRepository monitorRepository, EntityManager entityManager,
                             EnvoyResourceManagement envoyResourceManagement,
                             MonitorEventProducer monitorEventProducer,
                             RestTemplateBuilder restTemplateBuilder,
                             ServicesProperties servicesProperties) {
        this.monitorRepository = monitorRepository;
        this.entityManager = entityManager;
        this.envoyResourceManagement = envoyResourceManagement;
        this.monitorEventProducer = monitorEventProducer;
        this.restTemplate = restTemplateBuilder.rootUri(servicesProperties.getResourceManagementUrl()).build();

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
    public Monitor createMonitor(String tenantId, @Valid MonitorCreate newMonitor) throws IllegalArgumentException, AlreadyExistsException {
        Monitor monitor = new Monitor()
                .setTenantId(tenantId)
                .setMonitorName(newMonitor.getMonitorName())
                .setLabels(newMonitor.getLabels())
                .setContent(newMonitor.getContent())
                .setAgentType(newMonitor.getAgentType())
                .setSelectorScope(newMonitor.getSelectorScope())
                .setTargetTenant(newMonitor.getTargetTenant());


        monitorRepository.save(monitor);
        publishMonitor(monitor, OperationType.CREATE, null);
        return monitor;
    }

    /**
     * Get a list of resources for the tenant that match the given labels
     *
     * @param tenantId tenant whose resources are to be found
     * @param labels   labels to be matched
     * @return The list found
     */
    private List<Resource> getResourcesWithLabels(String tenantId, Map<String, String> labels) {
        List<Resource> emptyList = new ArrayList<>();
        String endpoint = "/api/tenant/{tenantId}/resourceLabels";
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(endpoint);
        for (Map.Entry<String, String> e : labels.entrySet()) {
            uriComponentsBuilder.queryParam(e.getKey(), e.getValue());
        }
        String uriString = uriComponentsBuilder.buildAndExpand(tenantId).toUriString();
        ResponseEntity<List<Resource>> resp = restTemplate.exchange(uriString, HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Resource>>() {
                });
        if (resp.getStatusCode() != HttpStatus.OK) {
            log.error("get failed on: " + uriString, resp.getStatusCode());
            return emptyList;
        }
        return resp.getBody();
    }

    /**
     * Send a kafka event announcing the monitor operation.  Finds the envoys that match labels in the
     * current monitor as well as the ones that match the old labels, and sends and event to each envoy.
     *
     * @param monitor       the monitor to create the event for.
     * @param operationType the crud operation that occurred on the monitor
     * @param oldLabels     the old labels that were on the monitor before if this is an update operation
     */
    public void publishMonitor(Monitor monitor, OperationType operationType, Map<String, String> oldLabels) {
        List<Resource> resources = getResourcesWithLabels(monitor.getTenantId(), monitor.getLabels());
        List<Resource> oldResources = new ArrayList<>();
        if (oldLabels != null && !oldLabels.equals(monitor.getLabels())) {
            oldResources = getResourcesWithLabels(monitor.getTenantId(), oldLabels);
        }
        resources.addAll(oldResources);
        Map<String, Resource> resourceMap = new HashMap<>();
        // Eliminate duplicate resources
        for (Resource r : resources) {
            resourceMap.put(r.getResourceId(), r);
        }
        for (String id : resourceMap.keySet()) {
            Resource r = resourceMap.get(id);
            ResourceInfo resourceInfo = envoyResourceManagement
                    .getOne(monitor.getTenantId(), r.getResourceId()).join().get(0);
            if (resourceInfo != null) {
                MonitorEvent monitorEvent = new MonitorEvent()
                        .setFromMonitor(monitor)
                        .setOperationType(operationType)
                        .setEnvoyId(resourceInfo.getEnvoyId());
                monitorEventProducer.sendMonitorEvent(monitorEvent);
            }
        }
    }

    /**
     * Update an existing monitor.
     *
     * @param tenantId      The tenant to create the entity for.
     * @param id            The id of the existing monitor.
     * @param updatedValues The new monitor parameters to store.
     * @return The newly updated monitor.
     */
    public Monitor updateMonitor(String tenantId, UUID id, @Valid MonitorUpdate updatedValues) {
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
        monitor.setTargetTenant(updatedValues.getTargetTenant());
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

    @SuppressWarnings("unused")
    public List<Monitor> getMonitorsWithLabels(String tenantId, Map<String, String> labels) {
        // Adam's code
        return null;
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
                .getOne(r.getTenantId(), r.getResourceId()).join().get(0);
        if (resourceInfo == null) {
            return;
        }

        List<Monitor> oldMonitors = null;
        List<Monitor> monitors = getMonitorsWithLabels(event.getResource().getTenantId(), event.getResource().getLabels());
        if (monitors == null) {
            monitors = new ArrayList<>();
        }
        if (event.getOldLabels() != null && !event.getOldLabels().equals(event.getResource().getLabels())) {
            oldMonitors = getMonitorsWithLabels(event.getResource().getTenantId(), event.getOldLabels());
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
            MonitorEvent monitorEvent = new MonitorEvent()
                    .setFromMonitor(m)
                    .setOperationType(event.getOperation())
                    .setEnvoyId(resourceInfo.getEnvoyId());

            monitorEventProducer.sendMonitorEvent(monitorEvent);
        }
    }

    /**
     * takes in a Mapping of labels for a tenant, builds and runs the query to match to those labels
     * @param labels the labels that we need to match to
     * @param tenantId The tenant associated to the resource
     * @return the list of Monitor's that match the labels
     */
    public List<Monitor> getMonitorsFromLabels(Map<String, String> labels, String tenantId, MATCH_OPTIONS option) {
        /*
        SELECT * FROM resources where id IN (SELECT id from resource_labels WHERE id IN (select id from resources)
        AND ((labels = "windows" AND labels_key = "os") OR (labels = "prod" AND labels_key="env")) GROUP BY id
        HAVING COUNT(id) = 2) AND tenant_id = "aaaad";
        */


        MapSqlParameterSource paramSource = new MapSqlParameterSource();
        paramSource.addValue("tenantId", tenantId);//AS r JOIN resource_labels AS rl
        StringBuilder builder = new StringBuilder("SELECT * FROM monitors JOIN monitor_labels AS ml WHERE monitors.id = ml.id AND monitors.id IN ");
        builder.append("(SELECT id from monitor_labels WHERE id IN ( SELECT id FROM monitors WHERE tenant_id = :tenantId) AND ");

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

        builder.append(" GROUP BY id");
        //HAVING COUNT(id) = :i)
        switch(option) {
            case MATCH_ALL:
                builder.append("HAVING COUNT(id) = :i");
        }
        builder.append(") ORDER BY monitors.id");
        paramSource.addValue("i", i);

        NamedParameterJdbcTemplate namedParameterTemplate = new NamedParameterJdbcTemplate(jdbcTemplate.getDataSource());
        List<Monitor> monitors = new ArrayList<>();
        namedParameterTemplate.query(builder.toString(), paramSource, (resultSet)->{

            long prevId = 0;
            Monitor prevMonitor = null;

            do{
                if(resultSet.getLong("id") == prevId) {
                    prevMonitor.getLabels().put(
                            resultSet.getString("labels_key"),
                            resultSet.getString("labels"));
                }else {
                    Map<String, String> theseLabels = new HashMap<String, String>();
                    theseLabels.put(
                            resultSet.getString("labels_key"),
                            resultSet.getString("labels"));
                    Monitor m = new Monitor()
                            .setId(UUID.fromString(resultSet.getString("id")))
                            .setTenantId(resultSet.getString("tenant_id"))
                            .setContent(resultSet.getString("content"))
                            //.setMonitorName(resultSet.getString("monitor_name"))
                            //.setSelectorScope(ConfigSelectorScope.valueOf(resultSet.getInt("selector_scope")))
                            //.setAgentType(AgentType.valueOf(resultSet.getInt("agent_type")))
                            .setTargetTenant(resultSet.getString("target_tenant"))
                            .setLabels(theseLabels);
                    prevId = resultSet.getLong("id");
                    prevMonitor = m;
                    monitors.add(m);

                }
            } while(resultSet.next());
        });

        return monitors;
    }

    public static enum MATCH_OPTIONS {
        MATCH_SOME, MATCH_ALL;
    }
}
