package com.rackspace.salus.monitor_management.web.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.web.client.ZoneApiClient;
import com.rackspace.salus.monitor_management.web.model.ZoneDTO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RunWith(SpringRunner.class)
@RestClientTest
public class ZoneApiClientTest {
    @TestConfiguration
    public static class ExtraTestConfig {
        @Bean
        public ZoneApiClient zoneApiClient(RestTemplateBuilder restTemplateBuilder) {
            return new ZoneApiClient(restTemplateBuilder.build());
        }
    }
    @Autowired
    MockRestServiceServer mockServer;

    @Autowired
    ZoneApiClient zoneApiClient;

    private PodamFactory podamFactory = new PodamFactoryImpl();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testGetByZoneName() throws JsonProcessingException {
        ZoneDTO expectedZone = podamFactory.manufacturePojo(ZoneDTO.class);
        mockServer.expect(requestTo("/api/tenant/t-1/zones/z-1"))
                .andRespond(withSuccess(
                        objectMapper.writeValueAsString(expectedZone), MediaType.APPLICATION_JSON
                ));

        final ZoneDTO zone = zoneApiClient.getByZoneName("t-1", "z-1");

        assertThat(zone, equalTo(expectedZone));
    }
}