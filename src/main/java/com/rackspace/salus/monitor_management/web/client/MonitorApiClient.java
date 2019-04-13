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

import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

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

  private static final ParameterizedTypeReference<List<BoundMonitor>> LIST_OF_BOUND_MONITOR = new ParameterizedTypeReference<List<BoundMonitor>>() {
  };
  private final RestTemplate restTemplate;

  public MonitorApiClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Override
  public List<BoundMonitor> getBoundMonitors(String envoyId) {
    return restTemplate.exchange(
        "/api/boundMonitors/{envoyId}",
        HttpMethod.GET,
        null,
        LIST_OF_BOUND_MONITOR,
        envoyId
    )
        .getBody();
  }
}
