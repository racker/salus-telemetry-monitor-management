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

import com.fasterxml.jackson.annotation.JsonView;
import com.rackspace.salus.monitor_management.services.MonitorContentTranslationService;
import com.rackspace.salus.monitor_management.web.model.MonitorTranslationOperatorCreate;
import com.rackspace.salus.monitor_management.web.model.MonitorTranslationOperatorDTO;
import com.rackspace.salus.telemetry.model.PagedContent;
import com.rackspace.salus.telemetry.model.View;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import java.util.UUID;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Api(value = "Monitor translation operations", authorizations = {
    @Authorization(value = "repose_auth",
        scopes = {
            @AuthorizationScope(scope = "write:monitor-translation", description = "modifies monitor translations"),
            @AuthorizationScope(scope = "read:monitor-translation", description = "reads monitor translations"),
            @AuthorizationScope(scope = "delete:monitor-translation", description = "deletes monitor translations")
        })
})
public class MonitorTranslationApiController {

  private final MonitorContentTranslationService monitorContentTranslationService;

  @Autowired
  public MonitorTranslationApiController(
      MonitorContentTranslationService monitorContentTranslationService) {
    this.monitorContentTranslationService = monitorContentTranslationService;
  }

  @GetMapping("/admin/monitor-translations")
  @ApiOperation("Gets all monitor translation operators")
  @JsonView(View.Admin.class)
  public PagedContent<MonitorTranslationOperatorDTO> getAll(Pageable pageable) {
    return PagedContent.fromPage(monitorContentTranslationService.getAll(pageable))
        .map(MonitorTranslationOperatorDTO::new);
  }

  @GetMapping("/admin/monitor-translations/{id}")
  @ApiOperation("Gets a specific monitor translation operator")
  @JsonView(View.Admin.class)
  public MonitorTranslationOperatorDTO getById(@PathVariable UUID id) {
    return new MonitorTranslationOperatorDTO(
        monitorContentTranslationService.getById(id)
    );
  }

  @PostMapping("/admin/monitor-translations")
  @ResponseStatus(HttpStatus.CREATED)
  @ApiOperation("Create a new monitor translation operator")
  @ApiResponses(value = {@ApiResponse(code = 201, message = "Successfully created")})
  @JsonView(View.Admin.class)
  public MonitorTranslationOperatorDTO create(@RequestBody @Valid MonitorTranslationOperatorCreate in) {
    return new MonitorTranslationOperatorDTO(
        monitorContentTranslationService.create(in)
    );
  }

  @DeleteMapping("/admin/monitor-translations/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiOperation("Delete a monitor translation operator")
  @ApiResponses(value = {@ApiResponse(code = 204, message = "Successfully deleted")})
  @JsonView(View.Admin.class)
  public void delete(@PathVariable UUID id) {
    monitorContentTranslationService.delete(id);
  }
}
