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

package com.rackspace.salus.monitor_management.web.model.validator;

import com.google.common.net.HostAndPort;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.InetAddressValidator;

@Slf4j
public class ValidHostAndPortValidator implements ConstraintValidator<ValidHostAndPort, String> {
   public void initialize(ValidHostAndPort constraint) {
   }

   public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null) {
         // to allow for optional fields
         return true;
      }

      try {
         // throws if unable to parse
         final HostAndPort hostAndPort = HostAndPort.fromString(value);
         // throws if port wasn't included in parsed
         final int port = hostAndPort.getPort();

         // but the parsing above doesn't validate the actual IP/host part
         if (!InetAddressValidator.getInstance().isValid(hostAndPort.getHost()) &&
               !DomainValidator.getInstance(true).isValid(hostAndPort.getHost())) {
            log.trace("Value '{}' is not a valid IP address or domain", value);
            return false;
         }

         log.debug("Validated '{}' as {}:{}", value, hostAndPort.getHost(), port);
      } catch (IllegalArgumentException|IllegalStateException e) {
         log.trace("Failed to validate '{}'", value, e);
         return false;
      }
      return true;
   }

}
