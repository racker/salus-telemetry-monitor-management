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

package com.rackspace.salus.monitor_management.web.error;

import com.rackspace.salus.telemetry.errors.AlreadyExistsException;
import com.rackspace.salus.telemetry.model.NotFoundException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.ServletWebRequest;

@ControllerAdvice(basePackages = "com.rackspace.salus.monitor_management.web")
@ResponseBody
public class RestExceptionHandler {

  private final ErrorAttributes errorAttributes;

  @Autowired
  public RestExceptionHandler(ErrorAttributes errorAttributes) {
    this.errorAttributes = errorAttributes;
  }

  @ExceptionHandler({IllegalArgumentException.class})
  public ResponseEntity<?> handleBadRequest(
      HttpServletRequest request) {
    return respondWith(request, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler({NotFoundException.class})
  public ResponseEntity<?> handleNotFound(
      HttpServletRequest request) {
    return respondWith(request, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler({AlreadyExistsException.class})
  public ResponseEntity<?> handleAlreadyExists(
      HttpServletRequest request) {
    return respondWith(request, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  private ResponseEntity<?> respondWith(HttpServletRequest request,
                                        HttpStatus status) {
    Map<String, Object> body = getErrorAttributes(request);
    body.put("status", status.value());
    return new ResponseEntity<>(body, status);
  }

  private Map<String, Object> getErrorAttributes(HttpServletRequest request) {
    final ServletWebRequest webRequest = new ServletWebRequest(request);
    return errorAttributes.getErrorAttributes(webRequest, false);
  }
}
