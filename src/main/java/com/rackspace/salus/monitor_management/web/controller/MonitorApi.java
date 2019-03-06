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

import com.rackspace.salus.monitor_management.services.MonitorManagement;
import com.rackspace.salus.monitor_management.web.model.MonitorCreate;
import com.rackspace.salus.monitor_management.web.model.MonitorUpdate;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.Monitor;
import com.rackspace.salus.telemetry.model.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@RestController
@RequestMapping("/api")
public class MonitorApi {

    private MonitorManagement monitorManagement;
    private TaskExecutor taskExecutor;

    @Autowired
    public MonitorApi(MonitorManagement monitorManagement, TaskExecutor taskExecutor) {
        this.monitorManagement = monitorManagement;
        this.taskExecutor = taskExecutor;
    }

    @GetMapping("/monitors")
    public Page<Monitor> getAll(@RequestParam(defaultValue = "100") int size,
                                @RequestParam(defaultValue = "0") int page) {

        return monitorManagement.getAllMonitors(PageRequest.of(page, size));

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

    @GetMapping("/tenant/{tenantId}/monitors/{uuid}")
    public Monitor getById(@PathVariable String tenantId,
                                  @PathVariable UUID uuid) throws NotFoundException {
        Monitor monitor = monitorManagement.getMonitor(tenantId, uuid);
        if (monitor == null) {
            throw new NotFoundException(String.format("No monitor found for %s on tenant %s",
                    uuid, tenantId));
        }
        return monitor;
    }

    @GetMapping("/tenant/{tenantId}/monitors")
    public Page<Monitor> getAllForTenant(@PathVariable String tenantId,
                                         @RequestParam(defaultValue = "100") int size,
                                         @RequestParam(defaultValue = "0") int page) {

        return monitorManagement.getMonitors(tenantId, PageRequest.of(page, size));
    }

    @PostMapping("/tenant/{tenantId}/monitors")
    @ResponseStatus(HttpStatus.CREATED)
    public Monitor create(@PathVariable String tenantId,
                          @Valid @RequestBody final MonitorCreate input)
            throws IllegalArgumentException {
        return monitorManagement.createMonitor(tenantId, input);
    }

    @PutMapping("/tenant/{tenantId}/monitors/{uuid}")
    public Monitor update(@PathVariable String tenantId,
                          @PathVariable UUID uuid,
                          @Valid @RequestBody final MonitorUpdate input) throws IllegalArgumentException {
        return monitorManagement.updateMonitor(tenantId, uuid, input);
    }

    @DeleteMapping("/tenant/{tenantId}/monitors/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String tenantId,
                       @PathVariable UUID uuid) {
        monitorManagement.removeMonitor(tenantId, uuid);
    }

    @GetMapping("/tenant/{tenantId}/monitorLabels")
    public List<Monitor> getResourcesWithLabels(@PathVariable String tenantId,
                                                 @RequestBody Map<String, String> labels) {
        return monitorManagement.getMonitorsFromLabels(labels, tenantId, MonitorManagement.MatchOptions.MATCH_ALL);

    }
}
