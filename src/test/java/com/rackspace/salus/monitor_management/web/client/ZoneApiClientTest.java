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

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.web.model.ZoneDTO;
import com.rackspace.salus.telemetry.repositories.TenantMetadataRepository;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheType;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@RestClientTest
@AutoConfigureCache(cacheProvider = CacheType.JCACHE)
public class ZoneApiClientTest {
    @TestConfiguration
    @Import(ZoneApiCacheConfig.class)
    public static class ExtraTestConfig {
        @Bean
        public ZoneApiClient zoneApiClient(RestTemplateBuilder restTemplateBuilder) {
            return new ZoneApiClient(restTemplateBuilder.build());
        }
    }
    @Autowired
    MockRestServiceServer mockServer;

    @Autowired
    ZoneApi zoneApiClient;

    @MockBean
    TenantMetadataRepository tenantMetadataRepository;

    private PodamFactory podamFactory = new PodamFactoryImpl();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testGetByZoneName_private() throws JsonProcessingException {
        ZoneDTO expectedZone = podamFactory.manufacturePojo(ZoneDTO.class);
        mockServer.expect(requestTo("/api/tenant/t-1/zones/z-1"))
                .andRespond(withSuccess(
                        objectMapper.writeValueAsString(expectedZone), MediaType.APPLICATION_JSON
                ));

        final ZoneDTO zone = zoneApiClient.getByZoneName("t-1", "z-1");

        assertThat(zone, equalTo(expectedZone));
    }

    @Test
    public void testGetByZoneName_public() throws JsonProcessingException {
        ZoneDTO expectedZone = podamFactory.manufacturePojo(ZoneDTO.class);
        mockServer.expect(requestTo("/api/admin/zones/public/west"))
                .andRespond(withSuccess(
                        objectMapper.writeValueAsString(expectedZone), MediaType.APPLICATION_JSON
                ));

        final ZoneDTO zone = zoneApiClient.getByZoneName(null, "public/west");

        assertThat(zone, equalTo(expectedZone));
    }

    @Test
    public void testGetAvailableZones() throws JsonProcessingException {
        final List<ZoneDTO> expectedZones = List.of(
            podamFactory.manufacturePojo(ZoneDTO.class),
            podamFactory.manufacturePojo(ZoneDTO.class)
        );

        mockServer.expect(
            // assert/expect only one non-cached call to this
            ExpectedCount.once(),
            requestTo("/api/tenant/t-1/zones?size=2147483647")
        )
            .andRespond(withSuccess(
                objectMapper.writeValueAsString(
                    new PageImpl<>(expectedZones)
                ),
                MediaType.APPLICATION_JSON
            ));

        List<ZoneDTO> zones = zoneApiClient.getAvailableZones("t-1");
        assertThat(zones, equalTo(expectedZones));

        // and call again, but should be cached
        zones = zoneApiClient.getAvailableZones("t-1");
        assertThat(zones, equalTo(expectedZones));
    }
}