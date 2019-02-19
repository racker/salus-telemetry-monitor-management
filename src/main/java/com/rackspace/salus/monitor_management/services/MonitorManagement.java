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

import com.rackspace.salus.monitor_management.web.model.MonitorCreate;
import com.rackspace.salus.monitor_management.web.model.MonitorUpdate;
import com.rackspace.salus.telemetry.errors.MonitorAlreadyExists;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.Monitor;
import com.rackspace.salus.telemetry.model.Monitor_;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.validation.Valid;
import java.util.List;
import java.util.stream.Stream;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import java.util.Set;


@Slf4j
@Service
public class MonitorManagement {

    private final MonitorRepository monitorRepository;

    @PersistenceContext
    private final EntityManager entityManager;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    public MonitorManagement(MonitorRepository monitorRepository, EntityManager entityManager) {
        this.monitorRepository = monitorRepository;
        this.entityManager = entityManager;
    }

    /**
     * Gets an individual monitor object by the public facing id.
     *
     * @param tenantId  The tenant owning the monitor.
     * @param monitorId The unique value representing the monitor.
     * @return The monitor object.
     */
    public Monitor getMonitor(String tenantId, String monitorId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Monitor> cr = cb.createQuery(Monitor.class);
        Root<Monitor> root = cr.from(Monitor.class);
        cr.select(root).where(cb.and(
                cb.equal(root.get(Monitor_.tenantId), tenantId),
                cb.equal(root.get(Monitor_.monitorId), monitorId)));

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
    public Monitor createMonitor(String tenantId, @Valid MonitorCreate newMonitor) throws IllegalArgumentException, MonitorAlreadyExists {
        Monitor existing = getMonitor(tenantId, newMonitor.getMonitorId());
        if (existing != null) {
            throw new MonitorAlreadyExists(String.format("Monitor already exists with identifier %s on tenant %s",
                    newMonitor.getMonitorId(), tenantId));
        }

        Monitor monitor = new Monitor()
                .setTenantId(tenantId)
                .setMonitorId(newMonitor.getMonitorId())
                .setLabels(newMonitor.getLabels())
                .setContent(newMonitor.getContent())
                .setAgentType(AgentType.valueOf(newMonitor.getAgentType()));

        monitorRepository.save(monitor);
        return monitor;
    }

    /**
     * Update an existing monitor.
     *
     * @param tenantId      The tenant to create the entity for.
     * @param monitorId     The id of the existing monitor.
     * @param updatedValues The new monitor parameters to store.
     * @return The newly updated monitor.
     */
    public Monitor updateMonitor(String tenantId, String monitorId, @Valid MonitorUpdate updatedValues) {
        Monitor monitor = getMonitor(tenantId, monitorId);
        if (monitor == null) {
            throw new NotFoundException(String.format("No monitor found for %s on tenant %s",
                    monitorId, tenantId));
        }
        PropertyMapper map = PropertyMapper.get();
        map.from(updatedValues.getLabels())
                .whenNonNull()
                .to(monitor::setLabels);
        monitor.setContent(updatedValues.getContent());
        monitorRepository.save(monitor);
        return monitor;
    }


    /**
     * Delete a monitor.
     *
     * @param tenantId  The tenant the monitor belongs to.
     * @param monitorId The id of the monitor.
     */
    public void removeMonitor(String tenantId, String monitorId) {
        Monitor monitor = getMonitor(tenantId, monitorId);
        if (monitor != null) {
            monitorRepository.deleteById(monitor.getId());
        } else {
            throw new NotFoundException(String.format("No monitor found for %s on tenant %s",
                    monitorId, tenantId));
        }
    }

    public void handleResourceEvent(ResourceEvent event) {
        log.debug("");
        if (event.getOldLabels() != null) {
            Set<String> keys = event.getOldLabels().keySet();
            keys.removeAll(event.getResource().getLabels().keySet());//now we should have the difference of labels.
        }
        /*
            We probably want to grab three different lists of labels. Deleted labels (set difference on the oldLabels),
            added labels (set difference on the new labels), and the labels that stayed the same (possibly updated?)

            Unless we just want to start out by reading in the new list of labels and clobbering the old data that exists.

            When we do something with them this feels a little like a state machine which is really well suited to functions
            attached to enums. But its probably fine to just grab the different lists and pass them off to their respective SQL
            functions
        */


        // post kafka egress event. This will probably be handled post CRUD event, and not in this function.
    }
}
