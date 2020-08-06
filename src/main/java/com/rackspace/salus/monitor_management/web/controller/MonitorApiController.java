/*
 * Copyright 2020 Rackspace US, Inc.
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
 *
 */

package com.rackspace.salus.monitor_management.web.controller;

import static com.rackspace.salus.monitor_management.web.converter.PatchHelper.JSON_PATCH_TYPE;

import com.rackspace.salus.monitor_management.services.MonitorContentTranslationService;
import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.services.MonitorManagement;
import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import com.rackspace.salus.monitor_management.web.model.BoundMonitorsRequest;
import com.rackspace.salus.monitor_management.web.model.CloneMonitorRequest;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.ValidationGroups;
import com.rackspace.salus.telemetry.entities.BoundMonitor;
import com.rackspace.salus.telemetry.entities.Monitor;
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
import javax.json.JsonPatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
public class MonitorApiController {

  private MonitorManagement monitorManagement;
  private MonitorConversionService monitorConversionService;
  private final MonitorContentTranslationService monitorContentTranslationService;

  @Autowired
  public MonitorApiController(MonitorManagement monitorManagement,
                              MonitorConversionService monitorConversionService,
                              MonitorContentTranslationService monitorContentTranslationService) {
    this.monitorManagement = monitorManagement;
    this.monitorConversionService = monitorConversionService;
    this.monitorContentTranslationService = monitorContentTranslationService;
  }

  @GetMapping("/admin/monitors")
  @ApiOperation(value = "Gets all Monitors irrespective of Tenant")
  public PagedContent<DetailedMonitorOutput> getAll(Pageable pageable) {

    return PagedContent.fromPage(monitorManagement.getAllMonitors(pageable)
        .map(monitorConversionService::convertToOutput));
  }

  @PostMapping("/admin/bound-monitors")
  @ApiOperation(value = "Queries BoundMonitors attached to a particular Envoy"
      + " and translates the content for the given agent types and versions")
  public List<BoundMonitorDTO> queryBoundMonitors(@RequestBody @Validated BoundMonitorsRequest query) {
    final List<BoundMonitor> boundMonitors = monitorManagement
        .getAllBoundMonitorsByEnvoyId(query.getEnvoyId());

    return monitorContentTranslationService.translate(
        boundMonitors,
        query.getInstalledAgentVersions()
    );
  }

  @GetMapping("/tenant/{tenantId}/monitors/{uuid}")
  @ApiOperation(value = "Gets specific Monitor for Tenant")
  public DetailedMonitorOutput getById(@PathVariable String tenantId,
                                       @PathVariable UUID uuid) throws NotFoundException {
    Monitor monitor = monitorManagement.getMonitor(tenantId, uuid).orElseThrow(
        () -> new NotFoundException(String.format("No monitor found for %s on tenant %s",
            uuid, tenantId
        )));
    return monitorConversionService.convertToOutput(monitor);
  }

  @GetMapping("/tenant/{tenantId}/policy-monitors")
  @ApiOperation(value = "Gets all Policy Monitors for Tenant")
  public PagedContent<DetailedMonitorOutput> getAllPolicyMonitorsForTenant(
      @PathVariable String tenantId, Pageable pageable)
      throws NotFoundException {

    return PagedContent.fromPage(monitorManagement.getAllPolicyMonitorsForTenant(tenantId, pageable)
        .map(monitorConversionService::convertToOutput));
  }

  @GetMapping("/admin/policy-monitors")
  @ApiOperation(value = "Gets all Policy Monitors")
  public PagedContent<DetailedMonitorOutput> getAllPolicyMonitors(Pageable pageable) {
    return PagedContent.fromPage(monitorManagement.getAllPolicyMonitors(pageable)
        .map(monitorConversionService::convertToOutput));
  }

  @GetMapping("/admin/policy-monitors/{uuid}")
  @ApiOperation(value = "Get specific Policy Monitor by Id")
  public DetailedMonitorOutput getPolicyMonitorById(@PathVariable UUID uuid)
      throws NotFoundException {
    Monitor monitor = monitorManagement.getPolicyMonitor(uuid).orElseThrow(() ->
        new NotFoundException(String.format("No policy monitor found with id %s", uuid)));

    return monitorConversionService.convertToOutput(monitor);
  }

  @PutMapping("/admin/policy-monitors/{uuid}")
  @ApiOperation(value = "Updates specific Policy Monitor")
  public DetailedMonitorOutput updatePolicyMonitor(@PathVariable UUID uuid,
                                                   @Validated(ValidationGroups.Update.class)
                                                   @RequestBody final DetailedMonitorInput input)
      throws IllegalArgumentException {

    return monitorConversionService.convertToOutput(
        monitorManagement.updatePolicyMonitor(
            uuid,
            monitorConversionService.convertFromInput(Monitor.POLICY_TENANT, uuid, input)
        ));
  }

