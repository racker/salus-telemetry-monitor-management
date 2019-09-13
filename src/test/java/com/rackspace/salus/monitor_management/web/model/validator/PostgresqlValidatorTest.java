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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.ValidationGroups;
import com.rackspace.salus.monitor_management.web.model.telegraf.Mem;
import com.rackspace.salus.monitor_management.web.model.telegraf.Postgresql;
import com.rackspace.salus.monitor_management.web.validator.PostgresqlValidator;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class PostgresqlValidatorTest {

  private LocalValidatorFactoryBean validatorFactoryBean;

  @Before
  public void setup() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }

  @Test
  public void testValid() {

    final Postgresql plugin = new Postgresql();
    plugin.setAddress("host=localhost user=postgres sslmode=disable");
    final LocalMonitorDetails details = new LocalMonitorDetails();
    details.setPlugin(plugin);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setDetails(details);

    final Set<ConstraintViolation<DetailedMonitorInput>> errors = validatorFactoryBean.validate(input);

    assertThat(errors, hasSize(0));
  }
  @Test
  public void testInvalid() {

    List<String> l = new ArrayList<>();
    l.add("1");
    final Postgresql plugin = new Postgresql();
    plugin.setAddress("host=localhost user=postgres sslmode=disable");
    plugin.setDatabases(l).setIgnoredDatabases(l);

    final LocalMonitorDetails details = new LocalMonitorDetails();
    details.setPlugin(plugin);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setDetails(details);

    final Set<ConstraintViolation<DetailedMonitorInput>> errors = validatorFactoryBean.validate(input);
    assertThat(errors, hasSize(1));
    assertThat(new ArrayList<>(errors).get(0).getMessage(),
        containsString(PostgresqlValidator.AtMostOneOf.DEFAULT_MESSAGE));
  }

}
