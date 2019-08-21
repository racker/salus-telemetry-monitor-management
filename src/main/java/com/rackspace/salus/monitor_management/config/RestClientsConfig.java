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

package com.rackspace.salus.monitor_management.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.policy.manage.web.client.PolicyApiClient;
import com.rackspace.salus.resource_management.web.client.ResourceApi;
import com.rackspace.salus.resource_management.web.client.ResourceApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestClientsConfig {

  private final ServicesProperties servicesProperties;

  @Autowired
  public RestClientsConfig(ServicesProperties servicesProperties) {
    this.servicesProperties = servicesProperties;
  }

  @Bean
  public ResourceApi resourceApi(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
    return new ResourceApiClient(objectMapper,
        restTemplateBuilder.rootUri(servicesProperties.getResourceManagementUrl())
        .build()
    );
  }

  @Bean
  public PolicyApi policyAPi(RestTemplateBuilder restTemplateBuilder) {
    return new PolicyApiClient(
        restTemplateBuilder.rootUri(servicesProperties.getPolicyManagementUrl())
            .build()
    );
  }
}
