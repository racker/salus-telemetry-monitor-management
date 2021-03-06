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
import com.rackspace.salus.monitor_management.web.model.telegraf.Dns;
import com.rackspace.salus.monitor_management.web.model.telegraf.HttpResponse;
import com.rackspace.salus.monitor_management.web.model.telegraf.MysqlRemote;
import com.rackspace.salus.monitor_management.web.model.telegraf.NetResponse;
import com.rackspace.salus.monitor_management.web.model.telegraf.Ping;
import com.rackspace.salus.monitor_management.web.model.telegraf.PostgresqlRemote;
import com.rackspace.salus.monitor_management.web.model.telegraf.Smtp;
import com.rackspace.salus.monitor_management.web.model.telegraf.SqlServerRemote;
import com.rackspace.salus.monitor_management.web.model.telegraf.X509Cert;

@JsonTypeInfo(use = Id.NAME, property = "type")
// NOTE: when adding a new sub-type, place the new entry in alphabetical order by 'name'
@JsonSubTypes({
    @Type(name = "dns", value = Dns.class),
    @Type(name = "http", value = HttpResponse.class),
    @Type(name = "mysql", value = MysqlRemote.class),
    @Type(name = "net_response", value = NetResponse.class),
    @Type(name = "ping", value = Ping.class),
    @Type(name = "postgresql", value = PostgresqlRemote.class),
    @Type(name = "smtp", value = Smtp.class),
    @Type(name = "sqlserver", value = SqlServerRemote.class),
    @Type(name = "ssl", value = X509Cert.class)
})
public abstract class RemotePlugin {

}