  @PatchMapping(path = "/admin/policy-monitors/{uuid}",
                consumes = {MediaType.APPLICATION_JSON_VALUE, JSON_PATCH_TYPE})
  @ApiOperation(value = "Patch specific Policy Monitor")
  public DetailedMonitorOutput patchPolicyMonitor(@PathVariable UUID uuid,
                                                  @RequestBody final JsonPatch input)
      throws IllegalArgumentException {

    Monitor monitor = monitorManagement.getPolicyMonitor(uuid).orElseThrow(() ->
        new NotFoundException(String.format("No policy monitor found with id %s", uuid)));

    return monitorConversionService.convertToOutput(
        monitorManagement.updatePolicyMonitor(
            uuid,
            monitorConversionService.convertFromPatchInput(Monitor.POLICY_TENANT, uuid, monitor, input),
            true
        ));
  }

  @PostMapping("/admin/policy-monitors")
  @ResponseStatus(HttpStatus.CREATED)
  @ApiOperation(value = "Creates new Policy Monitor")
  @ApiResponses(value = {@ApiResponse(code = 201, message = "Successfully Created Policy Monitor")})
  public DetailedMonitorOutput createPolicyMonitor(
      @Validated(ValidationGroups.Create.class)
      @RequestBody final DetailedMonitorInput input)
      throws IllegalArgumentException {
    return monitorConversionService.convertToOutput(
        monitorManagement.createPolicyMonitor(
            monitorConversionService.convertFromInput(Monitor.POLICY_TENANT, null, input)));
  }

  @PostMapping("/admin/clone-monitor")
  @ResponseStatus(HttpStatus.CREATED)
  @ApiOperation(value = "Clones a monitor from one tenant to another")
  @ApiResponses(value = {@ApiResponse(code = 201, message = "Successfully Cloned Monitor")})
  public DetailedMonitorOutput cloneMonitor(@RequestBody final CloneMonitorRequest input)
      throws IllegalArgumentException {
    return monitorConversionService.convertToOutput(
        monitorManagement.cloneMonitor(
            input.getOriginalTenant(),
            input.getNewTenant(),
            input.getMonitorId()
        ));
  }

