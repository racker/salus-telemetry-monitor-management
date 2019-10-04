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

package com.rackspace.salus.monitor_management.web.converter;

import static com.rackspace.salus.monitor_management.web.converter.PatchHelper.JSON_MERGE_PATCH_TYPE;

import javax.json.Json;
import javax.json.JsonMergePatch;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Component;

@Component
public class JsonMergePatchHttpMessageConverter extends AbstractHttpMessageConverter<JsonMergePatch> {

  public JsonMergePatchHttpMessageConverter() {
    super(MediaType.valueOf(JSON_MERGE_PATCH_TYPE));
  }

  @Override
  protected boolean supports(Class<?> clazz) {
    return JsonMergePatch.class.isAssignableFrom(clazz);
  }

  @Override
  protected JsonMergePatch readInternal(Class<? extends JsonMergePatch> clazz, HttpInputMessage inputMessage)
      throws HttpMessageNotReadableException {

    try (JsonReader reader = Json.createReader(inputMessage.getBody())) {
      return Json.createMergePatch(reader.readValue());
    } catch (Exception e) {
      throw new HttpMessageNotReadableException(e.getMessage(), inputMessage);
    }
  }

  @Override
  protected void writeInternal(JsonMergePatch jsonMergePatch, HttpOutputMessage outputMessage)
      throws HttpMessageNotWritableException {

    try (JsonWriter writer = Json.createWriter(outputMessage.getBody())) {
      writer.write(jsonMergePatch.toJsonValue());
    } catch (Exception e) {
      throw new HttpMessageNotWritableException(e.getMessage(), e);
    }
  }
}
