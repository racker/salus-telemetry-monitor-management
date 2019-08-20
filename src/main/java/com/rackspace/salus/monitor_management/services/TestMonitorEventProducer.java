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

import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.telemetry.messaging.TestMonitorRequestEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TestMonitorEventProducer {

  private final KafkaTemplate kafkaTemplate;
  private final KafkaTopicProperties kafkaTopicProperties;

  @Autowired
  public TestMonitorEventProducer(KafkaTemplate kafkaTemplate,
                                  KafkaTopicProperties kafkaTopicProperties) {
    this.kafkaTemplate = kafkaTemplate;
    this.kafkaTopicProperties = kafkaTopicProperties;
  }

  public void send(TestMonitorRequestEvent event) {
    final String topic = kafkaTopicProperties.getTestMonitorRequests();

    log.debug("Sending test-monitor request event={} on topic={}", event, topic);
    //noinspection unchecked
    kafkaTemplate.send(topic, event);
  }

}