  @DeleteMapping("/admin/policy-monitors/{uuid}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiOperation(value = "Deletes specific Policy Monitor")
  @ApiResponses(value = {@ApiResponse(code = 204, message = "Policy Monitor Deleted")})
  public void deletePolicyMonitor(@PathVariable UUID uuid) {
    monitorManagement.removePolicyMonitor(uuid);
  }

  @GetMapping("/tenant/{tenantId}/bound-monitors")
  public PagedContent<BoundMonitorDTO> getBoundMonitorsForTenant(@PathVariable String tenantId,
                                                                 @RequestParam(required = false) String resourceId,
                                                                 @RequestParam(required = false) UUID monitorId,
                                                                 Pageable pageable) {

    if (StringUtils.isNotBlank(resourceId) && monitorId != null) {
      return PagedContent.fromPage(
          monitorManagement
              .getAllBoundMonitorsByResourceIdAndMonitorIdAndTenantId(resourceId, monitorId, tenantId, pageable)
              .map(monitorConversionService::convertToBoundMonitorDTO)
      );
    } else if (StringUtils.isNotBlank(resourceId)) {
      return PagedContent.fromPage(
          monitorManagement
              .getAllBoundMonitorsByResourceIdAndTenantId(resourceId, tenantId, pageable)
              .map(monitorConversionService::convertToBoundMonitorDTO)
      );
    } else if (monitorId != null) {
      return PagedContent.fromPage(
          monitorManagement
              .getAllBoundMonitorsByMonitorIdAndTenantId(monitorId, tenantId, pageable)
              .map(monitorConversionService::convertToBoundMonitorDTO)
      );
    }
    return PagedContent.fromPage(
        monitorManagement.getAllBoundMonitorsByTenantId(tenantId, pageable)
            .map(monitorConversionService::convertToBoundMonitorDTO)
    );
  }

  @GetMapping("/tenant/{tenantId}/monitors")
  @ApiOperation(value = "Gets all Monitors for Tenant")
  public PagedContent<DetailedMonitorOutput> getAllForTenant(@PathVariable String tenantId,
                                                             Pageable pageable) {

    return PagedContent.fromPage(monitorManagement.getMonitors(tenantId, pageable)
        .map(monitorConversionService::convertToOutput));
  }

  @PostMapping("/tenant/{tenantId}/monitors")
  @ResponseStatus(HttpStatus.CREATED)
  @ApiOperation(value = "Creates new Monitor for Tenant")
  @ApiResponses(value = {@ApiResponse(code = 201, message = "Successfully Created Monitor")})
  public DetailedMonitorOutput create(@PathVariable String tenantId,
                                      @Validated(ValidationGroups.Create.class)
                                      @RequestBody final DetailedMonitorInput input)
      throws IllegalArgumentException {

    return monitorConversionService.convertToOutput(
        monitorManagement.createMonitor(
            tenantId,
            monitorConversionService.convertFromInput(tenantId, null, input)
        ));
  }

  @PutMapping("/tenant/{tenantId}/monitors/{uuid}")
  @ApiOperation(value = "Updates specific Monitor for Tenant")
  public DetailedMonitorOutput update(@PathVariable String tenantId,
                                      @PathVariable UUID uuid,
                                      @Validated(ValidationGroups.Update.class)
                                      @RequestBody final DetailedMonitorInput input)
      throws IllegalArgumentException {

    return monitorConversionService.convertToOutput(
        monitorManagement.updateMonitor(
            tenantId,
            uuid,
            monitorConversionService.convertFromInput(tenantId, uuid, input)
        ));
  }

  @PatchMapping(path = "/tenant/{tenantId}/monitors/{uuid}",
                consumes = {MediaType.APPLICATION_JSON_VALUE, JSON_PATCH_TYPE})
  @ApiOperation(value = "Updates specific Monitor for Tenant")
  public DetailedMonitorOutput patch(@PathVariable String tenantId,
      @PathVariable UUID uuid,
      @RequestBody final JsonPatch input)
      throws IllegalArgumentException {

    Monitor monitor = monitorManagement.getMonitor(tenantId, uuid).orElseThrow(
        () -> new NotFoundException(String.format("No monitor found for %s on tenant %s",
            uuid, tenantId
        )));

    return monitorConversionService.convertToOutput(
        monitorManagement.updateMonitor(
            tenantId,
            uuid,
            monitorConversionService.convertFromPatchInput(tenantId, uuid, monitor, input),
            true
        ));
  }

  @DeleteMapping("/tenant/{tenantId}/monitors/{uuid}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiOperation(value = "Deletes specific Monitor for Tenant")
  @ApiResponses(value = {@ApiResponse(code = 204, message = "Resource Deleted")})
  public void delete(@PathVariable String tenantId,
                     @PathVariable UUID uuid) {
    monitorManagement.removeMonitor(tenantId, uuid);
  }

  @GetMapping("/tenant/{tenantId}/monitor-labels")
  @ApiOperation(value = "Gets all Monitors that match labels. All labels must match to retrieve relevant Monitors.")
  public PagedContent<DetailedMonitorOutput> getMonitorsWithLabels(@PathVariable String tenantId,
                                                                   @RequestBody Map<String, String> labels,
                                                                   Pageable pageable) {
    return PagedContent.fromPage(monitorManagement.getMonitorsFromLabels(labels, tenantId, pageable)
        .map(monitor -> monitorConversionService.convertToOutput(monitor)));
  }

  @GetMapping("/tenant/{tenantId}/monitor-label-selectors")
  @ApiOperation("Lists the label selector keys and the values for each that are currently in use on monitors")
  public MultiValueMap<String, String> getMonitorLabelSelectors(@PathVariable String tenantId) {
    return monitorManagement.getTenantMonitorLabelSelectors(tenantId);
  }

  @GetMapping("/tenant/{tenantId}/search")
  @ApiOperation("Finds all monitors that match the searchCriteria either in the monitorName or the ID. Dynamic sorting is not supported and will be ignored.")
  public PagedContent<DetailedMonitorOutput> getMonitorsBySearchString(@PathVariable String tenantId,
      @RequestParam("q") String searchCriteria,
      Pageable pageable) {
    // Because the search is happening in a native query sorting is not supported and causes an exception if the Pageable has a sorting parameter
    // So this is ignoring the sorting provided
    Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    return PagedContent.fromPage(monitorManagement.getMonitorsBySearchString(tenantId, searchCriteria, page)
      .map(monitor -> monitorConversionService.convertToOutput(monitor)));
  }

  @DeleteMapping("/admin/tenant/{tenantId}/monitors")
  @ApiOperation("Deletes all monitors for a particular tenant")
  public void deleteAllTenantMonitors(@PathVariable String tenantId) {
    monitorManagement.removeAllTenantMonitors(tenantId);
  }
}
