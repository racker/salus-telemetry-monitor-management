/*
 * Copyright 2020 Rackspace US, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rackspace.salus.monitor_management.web.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.rackspace.salus.monitor_management.web.model.oracle.Dataguard;
import com.rackspace.salus.monitor_management.web.model.oracle.Rman;
import com.rackspace.salus.monitor_management.web.model.oracle.Tablespace;
import com.rackspace.salus.monitor_management.web.model.packages.Packages;
import com.rackspace.salus.monitor_management.web.model.telegraf.Apache;
import com.rackspace.salus.monitor_management.web.model.telegraf.Cpu;
import com.rackspace.salus.monitor_management.web.model.telegraf.Disk;
import com.rackspace.salus.monitor_management.web.model.telegraf.DiskIo;
import com.rackspace.salus.monitor_management.web.model.telegraf.Mem;
import com.rackspace.salus.monitor_management.web.model.telegraf.Mysql;
import com.rackspace.salus.monitor_management.web.model.telegraf.Net;
import com.rackspace.salus.monitor_management.web.model.telegraf.Postgresql;
import com.rackspace.salus.monitor_management.web.model.telegraf.Procstat;
import com.rackspace.salus.monitor_management.web.model.telegraf.Redis;
import com.rackspace.salus.monitor_management.web.model.telegraf.SqlServer;
import com.rackspace.salus.monitor_management.web.model.telegraf.System;

@JsonTypeInfo(use = Id.NAME, property = "type")
// NOTE: when adding a new sub-type, place the new entry in alphabetical order by 'name'
@JsonSubTypes({
    @Type(name = "apache", value = Apache.class),
    @Type(name = "cpu", value = Cpu.class),
    @Type(name = "disk", value = Disk.class),
    @Type(name = "diskio", value = DiskIo.class),
    @Type(name = "mem", value = Mem.class),
    @Type(name = "mysql", value = Mysql.class),
    @Type(name = "net", value = Net.class),
    @Type(name = "oracle_dataguard", value = Dataguard.class),
    @Type(name = "oracle_rman", value = Rman.class),
    @Type(name = "oracle_tablespace", value = Tablespace.class),
    @Type(name = "packages", value = Packages.class),
    @Type(name = "postgresql", value = Postgresql.class),
    @Type(name = "procstat", value = Procstat.class),
    @Type(name = "redis", value = Redis.class),
    @Type(name = "sqlserver", value = SqlServer.class),
    @Type(name = "system", value = System.class)
})
public abstract class LocalPlugin {

}
