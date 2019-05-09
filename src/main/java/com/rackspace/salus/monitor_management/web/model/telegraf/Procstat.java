package com.rackspace.salus.monitor_management.web.model.telegraf;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rackspace.salus.monitor_management.web.model.ApplicableAgentType;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.validator.OneOfValidator;
import com.rackspace.salus.telemetry.model.AgentType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotEmpty;

@Data
@EqualsAndHashCode(callSuper = true)
@ApplicableAgentType(AgentType.TELEGRAF)
@JsonInclude(JsonInclude.Include.NON_NULL)
@OneOfValidator.OneOf(groups = OneOfValidator.OneOf.class)
public class Procstat extends LocalPlugin {
    @NotEmpty(groups = OneOfValidator.OneOf.class)
    String pidfile;
    @NotEmpty(groups = OneOfValidator.OneOf.class)
    String user;
    @NotEmpty(groups = OneOfValidator.OneOf.class)
    String exe;
    @NotEmpty(groups = OneOfValidator.OneOf.class)
    String pattern;
    @NotEmpty(groups = OneOfValidator.OneOf.class)
    String systemd_unit;
    @NotEmpty(groups = OneOfValidator.OneOf.class)
    String cgroup;
    @NotEmpty(groups = OneOfValidator.OneOf.class)
    String win_service;

    /*
    Everything gets grabbed through a pgrep call
    Not actually pgrep... Just pulls in the file from the path.
    pidfile;
    pgrep -u
    user;
    pgrep
    exe;
    pgrep -f
    pattern;
    systemd unit name
    systemd_unit;
    cgroup name or path
    cgroup;
    windows service name
    win_service

    process_name
    prefix
    pid_tag
    pid_finder
     */
}
