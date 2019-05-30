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

import com.rackspace.salus.monitor_management.web.model.MonitorDTO;
import com.rackspace.salus.monitor_management.web.model.ZoneDTO;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * This client component provides a small subset of Zone Management REST operations that
 * can be called internally by other microservices in Salus.
 *
 * <p>
 *   It is required that the {@link RestTemplate} provided to this instance has been
 *   configured with the appropriate root URI for locating the zone management service.
 *   The following is an example of a configuration bean that does that:
 * </p>
 *
 * <pre>
 {@literal @}Configuration
 public class RestClientsConfig {

   {@literal @}Bean
   public ZoneApi zoneApi(RestTemplateBuilder restTemplateBuilder) {
     return new ZoneApiClient(
       restTemplateBuilder
       .rootUri("http://localhost:8089")
       .build()
     );
   }
 }
 * </pre>
 *
 */
@Slf4j
public class ZoneApiClient implements ZoneApi {

    private static final ParameterizedTypeReference<List<ZoneDTO>> LIST_OF_ZONES = new ParameterizedTypeReference<List<ZoneDTO>>() {
    };
    private static final ParameterizedTypeReference<List<MonitorDTO>> LIST_OF_MONITOR = new ParameterizedTypeReference<List<MonitorDTO>>() {
    };

    private final RestTemplate restTemplate;


    public ZoneApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    @Override
    public ZoneDTO getByZoneName(String tenantId, String name) {
        try {
            return restTemplate.getForObject(
                    "/api/tenant/{tenantId}/zones/{name}",
                    ZoneDTO.class,
                    tenantId, name
            );
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
                // what happens if this isn't here?
            }
            else {
                throw new IllegalArgumentException(e);
            }
        }
    }

    @Override
    public List<ZoneDTO> getAvailableZones(String tenantId) {

        return restTemplate.exchange(
                "/api/tenant/{tenantId}/zones",
                HttpMethod.GET,
                null,
                LIST_OF_ZONES,
                tenantId
        ).getBody();
    }

    @Override
    public List<MonitorDTO> getMonitorsForZone(String tenantId, String zone) {
        return restTemplate.exchange(
                "/api/tenant/{tenantId}/monitorsByZone/{zone}",
                HttpMethod.GET,
                null,
                LIST_OF_MONITOR,
                tenantId,
                zone
        ).getBody();
    }
}
