/*
 * Copyright 2019 Rackspace US, Inc.
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
import com.rackspace.salus.monitor_management.web.model.telegraf.Cpu;
import com.rackspace.salus.monitor_management.web.model.telegraf.Disk;
import com.rackspace.salus.monitor_management.web.model.telegraf.DiskIo;
import com.rackspace.salus.monitor_management.web.model.telegraf.HttpResponse;
import com.rackspace.salus.monitor_management.web.model.telegraf.Mem;
import com.rackspace.salus.monitor_management.web.model.telegraf.Net;
import com.rackspace.salus.monitor_management.web.model.telegraf.NetResponse;
import com.rackspace.salus.monitor_management.web.model.telegraf.Ping;
import com.rackspace.salus.monitor_management.web.model.telegraf.Procstat;
import com.rackspace.salus.monitor_management.web.model.telegraf.X509Cert;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(name = "cpu", value = Cpu.class),
    @Type(name = "disk", value = Disk.class),
    @Type(name = "diskio", value = DiskIo.class),
    @Type(name = "mem", value = Mem.class),
    @Type(name = "net", value = Net.class),
    @Type(name = "procstat", value = Procstat.class),
    @Type(name = "ping", value = Ping.class),
    @Type(name = "x509_cert", value = X509Cert.class),
    @Type(name = "http_response", value = HttpResponse.class),
    @Type(name = "net_response", value = NetResponse.class)
})
public abstract class Plugin {

}
