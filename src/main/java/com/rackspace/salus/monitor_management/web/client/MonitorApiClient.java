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
 */

package com.rackspace.salus.monitor_management.web.client;

import static com.rackspace.salus.common.web.RemoteOperations.mapRestClientExceptions;

import com.rackspace.salus.monitor_management.web.model.BoundMonitorDTO;
import com.rackspace.salus.monitor_management.web.model.BoundMonitorsRequest;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.TestMonitorInput;
import com.rackspace.salus.monitor_management.web.model.TestMonitorResult;
import com.rackspace.salus.monitor_management.web.model.TranslateMonitorContentRequest;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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
  public DetailedMonitorOutput getMonitorTemplateById(String monitorId) {

    return mapRestClientExceptions(
        SERVICE_NAME,
        () -> {
          try {
            return restTemplate.getForObject(
                "/api/admin/monitor-templates/{monitorId}",
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
  public TestMonitorResult performTestMonitor(String tenantId, TestMonitorInput input) {
    String uriString = UriComponentsBuilder
            .fromUriString("/api/tenant/{tenantId}/test-monitor")
            .buildAndExpand(tenantId)
            .toUriString();

    return mapRestClientExceptions(
            SERVICE_NAME,
            () -> restTemplate.postForEntity(
                    uriString,
                    new HttpEntity<>(input),
                    TestMonitorResult.class
            ).getBody());
  }

  @Override
  public String translateMonitorContent(AgentType agentType, String agentVersion,
                                        MonitorType monitorType,
                                        ConfigSelectorScope scope,
                                        String content) {
    return mapRestClientExceptions(
        SERVICE_NAME,
        () -> restTemplate.postForEntity(
            "/api/admin/translate-monitor-content",
            new HttpEntity<>(
                new TranslateMonitorContentRequest()
                    .setAgentType(agentType)
                    .setAgentVersion(agentVersion)
                    .setMonitorType(monitorType)
                    .setScope(scope)
                    .setContent(content)
            ),
            String.class
        ).getBody()
    );
  }
}
