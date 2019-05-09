package com.rackspace.salus.monitor_management.web.model.telegraf;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rackspace.salus.monitor_management.web.model.ApplicableAgentType;
import com.rackspace.salus.monitor_management.web.model.LocalPlugin;
import com.rackspace.salus.monitor_management.web.validator.ProcstatValidator;
import com.rackspace.salus.telemetry.model.AgentType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApplicableAgentType(AgentType.TELEGRAF)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ProcstatValidator.OneOf(groups = ProcstatValidator.OneOf.class)
public class Procstat extends LocalPlugin {
    String pidFile;
    String user;
    String exe;
    String pattern;
    String systemd_unit;
    String cgroup;
    String win_service;

    String process_name;

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
