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

import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.telemetry.messaging.ReattachedEnvoyResourceEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        ResourceEventListener.class,
        KafkaTopicProperties.class
    },
    properties = {
        "salus.kafka.topics.resources=" + ResourceEventListenerTest.TOPIC,
        // override app default so that we can produce before consumer is ready
        "spring.kafka.consumer.auto-offset-reset=earliest"
    }
)
@ImportAutoConfiguration({
    KafkaAutoConfiguration.class
})
@EmbeddedKafka(topics = ResourceEventListenerTest.TOPIC)
public class ResourceEventListenerTest {

  static final String TOPIC = "resource_events";

  static {
    System.setProperty(
        EmbeddedKafkaBroker.BROKER_LIST_PROPERTY, "spring.kafka.bootstrap-servers");
  }

  @Autowired
  private KafkaTemplate<String, Object> kafkaTemplate;

  @MockBean
  MonitorManagement monitorManagement;

  @Autowired
  ResourceEventListener resourceEventListener;

  @Test
  public void testReattachedEnvoyResourceEvent() throws InterruptedException {
    final ReattachedEnvoyResourceEvent event = new ReattachedEnvoyResourceEvent()
        .setEnvoyId("e-1");
    event
        .setTenantId("t-1")
        .setResourceId("r-1");

    kafkaTemplate.send(TOPIC, "t-1:r-1", event);

    verify(monitorManagement, after(5000))
        .handleReattachedEnvoy(event);
  }
}