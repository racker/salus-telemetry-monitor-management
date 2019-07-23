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

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;
import static org.springframework.kafka.test.utils.KafkaTestUtils.getSingleRecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.config.ServicesProperties;
import com.rackspace.salus.monitor_management.config.TxnConfig;
import com.rackspace.salus.monitor_management.config.ZonesProperties;
import com.rackspace.salus.monitor_management.repositories.BoundMonitorRepository;
import com.rackspace.salus.monitor_management.repositories.MonitorRepository;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.client.ResourceApiClient;
import com.rackspace.salus.telemetry.etcd.services.EnvoyResourceManagement;
import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.ResourceEvent;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import java.util.Collections;
import java.util.Map;
import javax.persistence.EntityManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.Test;
import org.junit.runner.RunWith;
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

//@SpringBootTest(
//    classes = {
//        TxnConfig.class,
////        LocalContainerEntityManagerFactoryBean.class,
//        KafkaTopicProperties.class
//    },
//    properties = {
//        "salus.kafka.topics.resources=" + ResourceEventListenerTest.TOPIC,
//        // override app default so that we can produce before consumer is ready
//        "spring.kafka.consumer.auto-offset-reset=earliest"
//    }
//)

@Import({TxnConfig.class,
    ObjectMapper.class,
MonitorManagement.class,
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

  @MockBean
  BoundMonitorRepository boundMonitorRepository;

  @MockBean
  ResourceApi resourceApi;

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




  @Autowired
  MonitorManagement monitorManagement;

  private PodamFactory podamFactory = new PodamFactoryImpl();

  // IntelliJ gets confused finding this broker bean when @SpringBootTest is activated
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private EmbeddedKafkaBroker embeddedKafka;



  @Test
  //  @Transactional(value="chainedTransactionManager")
  public void testMonitorTransaction() {
    String tenantId = RandomStringUtils.randomAlphanumeric(10);
    MonitorCU create = podamFactory.manufacturePojo(MonitorCU.class);
    // limit to local/agent monitors only
    create.setSelectorScope(ConfigSelectorScope.LOCAL);
    create.setZones(Collections.emptyList());
    monitorManagement.createMonitor(tenantId, create);

    final Map<String, Object> consumerProps = KafkaTestUtils
        .consumerProps("testMonitorTransaction", "true", embeddedKafka);
    // Since we're pre-sending the messages to test for, we need to read from start of topic
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    // We need to match the ser/deser used in expected application config
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
    System.out.println(record);
  }


//
//  private <K,V> Consumer<K, V> buildConsumer(Class<? extends Deserializer> keyDeserializer,
//      Class<? extends Deserializer> valueDeserializer) {
//    // Use the procedure documented at https://docs.spring.io/spring-kafka/docs/2.2.4.RELEASE/reference/#embedded-kafka-annotation
//
//    final Map<String, Object> consumerProps = KafkaTestUtils
//        .consumerProps("testMonitorTransaction", "true", embeddedKafka);
//    // Since we're pre-sending the messages to test for, we need to read from start of topic
//    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
//    // We need to match the ser/deser used in expected application config
//    consumerProps
//        .put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer.getName());
//    consumerProps
//        .put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer.getName());
//
//    consumerProps
//        .put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
//
//    final DefaultKafkaConsumerFactory<K, V> consumerFactory =
//        new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(),
//            new JsonDeserializer<>(MonitorBoundEvent.class));
//    return consumerFactory.createConsumer();
//  }
}