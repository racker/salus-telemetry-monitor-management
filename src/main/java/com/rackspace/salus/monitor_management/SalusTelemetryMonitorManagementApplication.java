package com.rackspace.salus.monitor_management;

import com.rackspace.salus.common.messaging.EnableSalusKafkaMessaging;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableSalusKafkaMessaging
public class SalusTelemetryMonitorManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalusTelemetryMonitorManagementApplication.class, args);
    }

}

