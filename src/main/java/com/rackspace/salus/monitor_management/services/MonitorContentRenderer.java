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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.monitor_management.config.MonitorContentProperties;
import com.rackspace.salus.monitor_management.errors.InvalidTemplateException;
import com.rackspace.salus.resource_management.web.model.ResourceDTO;
import com.samskivert.mustache.Escapers;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Mustache.Formatter;
import com.samskivert.mustache.MustacheException;
import com.samskivert.mustache.Template;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MonitorContentRenderer {

  private static final String CTX_RESOURCE = "resource";

  private final Compiler mustacheCompiler;
  private final Pattern quotedPlaceholderPattern;
  private final ObjectMapper objectMapper;

  @Autowired
  public MonitorContentRenderer(MonitorContentProperties properties, ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    mustacheCompiler = Mustache.compiler()
        .withEscaper(Escapers.NONE)
        .withDelims(properties.getPlaceholderDelimiters())
        .withFormatter(new JsonValueFormatter());

    // The following is best explained with an example:
    // if the delimiters is configured with the following, note the required space between delimiters
    //   ${ }
    // then the regex pattern becomes
    //   "(\$\{.+?\})"
    quotedPlaceholderPattern = Pattern.compile("\"(" +
        escapeRegexChars(properties.getPlaceholderDelimiters())
            .replace(" ", ".+?")
        + ")\"");
  }

  private static String escapeRegexChars(String raw) {
    // This replacement is MUCH simpler than it looks -- it's just putting a backslash
    // in front of the characters
    //   $ { } [ ]
    // since they have special meaning in regex patterns and the placeholder delimiters are likely
    // to use some or all of those characters.
    return raw.replaceAll("[${}[\\\\]]", "\\\\$0");
  }

  String render(String rawContent, ResourceDTO resource) throws InvalidTemplateException {

    final Template template = mustacheCompiler.compile(
        unquotePlaceholders(rawContent)
    );

    final Map<String, Object> context = new HashMap<>();
    context.put(CTX_RESOURCE, resource);

    try {
      return template.execute(context);
    } catch (MustacheException e) {
      throw new InvalidTemplateException(
          String.format("Unable to render monitor content template, content=%s, resource=%s",
              rawContent, resource),
          e);
    }
  }

  /**
   * This method works in conjunction with {@link JsonValueFormatter} by preparing the following
   * type of content block:
   * <pre>
   *   {
   *     "interval": "${resource.metadata.per_resource_interval}",
   *     "host": "${resource.metadata.custom_host}"
   *   }
   * </pre>
   * into
   * <pre>
   *   {
   *     "interval": ${resource.metadata.per_resource_interval},
   *     "host": ${resource.metadata.custom_host}
   *   }
   * </pre>
   * so that non-string metadata values can be rendered correctly along with string values. Given
   * this metadata:
   * <pre>
   *   {
   *     "metadata": {
   *       "per_resource_interval": 30,
   *       "custom_host": "host-123"
   *     }
   *   }
   * </pre>
   * ...the content is rendered into:
   * <pre>
   *   {
   *     "interval" 30,
   *     "host": "host-123"
   *   }
   * </pre>
   * @param rawContent
   * @return
   */
  private String unquotePlaceholders(String rawContent) {
    final Matcher m = quotedPlaceholderPattern.matcher(rawContent);

    final StringBuilder result = new StringBuilder();

    while (m.find()) {
      m.appendReplacement(result, "$1");
    }
    m.appendTail(result);

    return result.toString();
  }

  /**
   * This formatter performs the second half of the processing described in
   * {@link MonitorContentRenderer#unquotePlaceholders(String)}. Each value is formatting using
   * a JSON {@link ObjectMapper} to ensure JSON compatible rendering of scalar and non-scalar types.
   */
  private class JsonValueFormatter implements Formatter {

    @Override
    public String format(Object value) {
      try {
        return objectMapper.writeValueAsString(value);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(
            String.format("Unable to format the value '%s' while rendering monitor content", value), e);
      }
    }
  }
}
