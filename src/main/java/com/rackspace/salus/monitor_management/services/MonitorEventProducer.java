package com.rackspace.salus.monitor_management.services;

import static com.rackspace.salus.common.messaging.KafkaMessageKeyBuilder.buildMessageKey;

import com.rackspace.salus.common.errors.RuntimeKafkaException;
import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.telemetry.messaging.MonitorBoundEvent;
import com.rackspace.salus.telemetry.messaging.PolicyMonitorUpdateEvent;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class MonitorEventProducer {

    private final KafkaTemplate<String,Object> kafkaTemplate;
    private final KafkaTopicProperties properties;

    @Autowired
    public MonitorEventProducer(KafkaTemplate<String,Object> kafkaTemplate, KafkaTopicProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties= properties;
    }

    public void sendMonitorEvent(MonitorBoundEvent event) {
        final String topic = properties.getMonitors();

        log.debug("Sending monitorBoundEvent={} on topic={}", event, topic);
        try {
            kafkaTemplate.send(topic, event.getEnvoyId(), event).get();
        } catch (InterruptedException e) {
            throw new RuntimeKafkaException(e);
        } catch (ExecutionException e) {
            throw new RuntimeKafkaException(e);
        }
    }

    public void sendPolicyMonitorUpdateEvent(PolicyMonitorUpdateEvent event) {
        final String topic = properties.getPolicies();

        log.debug("Sending policyMonitorUpdateEvent={} on topic={}", event, topic);
        try {
            kafkaTemplate.send(topic, buildMessageKey(event), event).get();
        } catch (InterruptedException e) {
            throw new RuntimeKafkaException(e);
        } catch (ExecutionException e) {
            throw new RuntimeKafkaException(e);
        }
    }
}