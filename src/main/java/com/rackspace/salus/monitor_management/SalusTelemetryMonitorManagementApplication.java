package com.rackspace.salus.monitor_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories("com.rackspace.salus.telemetry.repositories")
@EntityScan("com.rackspace.salus.telemetry.model")
public class SalusTelemetryMonitorManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalusTelemetryMonitorManagementApplication.class, args);
    }

}

