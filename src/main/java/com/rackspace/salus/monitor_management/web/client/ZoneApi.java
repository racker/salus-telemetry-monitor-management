package com.rackspace.salus.monitor_management.web.client;

import com.rackspace.salus.monitor_management.web.model.ZoneDTO;
import java.util.List;

/**
 * This interface declares a subset of internal REST API calls exposed by the Zone Management
 * service.
 *
 * @see ZoneApiClient
 */
public interface ZoneApi {

    ZoneDTO getByZoneName(String tenantId, String name);

    List<ZoneDTO> getAvailableZones(String tenantId);
}