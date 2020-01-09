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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MonitorContentRenderer {

  private static final String CTX_RESOURCE = "resource";
  private static final String REDELIM_L = "[[";
  private static final String REDELIM_R = "]]";
  private static final String REDELIMS = REDELIM_L + " " + REDELIM_R;

  private final ObjectMapper objectMapper;
  /**
   * This template compiler will be run first to replace quoted placeholder variable usage with the JSON
   * rendered value itself. For example, if the metadata hosts is a list of host1,host2,host3
   * this JSON provided by the user:
   *
   *    "pluginHosts": "${resource.metadata.hosts}"
   *
   * becomes
   *
   *    "pluginHosts": ["host1","host2","host3"]
   */
  private final Compiler mustacheWholeValueCompiler;
  /**
   * In contrast to the previous, this template compiler will only look for the regular delimited
   * placeholders and also strip any quotes from the rendered placeholder value. With the same
   * metadata, this user provided JSON:
   *
   *     "description": "Hosts will be ${resource.metadata.hosts}"
   *
   * becomes
   *
   *     "description": "Hosts will be [host1,host2,host3]"
   */
  private final Compiler mustacheEmbeddedCompiler;
  private final Pattern quotedPlaceholderPattern;

  @Autowired
  public MonitorContentRenderer(MonitorContentProperties properties, ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;

    mustacheWholeValueCompiler = Mustache.compiler()
        .withEscaper(Escapers.NONE)
        // Use a delimiter that differs from the configured one
        .withDelims(REDELIMS)
        .withFormatter(new JsonValueFormatter(false));
    mustacheEmbeddedCompiler = Mustache.compiler()
        .withEscaper(Escapers.NONE)
        .withDelims(properties.getPlaceholderDelimiters())
        // tell formatter to strip quotes since these replacements would happen within an already
        // quoted field
        .withFormatter(new JsonValueFormatter(true));

    // The following regex is looking for placeholder variables that are immediately surrounded
    // by double quotes, such as
    //
    //   "${resource.metadata.url}"
    //
    // It then has a matching group to group the is best explained with an example:
    // if the delimiters is configured with the following, note the required space between delimiters
    //   ${ }
    // then the regex pattern becomes
    //   "\$\{(.+?)\}"
    quotedPlaceholderPattern = Pattern.compile("\"" +
        escapeRegexChars(properties.getPlaceholderDelimiters())
            .replace(" ", "(.+?)")
        + "\"");
  }

  private static String escapeRegexChars(String raw) {
    // This replacement is MUCH simpler than it looks -- it's just putting a backslash
    // in front of the characters
    //   $ { } [ ]
    // since they have special meaning in regex patterns and the placeholder delimiters are likely
    // to use some or all of those characters.
    return raw.replaceAll("[${}[\\\\]]", "\\\\$0");
  }

  String render(String content, ResourceDTO resource) throws InvalidTemplateException {
    final Map<String, Object> context = new HashMap<>();
    context.put(CTX_RESOURCE, resource);

    content = redelimitQuotedPlaceholders(content);

    try {
      // First pass using the broader whole-value template compiler
      content = mustacheWholeValueCompiler
          .compile(content).execute(context);

      // Second pass using the template compiler with the original delimiters
      content = mustacheEmbeddedCompiler
          .compile(content).execute(context);

      return content;
    } catch (MustacheException e) {
      throw new InvalidTemplateException(
          String.format("Unable to render monitor content template, content=%s, resource=%s",
              content, resource),
          e);
    }
  }

  private String redelimitQuotedPlaceholders(String rawContent) {
    final Matcher m = quotedPlaceholderPattern.matcher(rawContent);

    final StringBuilder result = new StringBuilder();

    while (m.find()) {
      m.appendReplacement(result, REDELIM_L+"$1"+REDELIM_R);
    }
    m.appendTail(result);

    return result.toString();
  }

  private class JsonValueFormatter implements Formatter {

    private final boolean stripQuotes;

    public JsonValueFormatter(boolean stripQuotes) {
      this.stripQuotes = stripQuotes;
    }

    @Override
    public String format(Object value) {
      try {
        String content = objectMapper.writeValueAsString(value);
        if (stripQuotes) {
          content = content.replaceAll("\"", "");
        }
        return content;
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(
            String.format("Unable to format the value '%s' while rendering monitor content", value), e);
      }
    }
  }

}
