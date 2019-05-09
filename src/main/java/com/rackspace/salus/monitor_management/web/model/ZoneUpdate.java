package com.rackspace.salus.monitor_management.web.model;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.io.Serializable;

@Data
public class ZoneUpdate implements Serializable {

    @Min(value = 30, message = "The timeout must not be less than 30s")
    @Max(value = 1800, message = "The timeout must not be more than 1800s (30m)")
    long pollerTimeout;
}
