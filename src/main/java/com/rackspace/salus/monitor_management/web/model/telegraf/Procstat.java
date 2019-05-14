package com.rackspace.salus.monitor_management.web.model.telegraf;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rackspace.salus.monitor_management.web.model.ApplicableAgentType;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.validator.ProcstatValidator;
import com.rackspace.salus.telemetry.model.AgentType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.Constraint;

@Data
@EqualsAndHashCode(callSuper = true)
@ApplicableAgentType(AgentType.TELEGRAF)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ProcstatValidator.OneOf()
public class Procstat extends LocalPlugin {
    String pidFile;
    String user;
    String exe;
    String pattern;
    String systemdUnit;
    String cgroup;
    String winService;

    // This is optional; default is sourced from /proc/<pid>/status
    String processName;
}
