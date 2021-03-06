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
import com.rackspace.salus.monitor_management.errors.InvalidTemplateException;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class MonitorContentRendererTest {

  @Test
  public void testRenderTypical() throws InvalidTemplateException {
    Map<String, String> labels = new HashMap<>();

    final Map<String, String> metadata = new HashMap<>();
    metadata.put("public_ip", "150.1.2.3");

    final ResourceDTO resource = new ResourceDTO()
        .setLabels(labels)
        .setMetadata(metadata);

    final MonitorContentProperties properties = new MonitorContentProperties();
    final MonitorContentRenderer renderer = new MonitorContentRenderer(properties);

    final String rendered = renderer.render(
        "{\"type\": \"ping\", \"urls\": [\"${resource.metadata.public_ip}\"]}",
        resource
    );
    assertThat(rendered, equalTo("{\"type\": \"ping\", \"urls\": [\"150.1.2.3\"]}"));
  }

  @Test(expected = InvalidTemplateException.class)
  public void testMetadataFieldNotPresent() throws InvalidTemplateException {
    final ResourceDTO resource = new ResourceDTO()
        .setLabels(Collections.emptyMap())
        .setMetadata(Collections.emptyMap());

    final MonitorContentProperties properties = new MonitorContentProperties();
    final MonitorContentRenderer renderer = new MonitorContentRenderer(properties);

    final String rendered = renderer.render(
        "address=${resource.metadata.address}",
        resource
    );
  }

  @Test(expected = InvalidTemplateException.class)
  public void testMetadataFieldIsNull() throws InvalidTemplateException {
    final ResourceDTO resource = new ResourceDTO()
        .setLabels(Collections.emptyMap())
        .setMetadata(Collections.singletonMap("nullness", null));

    final MonitorContentProperties properties = new MonitorContentProperties();
    final MonitorContentRenderer renderer = new MonitorContentRenderer(properties);

    final String rendered = renderer.render(
        "value=${resource.metadata.nullness}",
        resource
    );
  }

  @Test(expected = InvalidTemplateException.class)
  public void testTopLevelBadReference() throws InvalidTemplateException {
    final ResourceDTO resource = new ResourceDTO()
        .setLabels(Collections.emptyMap())
        .setMetadata(Collections.emptyMap());

    final MonitorContentProperties properties = new MonitorContentProperties();
    final MonitorContentRenderer renderer = new MonitorContentRenderer(properties);

    final String rendered = renderer.render(
        "value=${nothere.novalue}",
        resource
    );
  }

  @Test(expected = InvalidTemplateException.class)
  public void testResourceLevelBadReference() throws InvalidTemplateException {
    final ResourceDTO resource = new ResourceDTO()
        .setLabels(Collections.emptyMap())
        .setMetadata(Collections.emptyMap());

    final MonitorContentProperties properties = new MonitorContentProperties();
    final MonitorContentRenderer renderer = new MonitorContentRenderer(properties);

    final String rendered = renderer.render(
        "value=${resource.wrong.reference}",
        resource
    );
  }

  @Test
  public void testDottedLabelFields() throws InvalidTemplateException {
    final ResourceDTO resource = new ResourceDTO()
        .setLabels(Collections.singletonMap("agent.discovered.os", "linux"))
        .setMetadata(Collections.emptyMap());

    final MonitorContentProperties properties = new MonitorContentProperties();
    final MonitorContentRenderer renderer = new MonitorContentRenderer(properties);

    final String rendered = renderer.render(
        // Attempt #1, resolves to default, empty string
//        "os=${resource.labels.agent.discovered.os}",
        // Attempt #2, resolves to default, empty string
//        "os=${resource.labels.'agent.discovered.os'}",
        // Attempt #3, works
        "os=${#resource.labels}${agent.discovered.os}${/resource.labels}",
        resource
    );

    assertThat(rendered, equalTo("os=linux"));
  }

  @Test
  public void testUnderscoredLabelFields() throws InvalidTemplateException {
    final ResourceDTO resource = new ResourceDTO()
        .setLabels(Collections.singletonMap("agent_discovered_os", "linux"))
        .setMetadata(Collections.emptyMap());

    final MonitorContentProperties properties = new MonitorContentProperties();
    final MonitorContentRenderer renderer = new MonitorContentRenderer(properties);

    final String rendered = renderer.render(
        "os=${resource.labels.agent_discovered_os}",
        resource
    );

    assertThat(rendered, equalTo("os=linux"));
  }

  @Test
  public void testDashedLabelFields() throws InvalidTemplateException {
    final ResourceDTO resource = new ResourceDTO()
        .setLabels(Collections.singletonMap("agent-discovered-os", "linux"))
        .setMetadata(Collections.emptyMap());

    final MonitorContentProperties properties = new MonitorContentProperties();
    final MonitorContentRenderer renderer = new MonitorContentRenderer(properties);

    final String rendered = renderer.render(
        "os=${resource.labels.agent-discovered-os}",
        resource
    );

    assertThat(rendered, equalTo("os=linux"));
  }
}