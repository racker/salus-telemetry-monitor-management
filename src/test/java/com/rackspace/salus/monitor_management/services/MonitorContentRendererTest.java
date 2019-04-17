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

package com.rackspace.salus.monitor_management.services;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.telemetry.model.Resource;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class MonitorContentRendererTest {

  @Test
  public void testRenderTypical() {
    Map<String, String> labels = new HashMap<>();

    final Map<String, String> metadata = new HashMap<>();
    metadata.put("public_ip", "150.1.2.3");

    final Resource resource = new Resource()
        .setLabels(labels)
        .setMetadata(metadata);

    final MonitorContentProperties properties = new MonitorContentProperties();
    final MonitorContentRenderer renderer = new MonitorContentRenderer(properties);

    assertThat(renderer.render(
        "{\"type\": \"ping\", \"urls\": [\"<<resource.metadata.public_ip>>\"]}",
        resource
    ), equalTo("{\"type\": \"ping\", \"urls\": [\"150.1.2.3\"]}"));
  }
}