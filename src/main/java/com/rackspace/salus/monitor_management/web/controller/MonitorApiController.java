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

import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import com.rackspace.salus.monitor_management.repositories.BoundMonitorRepository;
import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.services.MonitorManagement;
import com.rackspace.salus.monitor_management.web.client.MonitorApi;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.telemetry.model.Monitor;
import com.rackspace.salus.telemetry.model.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api")
public class MonitorApiController implements MonitorApi {

    private MonitorManagement monitorManagement;
    private final BoundMonitorRepository boundMonitorRepository;
    private TaskExecutor taskExecutor;
    private MonitorConversionService monitorConversionService;

    @Autowired
    public MonitorApiController(MonitorManagement monitorManagement, BoundMonitorRepository boundMonitorRepository,
                                TaskExecutor taskExecutor, MonitorConversionService monitorConversionService) {
        this.monitorManagement = monitorManagement;
        this.boundMonitorRepository = boundMonitorRepository;
        this.taskExecutor = taskExecutor;
        this.monitorConversionService = monitorConversionService;
    }

    @GetMapping("/monitors")
    public Page<DetailedMonitorOutput> getAll(@RequestParam(defaultValue = "100") int size,
                                @RequestParam(defaultValue = "0") int page) {

        return monitorManagement.getAllMonitors(PageRequest.of(page, size))
                .map(monitorConversionService::convertToOutput);

    }

    @GetMapping("/monitorsAsStream")
    public SseEmitter getAllAsStream() {
        SseEmitter emitter = new SseEmitter();
        Stream<Monitor> monitors = monitorManagement.getMonitorsAsStream();
        taskExecutor.execute(() -> {
            monitors.forEach(r -> {
                try {
                    emitter.send(r);
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            });
            emitter.complete();
        });
        return emitter;
    }

    @Override
    @GetMapping("/boundMonitors/{envoyId}")
    public List<BoundMonitor> getBoundMonitors(@PathVariable String envoyId) {
        return boundMonitorRepository.findByEnvoyId(envoyId);
    }

    @GetMapping("/tenant/{tenantId}/monitors/{uuid}")
    public DetailedMonitorOutput getById(@PathVariable String tenantId,
                                         @PathVariable UUID uuid) throws NotFoundException {
        Monitor monitor = monitorManagement.getMonitor(tenantId, uuid);
        if (monitor == null) {
            throw new NotFoundException(String.format("No monitor found for %s on tenant %s",
                    uuid, tenantId));
        }
        return monitorConversionService.convertToOutput(monitor);
    }

    @GetMapping("/tenant/{tenantId}/monitors")
    public Page<DetailedMonitorOutput> getAllForTenant(@PathVariable String tenantId,
                                         @RequestParam(defaultValue = "100") int size,
                                         @RequestParam(defaultValue = "0") int page) {

        return monitorManagement.getMonitors(tenantId, PageRequest.of(page, size))
                .map(monitorConversionService::convertToOutput);
    }

    @PostMapping("/tenant/{tenantId}/monitors")
    @ResponseStatus(HttpStatus.CREATED)
    public DetailedMonitorOutput create(@PathVariable String tenantId,
                                        @Valid @RequestBody final DetailedMonitorInput input)
            throws IllegalArgumentException {

        return monitorConversionService.convertToOutput(
                monitorManagement.createMonitor(
                        tenantId,
                        monitorConversionService.convertFromInput(input)));
    }

    @PutMapping("/tenant/{tenantId}/monitors/{uuid}")
    public DetailedMonitorOutput update(@PathVariable String tenantId,
                          @PathVariable UUID uuid,
                          @Valid @RequestBody final DetailedMonitorInput input) throws IllegalArgumentException {

        return monitorConversionService.convertToOutput(
                monitorManagement.updateMonitor(
                        tenantId,
                        uuid,
                        monitorConversionService.convertFromInput(input)));
    }

    @DeleteMapping("/tenant/{tenantId}/monitors/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String tenantId,
                       @PathVariable UUID uuid) {
        monitorManagement.removeMonitor(tenantId, uuid);
    }

    @GetMapping("/tenant/{tenantId}/monitorLabels")
    public List<Monitor> getMonitorsWithLabels(@PathVariable String tenantId,
                                                 @RequestBody Map<String, String> labels) {
        return monitorManagement.getMonitorsFromLabels(labels, tenantId);

    }
}
