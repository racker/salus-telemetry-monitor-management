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

package com.rackspace.salus.monitor_management.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.TxnConfig;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.entities.BoundMonitor;
import com.rackspace.salus.monitor_management.entities.Monitor;
import com.rackspace.salus.monitor_management.repositories.BoundMonitorRepository;
import com.rackspace.salus.monitor_management.repositories.MonitorRepository;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.client.ResourceApiClient;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.ResourceInfo;
import edu.emory.mathcs.backport.java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.persistence.EntityManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@DataJpaTest(showSql = false)

@Import({TxnConfig.class,
    ObjectMapper.class,
//MonitorManagement.class,
    MonitorContentRenderer.class,
    MonitorContentProperties.class,
//    MonitorRepository.class,
//    EnvoyResourceManagement.class,
//    MonitorContentProperties.class,
//   BoundMonitorRepository.class,
//    ZoneStorage.class,
    MonitorEventProducer.class,
//    MonitorContentRenderer.class,
//    ResourceApiClient.class,
//    RestTemplate.class,
//    ZoneManagement.class, ZonesProperties.class
})
@ImportAutoConfiguration({
    KafkaAutoConfiguration.class
})
@EmbeddedKafka(topics = "telemetry.monitors.json",
    brokerProperties = {"transaction.state.log.replication.factor=1", "transaction.state.log.min.isr=1"})
public class MonitorTransactionTest {

  static {
    System.setProperty(
        EmbeddedKafkaBroker.BROKER_LIST_PROPERTY, "spring.kafka.bootstrap-servers");
   }
  @TestConfiguration
  public static class Config {

    @Bean
    public ZonesProperties zonesProperties() {
      return new ZonesProperties();
    }

    @Bean
    public ServicesProperties servicesProperties() {
      return new ServicesProperties()
          .setResourceManagementUrl("");
    }
  }
  @MockBean
  EnvoyResourceManagement envoyResourceManagement;

  @MockBean
  ZoneStorage zoneStorage;

  @Autowired
  BoundMonitorRepository boundMonitorRepository;

  @MockBean
  ZoneManagement zoneManagement;

  @Autowired
  ObjectMapper objectMapper;
  @Autowired
  MonitorRepository monitorRepository;
  @Autowired
  EntityManager entityManager;
  @Autowired
  JdbcTemplate jdbcTemplate;

  @Mock
  ResourceApi resourceApi;

  MonitorManagement monitorManagement;

  @Autowired
  MonitorContentRenderer monitorContentRenderer;


  @Autowired
  MonitorEventProducer monitorEventProducer;

  private PodamFactory podamFactory = new PodamFactoryImpl();

  // IntelliJ gets confused finding this broker bean when @SpringBootTest is activated
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private EmbeddedKafkaBroker embeddedKafka;



  @Test
  public void testMonitorTransaction() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());

    ResourceDTO dummyResource = podamFactory.manufacturePojo(ResourceDTO.class);
    dummyResource.setAssociatedWithEnvoy(true);
    List<ResourceDTO> resources = new ArrayList<>();
    resources.add(dummyResource);
    when(resourceApi.getResourcesWithLabels(anyString(),any())).thenReturn(resources);

    ResourceInfo resourceInfo = podamFactory.manufacturePojo(ResourceInfo.class);
    resourceInfo.setEnvoyId("dummyEnvoy");
    CompletableFuture<ResourceInfo> dummy = CompletableFuture.completedFuture(resourceInfo);
    when(envoyResourceManagement.getOne(anyString(), anyString())).thenReturn(dummy);

    monitorManagement = new MonitorManagement(monitorRepository, entityManager, envoyResourceManagement,
        boundMonitorRepository, zoneStorage, monitorEventProducer, monitorContentRenderer, resourceApi,
        zoneManagement, new ZonesProperties(), jdbcTemplate );

    monitorManagement.createMonitor(tenantId, create);


    Iterator<Monitor> monitorIterator = monitorRepository.findAll().iterator();
    Monitor monitor = monitorIterator.next();
    Assert.assertEquals(monitor.getMonitorName(), create.getMonitorName());
    Assert.assertEquals(monitor.getTenantId(), tenantId);
    Assert.assertEquals(monitorIterator.hasNext(), false);

    Iterator<BoundMonitor> boundMonitorIterator = boundMonitorRepository.findAll().iterator();
    BoundMonitor b = boundMonitorIterator.next();
    Assert.assertEquals(b.getMonitor().getMonitorName(), create.getMonitorName());
    Assert.assertEquals(b.getMonitor().getTenantId(), tenantId);
    Assert.assertEquals(boundMonitorIterator.hasNext(), false);

    final Map<String, Object> consumerProps = KafkaTestUtils
        .consumerProps("testMonitorTransaction", "true", embeddedKafka);
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    consumerProps
        .put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    consumerProps
        .put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getName());
    consumerProps
        .put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

    final DefaultKafkaConsumerFactory<String, MonitorBoundEvent> consumerFactory =
        new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(),
            new JsonDeserializer<>(MonitorBoundEvent.class));

    final Consumer<String, MonitorBoundEvent> consumer = consumerFactory.createConsumer();
    embeddedKafka.consumeFromEmbeddedTopics(consumer, "telemetry.monitors.json");
    final ConsumerRecord<String, MonitorBoundEvent> record = getSingleRecord(consumer, "telemetry.monitors.json", 500);

  }
}