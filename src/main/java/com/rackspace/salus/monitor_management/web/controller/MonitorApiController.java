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
import com.rackspace.salus.monitor_management.entities.Monitor;
import com.rackspace.salus.monitor_management.repositories.BoundMonitorRepository;
import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.services.MonitorManagement;
import com.rackspace.salus.monitor_management.web.client.MonitorApi;
import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.ValidationGroups;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.model.PagedContent;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
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

@Slf4j
@RestController
@RequestMapping("/api")
@Api(description = "Monitor operations", authorizations = {
        @Authorization(value = "repose_auth",
                scopes = {
                        @AuthorizationScope(scope = "write:monitor", description = "modify Monitors in your account"),
                        @AuthorizationScope(scope = "read:monitor", description = "read your Monitors"),
                        @AuthorizationScope(scope = "delete:monitor", description = "delete your Monitors")
                })
})
public class MonitorApiController implements MonitorApi {

    private MonitorManagement monitorManagement;
    private final BoundMonitorRepository boundMonitorRepository;
    private MonitorConversionService monitorConversionService;

    @Autowired
    public MonitorApiController(MonitorManagement monitorManagement,
                                BoundMonitorRepository boundMonitorRepository,
                                MonitorConversionService monitorConversionService) {
        this.monitorManagement = monitorManagement;
        this.boundMonitorRepository = boundMonitorRepository;
        this.monitorConversionService = monitorConversionService;
    }

    @GetMapping("/monitors")
    @ApiOperation(value = "Gets all Monitors irrespective of Tenant")
    public Page<DetailedMonitorOutput> getAll(@RequestParam(defaultValue = "100") int size,
                                @RequestParam(defaultValue = "0") int page) {

        return monitorManagement.getAllMonitors(PageRequest.of(page, size))
                .map(monitorConversionService::convertToOutput);

    }

    @Override
    @GetMapping("/boundMonitors/{envoyId}")
    @ApiOperation(value = "Gets all BoundMonitors attached to a particular Envoy")
    public List<BoundMonitorDTO> getBoundMonitors(@PathVariable String envoyId) {
        return boundMonitorRepository.findAllByEnvoyId(envoyId).stream()
            .map(BoundMonitor::toDTO)
            .collect(Collectors.toList());
    }

    @GetMapping("/tenant/{tenantId}/monitors/{uuid}")
    @ApiOperation(value = "Gets specific Monitor for Tenant")
    public DetailedMonitorOutput getById(@PathVariable String tenantId,
                                         @PathVariable UUID uuid) throws NotFoundException {
        Monitor monitor = monitorManagement.getMonitor(tenantId, uuid).orElseThrow(
                () -> new NotFoundException(String.format("No monitor found for %s on tenant %s",
                        uuid, tenantId)));
        return monitorConversionService.convertToOutput(monitor);
    }

    @GetMapping("/tenant/{tenantId}/boundMonitors")
    public PagedContent<BoundMonitorDTO> getBoundMonitorsForTenant(@PathVariable String tenantId,
                                                           Pageable pageable) {
        return PagedContent.fromPage(
            boundMonitorRepository.findAllByMonitor_TenantId(tenantId, pageable)
            .map(BoundMonitor::toDTO)
        );
    }

    @GetMapping("/tenant/{tenantId}/monitors")
    @ApiOperation(value = "Gets all Monitors for Tenant")
    public Page<DetailedMonitorOutput> getAllForTenant(@PathVariable String tenantId,
                                         @RequestParam(defaultValue = "100") int size,
                                         @RequestParam(defaultValue = "0") int page) {

        return monitorManagement.getMonitors(tenantId, PageRequest.of(page, size))
                .map(monitorConversionService::convertToOutput);
    }

    @PostMapping("/tenant/{tenantId}/monitors")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiOperation(value = "Creates new Monitor for Tenant")
    @ApiResponses(value = { @ApiResponse(code = 201, message = "Successfully Created Monitor")})
    public DetailedMonitorOutput create(@PathVariable String tenantId,
                                        @Validated(ValidationGroups.Create.class) @RequestBody
                                        final DetailedMonitorInput input)
            throws IllegalArgumentException {

        return monitorConversionService.convertToOutput(
                monitorManagement.createMonitor(
                        tenantId,
                        monitorConversionService.convertFromInput(input)));
    }

    @PutMapping("/tenant/{tenantId}/monitors/{uuid}")
    @ApiOperation(value = "Updates specific Monitor for Tenant")
    public DetailedMonitorOutput update(@PathVariable String tenantId,
                          @PathVariable UUID uuid,
                          @Validated @RequestBody final DetailedMonitorInput input) throws IllegalArgumentException {

        return monitorConversionService.convertToOutput(
                monitorManagement.updateMonitor(
                        tenantId,
                        uuid,
                        monitorConversionService.convertFromInput(input)));
    }

    @DeleteMapping("/tenant/{tenantId}/monitors/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiOperation(value = "Deletes specific Monitor for Tenant")
    @ApiResponses(value = { @ApiResponse(code = 204, message = "Resource Deleted")})
    public void delete(@PathVariable String tenantId,
                       @PathVariable UUID uuid) {
        monitorManagement.removeMonitor(tenantId, uuid);
    }

    @GetMapping("/tenant/{tenantId}/monitorLabels")
    @ApiOperation(value = "Gets all Monitors that match labels. All labels must match to retrieve relevant Monitors.")
    public List<DetailedMonitorOutput> getMonitorsWithLabels(@PathVariable String tenantId,
                                                 @RequestBody Map<String, String> labels) {
        return monitorManagement.getMonitorsFromLabels(labels, tenantId).stream()
            .map(monitor -> monitorConversionService.convertToOutput(monitor))
            .collect(Collectors.toList());

    }
}
