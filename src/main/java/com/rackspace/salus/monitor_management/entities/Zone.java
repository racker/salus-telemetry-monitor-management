package com.rackspace.salus.monitor_management.entities;

import com.rackspace.salus.monitor_management.web.model.ZoneDTO;
import lombok.Data;
import org.hibernate.annotations.Type;
import org.springframework.boot.convert.DurationUnit;

import javax.persistence.*;
import org.hibernate.validator.constraints.NotBlank;

import java.io.Serializable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "zones", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "name"})})
@Data
public class Zone implements Serializable {
    @Id
    @GeneratedValue
    @Type(type="uuid-char")
    UUID id;

    @NotBlank
    @Column(name="name")
    String name;

    @NotBlank
    @Column(name="tenant_id")
    String tenantId;

    @DurationUnit(ChronoUnit.SECONDS)
    @Column(name="envoy_timeout")
    Duration envoyTimeout = Duration.ofSeconds(120);

    public ZoneDTO toDTO() {
        return new ZoneDTO()
                .setName(name)
                .setTenantId(tenantId)
                .setEnvoyTimeout(envoyTimeout.getSeconds());
    }
}
