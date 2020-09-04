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
 * This component is used once per monitor operation to locate the poller-envoy
 * with the least number of bound monitors.
 * This component <b>must not be directly injected</b>, but instead instances should be created by calling
 * {@link ZoneAllocationResolverFactory} prior to iterating over a set of monitor binding changes.
 * <p>
 *   The stateful behavior optimizes for the common scenario where a remote monitor is binding to a
 *   potentially large number of resources.
 *   For example, rather than repeatedly query etcd and SQL for every resource of a monitor
 *   to be bound, a snapshot of each zone is retrieved as needed and locally incremented.
 *   The downside of using a mutable snapshot is that each monitor operation can slightly
 *   diverge in tracking poller-envoy allocations; however, some amount of imbalance is fine
 *   and can be rectified by a rebalance operation.
 * </p>
 */
@Component
@Scope("prototype")
public class ZoneAllocationResolver {

  private final ZoneStorage zoneStorage;
  private final EntityManager entityManager;

  private final Map<ResolvedZone, CompletableFuture<Map<String, String>>> resourceIdToEnvoyIdSnapshot = new HashMap<>();
  private final Map<ResolvedZone, CompletableFuture<Map<String, Integer>>> pollerCountSnapshot = new HashMap<>();

  @Autowired
  public ZoneAllocationResolver(ZoneStorage zoneStorage, EntityManager entityManager) {
    this.zoneStorage = zoneStorage;
    this.entityManager = entityManager;
  }

  /**
   * Identify the poller-envoy that has the least number of bound monitors in the requested zone.
   * It is assumed that the caller will create a {@link com.rackspace.salus.telemetry.entities.BoundMonitor}
   * referencing the returned instance and the internal count will be incremented.
   * As such, repeated calls to this method will retrieve an answer based on the incrementing
   * counts.
   * @param zone the zone to retrieve
   * @return the least loaded envoy-poller or an empty value if none are active in the requested zone
   */
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
                          // In our cache, count a binding to that poller-envoy since it is
                          // assumed the caller will create a BoundMonitor referencing this envoy-poller.
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
    return pollerCountSnapshot.computeIfAbsent(
        zone,
        this::retrievePollerResourceCounts
    );
  }

  /**
   * Returns a map of active poller-envoys to number of monitors bound to each.
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
    return resourceIdToEnvoyIdSnapshot.computeIfAbsent(
        zone,
        zoneStorage::getResourceIdToEnvoyIdMap
    );
  }
}
