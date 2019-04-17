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

import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.telemetry.model.Resource;
import com.samskivert.mustache.Escapers;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.MustacheException;
import com.samskivert.mustache.Template;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MonitorContentRenderer {

  private static final String CTX_RESOURCE = "resource";

  private final Compiler mustacheCompiler;

  @Autowired
  public MonitorContentRenderer(MonitorContentProperties properties) {
    mustacheCompiler = Mustache.compiler().withEscaper(Escapers.NONE).withDelims(properties.getPlaceholderDelimiters());
  }

  public String render(String rawContent, Resource resource) {
    final Template template = mustacheCompiler.compile(rawContent);

    final Map<String, Object> context = new HashMap<>();
    context.put(CTX_RESOURCE, resource);

    try {
      return template.execute(context);
    } catch (MustacheException e) {
      throw new IllegalArgumentException("Unable to render monitor content template", e);
    }
  }
}
