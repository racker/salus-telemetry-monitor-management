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

package com.rackspace.salus.monitor_management;

import com.rackspace.salus.common.messaging.EnableSalusKafkaMessaging;
import com.rackspace.salus.common.util.DumpConfigProperties;
import com.rackspace.salus.common.web.ExtendedErrorAttributesConfig;
import com.rackspace.salus.telemetry.etcd.EnableEtcd;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableSalusKafkaMessaging
@EntityScan({
    "com.rackspace.salus.telemetry.model",
    "com.rackspace.salus.monitor_management.entities"
})
@EnableEtcd
@Import(ExtendedErrorAttributesConfig.class)
public class TelemetryMonitorManagementApplication {

  public static void main(String[] args) {
    DumpConfigProperties.process(args);

    SpringApplication.run(TelemetryMonitorManagementApplication.class, args);
  }

}

