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

import com.rackspace.salus.monitor_management.services.TestMonitorService;
import com.rackspace.salus.monitor_management.web.model.TestMonitorInput;
import com.rackspace.salus.monitor_management.web.model.TestMonitorOutput;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.concurrent.CompletableFuture;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Api
@Slf4j
public class TestMonitorApiController {

  private final TestMonitorService testMonitorService;

  @Autowired
  public TestMonitorApiController(TestMonitorService testMonitorService) {
    this.testMonitorService = testMonitorService;
  }

  @PostMapping("/tenant/{tenantId}/test-monitor")
  @ApiOperation("Initiates a test-monitor operation and blocks until the results are available")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Results contain metrics of the tested monitor and no errors occurred"),
      @ApiResponse(code = 206, message = "Results contain metrics of the tested monitor, but some errors also occurred"),
      @ApiResponse(code = 422, message = "Metrics could not be gathered due to missing conditions or a timeout")
  })
  public CompletableFuture<ResponseEntity<?>> performTestMonitor(
      @PathVariable String tenantId,
      @RequestBody @Valid TestMonitorInput input) {

    return testMonitorService
        .performTestMonitorOnResource(
            tenantId, input.getResourceId(),
            input.getDetails()
        )
        .thenApply(testMonitorOutput ->
            ResponseEntity
                .status(deriveStatus(testMonitorOutput))
                .body(testMonitorOutput));
  }

  private HttpStatus deriveStatus(TestMonitorOutput testMonitorOutput) {
    if (hasErrors(testMonitorOutput)) {
      if (testMonitorOutput.getMetrics() != null) {
        return HttpStatus.PARTIAL_CONTENT;
      } else {
        return HttpStatus.UNPROCESSABLE_ENTITY;
      }
    } else {
      return HttpStatus.OK;
    }
  }

  private boolean hasErrors(TestMonitorOutput testMonitorOutput) {
    return !CollectionUtils.isEmpty(testMonitorOutput.getErrors());
  }
}
