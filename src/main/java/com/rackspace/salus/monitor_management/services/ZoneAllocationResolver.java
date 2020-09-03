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

package com.rackspace.salus.monitor_management.services;

import com.rackspace.salus.telemetry.etcd.services.ZoneStorage;
import com.rackspace.salus.telemetry.etcd.types.EnvoyResourcePair;
import com.rackspace.salus.telemetry.etcd.types.ResolvedZone;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * This stateful component encapsulates and caches the resolving of poller-envoys to allocate for
 * monitor binding. It is recommended to use {@link ZoneAllocationResolverFactory} to create
 * instances of this component prior to iterating over monitor binding changes.
 * <p>
 *   The caching optimizes for the common scenario where a remote monitor is binding to a potentially
 *   large number of resources. Rather than query etcd and SQL for the poller-envoy loads repeatedly,
 *   a snapshot of each zone is retrieved as needed and tracks across repeated use.
 *   The downside of using mutable snapshots is that concurrent use of these resolvers across the
 *   system can result in potential imbalance in poller-envoy allocations; however, some amount of
 *   imbalance is fine and can be rectified by a rebalance operation.
 * </p>
 */
@Component
@Scope("prototype")
public class ZoneAllocationResolver {

  private final ZoneStorage zoneStorage;
  private final EntityManager entityManager;

  private final Map<ResolvedZone, CompletableFuture<Map<String, String>>> resourceIdToEnvoyIdCache = new HashMap<>();
  private final Map<ResolvedZone, CompletableFuture<Map<String, Integer>>> pollerCountCache = new HashMap<>();

  @Autowired
  public ZoneAllocationResolver(ZoneStorage zoneStorage, EntityManager entityManager) {
    this.zoneStorage = zoneStorage;
    this.entityManager = entityManager;
  }

  public Optional<EnvoyResourcePair> findLeastLoadedEnvoy(ResolvedZone zone) {

    // Get the (cached) poller-envoy binding counts for the zone
    return getPollerResourceCounts(zone)
        .thenCompose(pollerResourceCounts ->
            // ...and get a mapping of poller resourceIds to current envoyIds
            getResourceIdToEnvoyIdMap(zone)
                .thenApply(resourceIdToEnvoyId ->
                    pollerResourceCounts.entrySet().stream()
                        // Pick the smallest, least-assigned entry
                        .min(Comparator.comparingLong(Entry::getValue))
                        .map(entry -> {
                          // In our cache, count a binding to that poller-envoy
                          // setValue works here since getPollerResourceCounts provides
                          // a map for us to mutate
                          entry.setValue(entry.getValue() + 1);

                          final String resourceId = entry.getKey();
                          return new EnvoyResourcePair()
                              .setResourceId(resourceId)
                              .setEnvoyId(resourceIdToEnvoyId.get(resourceId));
                        }))
        )
        .join();
  }

  private CompletableFuture<Map<String, Integer>> getPollerResourceCounts(ResolvedZone zone) {
    return pollerCountCache.computeIfAbsent(
        zone,
        this::retrievePollerResourceCounts
    );
  }

  /**
   * Combines tracking of active poller-envoys with the currently bound monitors into a count of
   * of bound monitors per poller-envoy
   *
   * @param zone the zone to retrieve
   * @return mapping of envoy-pollers to bound monitor counts
   */
  public CompletableFuture<Map<EnvoyResourcePair, Integer>> getZoneBindingCounts(ResolvedZone zone) {
    // Combine the poller counts by resourceId
    return getPollerResourceCounts(zone)
        .thenCompose(pollerResourceCounts ->
            // ...with the resourceId to envoyId mappings
            getResourceIdToEnvoyIdMap(zone)
                .thenApply(resourceIdToEnvoyIdMap ->
                    // ...and re-map each key into an EnvoyResourcePair
                    pollerResourceCounts.entrySet().stream()
                        .collect(Collectors.toMap(
                            entry ->
                                new EnvoyResourcePair()
                                    .setResourceId(entry.getKey())
                                    .setEnvoyId(resourceIdToEnvoyIdMap.get(entry.getKey())),
                            Entry::getValue
                        ))
                ));
  }

  /**
   * Grabs a snapshot of active envoy-pollers from the etcd zone tracking and merges in the
   * counts of bound monitors per envoy-poller
   * @param zone the zone to retrieve
   * @return a mutable map of envoy-poller resourceId to binding counts
   */
  private CompletableFuture<Map<String, Integer>> retrievePollerResourceCounts(ResolvedZone zone) {
    return zoneStorage.getActivePollerResourceIdsInZone(zone)
        .thenApply(activePollerResources -> {
          // Start with a count of 0 for all active poller resource IDs
          // ...that ensures that pollers with no bindings are considered
          final Map<String, Integer> pollerResourceLoading = new HashMap<>(
              activePollerResources.size());
          for (String resourceId : activePollerResources) {
            pollerResourceLoading.put(resourceId, 0);
          }

          // Count bindings per poller resource ID
          final TypedQuery<Tuple> query;
          if (zone.isPublicZone()) {
            query = entityManager
                .createNamedQuery("BoundMonitor.publicPollerLoading", Tuple.class)
                .setParameter("zoneName", zone.getName());
          } else {
            query = entityManager
                .createNamedQuery("BoundMonitor.privatePollerLoading", Tuple.class)
                .setParameter("tenantId", zone.getTenantId())
                .setParameter("zoneName", zone.getName());
          }

          // Merge bindings counts into the active poller mappings
          query
              .getResultStream()
              .forEach(tuple -> {
                pollerResourceLoading.put(
                    // resourceId
                    (String) tuple.get(0),
                    // count
                    ((Number) tuple.get(1)).intValue()
                );
              });

          return pollerResourceLoading;
        });
  }

  private CompletableFuture<Map<String, String>> getResourceIdToEnvoyIdMap(ResolvedZone zone) {
    return resourceIdToEnvoyIdCache.computeIfAbsent(
        zone,
        zoneStorage::getResourceIdToEnvoyIdMap
    );
  }
}
