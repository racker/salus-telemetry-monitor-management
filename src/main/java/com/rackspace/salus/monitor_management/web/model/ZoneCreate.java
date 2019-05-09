package com.rackspace.salus.monitor_management.web.model;

import lombok.Data;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import org.hibernate.validator.constraints.NotBlank;
import java.io.Serializable;

@Data
public class ZoneCreate implements Serializable {

    @NotBlank
    @Pattern(regexp = "^[\\p{Alnum}]+$", message = "Only alphanumeric characters can be used")
    String name;

    @Min(value = 30, message = "The timeout must not be less than 30s")
    @Max(value = 1800, message = "The timeout must not be more than 1800s (30m)")
    long pollerTimeout = 120;
}
