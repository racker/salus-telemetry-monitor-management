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
import com.rackspace.salus.monitor_management.web.model.RemoteMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.telegraf.PostgresqlRemote;
import com.rackspace.salus.monitor_management.web.validator.PostgresqlRemoteValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class PostgresqlRemoteValidatorTest {

  private LocalValidatorFactoryBean validatorFactoryBean;

  @Before
  public void setup() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }

  @Test
  public void testValid() {

    final PostgresqlRemote plugin = new PostgresqlRemote();
    plugin.setAddress("host=localhost user=postgres sslmode=disable");
    final RemoteMonitorDetails details = new RemoteMonitorDetails();
    details.setPlugin(plugin);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setDetails(details);

    final Set<ConstraintViolation<DetailedMonitorInput>> errors = validatorFactoryBean.validate(input);

    assertThat(errors, hasSize(0));
  }
  @Test
  public void testInvalid() {

    List<String> l = List.of("1");
    final PostgresqlRemote plugin = new PostgresqlRemote();
    plugin.setAddress("host=localhost user=postgres sslmode=disable");
    plugin.setDatabases(l).setIgnoredDatabases(l);

    final RemoteMonitorDetails details = new RemoteMonitorDetails();
    details.setPlugin(plugin);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setDetails(details);

    final Set<ConstraintViolation<DetailedMonitorInput>> errors = validatorFactoryBean.validate(input);
    assertThat(errors, hasSize(1));
    assertThat(new ArrayList<>(errors).get(0).getMessage(),
        containsString(PostgresqlRemoteValidator.AtMostOneOf.DEFAULT_MESSAGE));
  }

}
