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
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.Monitor;
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

    // @GetMapping("/envoys")
    // public SseEmitter getAllWithPresenceMonitoringAsStream() {
    //     SseEmitter emitter = new SseEmitter();
    //     Stream<Monitor> monitorsWithEnvoys = monitorManagement.getMonitors(true);
    //     taskExecutor.execute(() -> {
    //         monitorsWithEnvoys.forEach(r -> {
    //             try {
    //                 emitter.send(r);
    //             } catch (IOException e) {
    //                 emitter.completeWithError(e);
    //             }
    //         });
    //         emitter.complete();
    //     });
    //     return emitter;
    // }

    // @GetMapping("/tenant/{tenantId}/monitors/{monitorId}")
    // public Monitor getByMonitorId(@PathVariable String tenantId,
    //                                 @PathVariable String monitorId) throws NotFoundException {

    //     Monitor monitor = monitorManagement.getMonitor(tenantId, monitorId);
    //     if (monitor == null) {
    //         throw new NotFoundException(String.format("No monitor found for %s on tenant %s",
    //                 monitorId, tenantId));
    //     }
    //     return monitor;
    // }

    // @GetMapping("/tenant/{tenantId}/monitors")
    // public Page<Monitor>  getAllForTenant(@PathVariable String tenantId,
    //                                @RequestParam(defaultValue = "100") int size,
    //                                @RequestParam(defaultValue = "0") int page) {

    //     return monitorManagement.getMonitors(tenantId, PageRequest.of(page, size));
    // }

    @PostMapping("/tenant/{tenantId}/monitors")
    @ResponseStatus(HttpStatus.CREATED)
    public Monitor create(@PathVariable String tenantId)
    //                       @Valid @RequestBody final MonitorCreate input)
            throws IllegalArgumentException {
        return monitorManagement.saveAndPublishMonitor(new Monitor().setContent("hig1"));
    }

    // @PutMapping("/tenant/{tenantId}/monitors/{monitorId}")
    // public Monitor update(@PathVariable String tenantId,
    //                        @PathVariable String monitorId,
    //                        @Valid @RequestBody final MonitorUpdate input) throws IllegalArgumentException {
    //     return monitorManagement.updateMonitor(tenantId, monitorId, input);
    // }

    // @DeleteMapping("/tenant/{tenantId}/monitors/{monitorId}")
    // @ResponseStatus(HttpStatus.NO_CONTENT)
    // public void delete(@PathVariable String tenantId,
    //                    @PathVariable String monitorId) {
    //     monitorManagement.removeMonitor(tenantId, monitorId);
    // }
}
