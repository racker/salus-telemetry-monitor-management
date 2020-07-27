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

package com.rackspace.salus.monitor_management.web.client;

import com.rackspace.salus.monitor_management.web.model.*;
import com.rackspace.salus.telemetry.model.AgentType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.rackspace.salus.common.web.RemoteOperations.mapRestClientExceptions;

/**
 * This client component provides a small subset of Monitor Management REST operations that
 * can be called internally by other microservices in Salus.
 *
 * <p>
 *   It is required that the {@link RestTemplate} provided to this instance has been
 *   configured with the appropriate root URI for locating the monitor management service.
 *   The following is an example of a configuration bean that does that:
 * </p>
 *
 * <pre>
 {@literal @}Configuration
  public class RestClientsConfig {

   {@literal @}Bean
    public MonitorApi monitorApi(RestTemplateBuilder restTemplateBuilder) {
      return new MonitorApiClient(
        restTemplateBuilder
        .rootUri("http://localhost:8089")
        .build()
      );
    }
  }
  * </pre>
 *
 */
public class MonitorApiClient implements MonitorApi {

  private static final ParameterizedTypeReference<List<BoundMonitorDTO>> LIST_OF_BOUND_MONITOR
      = new ParameterizedTypeReference<>() {};
  private static final String SERVICE_NAME = "monitor-management";
  private final RestTemplate restTemplate;

  public MonitorApiClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Override
  public List<BoundMonitorDTO> getBoundMonitors(String envoyId,
                                                Map<AgentType, String> installedAgentVersions) {

    return mapRestClientExceptions(
        SERVICE_NAME,
        () ->
            Objects.requireNonNull(restTemplate.exchange(
                "/api/admin/bound-monitors",
                HttpMethod.POST,
                new HttpEntity<>(
                    new BoundMonitorsRequest()
                        .setEnvoyId(envoyId)
                        .setInstalledAgentVersions(installedAgentVersions)
                ),
                LIST_OF_BOUND_MONITOR
            ).getBody())
    );
  }

  @Override
  public DetailedMonitorOutput getPolicyMonitorById(String monitorId) {

    return mapRestClientExceptions(
        SERVICE_NAME,
        () -> {
          try {
            return restTemplate.getForObject(
                "/api/admin/policy-monitors/{monitorId}",
                DetailedMonitorOutput.class,
                monitorId
            );
          } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
              return null;
            }
            else {
              // rethrow and let mapRestClientExceptions process it
              throw e;
            }
          }
        }
    );
  }

  @Override
  public DetailedMonitorOutput createMonitor(String tenantId, DetailedMonitorInput input, MultiValueMap<String, String> headers) {
    String uriString = UriComponentsBuilder
        .fromUriString("/api/tenant/{tenantId}/monitors")
        .buildAndExpand(tenantId)
        .toUriString();

    HttpHeaders reqHeaders = new HttpHeaders();
    reqHeaders.setContentType(MediaType.APPLICATION_JSON);
    if (headers != null) {
      reqHeaders.addAll(headers);
    }

    return mapRestClientExceptions(
        SERVICE_NAME,
        () -> restTemplate.postForEntity(
            uriString,
            new HttpEntity<>(input, reqHeaders),
            DetailedMonitorOutput.class
        ).getBody());
  }

  @Override
  public TestMonitorOutput performTestMonitor(String tenantId, TestMonitorInput input, MultiValueMap<String, String> headers) {
    String uriString = UriComponentsBuilder
            .fromUriString("/api/tenant/{tenantId}/test-monitor")
            .buildAndExpand(tenantId)
            .toUriString();

    HttpHeaders reqHeaders = new HttpHeaders();
    reqHeaders.setContentType(MediaType.APPLICATION_JSON);
    if (headers != null) {
      reqHeaders.addAll(headers);
    }

    return mapRestClientExceptions(
            SERVICE_NAME,
            () -> restTemplate.postForEntity(
                    uriString,
                    new HttpEntity<>(input, reqHeaders),
                    TestMonitorOutput.class
            ).getBody());
  }
}
