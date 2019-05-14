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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.monitor_management.web.model.ZoneCreatePrivate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class ValidCidrListValidatorTest {

  private LocalValidatorFactoryBean validatorFactoryBean;

  @Before
  public void setup() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }

  @Test
  public void testEmptyList() {
    List<String> addresses = Collections.emptyList();
    validScenario(addresses);
  }

  @Test
  public void testValidIpv4List() {
    List<String> addresses = new ArrayList<>();
    addresses.add("50.57.61.0/26");
    addresses.add("159.135.132.32/27");
    addresses.add("78.136.44.0/26");

    validScenario(addresses);
  }

  @Test
  public void testNonCidrIpv4Address() {
    List<String> addresses = new ArrayList<>();
    addresses.add("127.0.0.1");

    failingScenario(addresses);
  }

  @Test
  public void testValidIpv6List() {
    List<String> addresses = new ArrayList<>();
    addresses.add("2001:4801:7902:0001::/64");
    addresses.add("2a00:1a48:7902:0001::/64");
    addresses.add("2001:4802:7902:0001::/64");

    validScenario(addresses);
  }

  @Test
  public void testNonCidrIpv6Address() {
    List<String> addresses = new ArrayList<>();
    addresses.add("::1");

    failingScenario(addresses);
  }

  private void validScenario(List<String> addresses) {
    final ZoneCreatePrivate zone = new ZoneCreatePrivate()
        .setName("test")
        .setSourceIpAddresses(addresses);

    final Set<ConstraintViolation<ZoneCreatePrivate>> errors = validatorFactoryBean.validate(zone);

    assertThat(errors, hasSize(0));
  }

  private void failingScenario(List<String> addresses) {
    final ZoneCreatePrivate zone = new ZoneCreatePrivate()
        .setName("test")
        .setSourceIpAddresses(addresses);

    final Set<ConstraintViolation<ZoneCreatePrivate>> errors = validatorFactoryBean.validate(zone);

    assertThat(errors, hasSize(1));
    final ConstraintViolation<ZoneCreatePrivate> violation = errors.iterator().next();
    assertThat(violation.getPropertyPath().toString(), equalTo("sourceIpAddresses"));
    assertThat(violation.getMessage(), equalTo("All values must be valid CIDR notation"));
  }
}
