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
import com.rackspace.salus.telemetry.messaging.TestMonitorResultsEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class TestMonitorListener {

  private final TestMonitorService testMonitorService;
  private final KafkaTopicProperties kafkaTopicProperties;
  private final String appName;
  private final String ourHostName;

  @Autowired
  public TestMonitorListener(TestMonitorService testMonitorService,
                             KafkaTopicProperties kafkaTopicProperties,
                             @Value("${spring.application.name") String appName,
                             @Value("${localhost.name}") String ourHostName) {
    this.testMonitorService = testMonitorService;
    this.kafkaTopicProperties = kafkaTopicProperties;
    this.appName = appName;
    this.ourHostName = ourHostName;
  }

  @SuppressWarnings("unused") // used in @KafkaListener
  public String getResultsTopic() {
    return kafkaTopicProperties.getTestMonitorResults();
  }

  @SuppressWarnings("unused") // used in @KafkaListener
  public String getResultsGroupdId() {
    return String.join("-", appName, "testMonitorResults", ourHostName);
  }

  @KafkaListener(topics = "#{__listener.resultsTopic", groupId = "#{__listener.resultsGroupId}")
  public void consumeTestMonitorResultsEvent(TestMonitorResultsEvent event) {
    // let the service determine if the event is relevant or not by correlation ID
    testMonitorService.handleTestMonitorResultsEvent(event);
  }

}
