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

package com.rackspace.salus.monitor_management.web.client;

import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.jsr107.Eh107Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ZoneApiCacheProperties.class)
@EnableCaching
public class ZoneApiCacheConfig {

  public static final String PER_TENANT = "monmgmt_zoneapi_pertenant";

  private final ZoneApiCacheProperties properties;

  @Autowired
  public ZoneApiCacheConfig(ZoneApiCacheProperties properties) {
    this.properties = properties;
  }

  @Bean
  public JCacheManagerCustomizer zoneApiCacheCustomizer() {
    return cacheManager -> {
      cacheManager.createCache(PER_TENANT, perTenantCacheConfig());
    };
  }

  private javax.cache.configuration.Configuration<Object, Object> perTenantCacheConfig() {
    return Eh107Configuration.fromEhcacheCacheConfiguration(
        CacheConfigurationBuilder.newCacheConfigurationBuilder(Object.class, Object.class,
            ResourcePoolsBuilder.heap(properties.getPerTenantMaxSize())
        )
            .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(properties.getPerTenantTtl()))
    );
  }
}
