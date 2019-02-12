package com.rackspace.salus.monitor_management.config;

import com.rackspace.salus.telemetry.messaging.KafkaMessageType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@ConfigurationProperties("monitor-management")
@Component
@Data
public class MonitorManagementProperties {
    Map<KafkaMessageType, String> kafkaTopics;
}