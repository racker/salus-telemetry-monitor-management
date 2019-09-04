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

package com.rackspace.salus.monitor_management.web.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.rackspace.salus.monitor_management.web.model.telegraf.Cpu;
import com.rackspace.salus.monitor_management.web.model.telegraf.Ping;
import com.rackspace.salus.telemetry.model.LabelSelectorMethod;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@JsonTest
public class DetailedMonitorOutputTest {

  @Autowired
  private JacksonTester<DetailedMonitorOutput> json;

  @Test
  public void testLocalMonitor() throws IOException {
    final DetailedMonitorOutput detailedMonitorOutput = new DetailedMonitorOutput()
        .setId("m-1")
        .setResourceId("r-1")
        .setName("name-1")
        .setLabelSelector(
            Map.of("key1", "val1", "key2", "val2")
        )
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setInterval(Duration.ofSeconds(90))
        .setDetails(new LocalMonitorDetails()
            .setPlugin(new Cpu()
                .setCollectCpuTime(false)
                .setPercpu(true)
                .setReportActive(false)
                .setTotalcpu(true)
            )
        )
        .setCreatedTimestamp(Instant.EPOCH.toString())
        .setUpdatedTimestamp(Instant.EPOCH.plusSeconds(1).toString());

    assertThat(json.write(detailedMonitorOutput))
        .isEqualToJson("/DetailedMonitorOutputTest/local.json", JSONCompareMode.STRICT);
  }

  @Test
  public void testRemoteMonitor() throws IOException {
    final DetailedMonitorOutput detailedMonitorOutput = new DetailedMonitorOutput()
        .setId("m-1")
        .setResourceId("r-1")
        .setName("name-1")
        .setLabelSelector(
            Map.of("key1", "val1", "key2", "val2")
        )
        .setLabelSelectorMethod(LabelSelectorMethod.AND)
        .setInterval(Duration.ofSeconds(90))
        .setDetails(new RemoteMonitorDetails()
            .setMonitoringZones(List.of("z-1"))
            .setPlugin(new Ping()
                .setUrls(List.of("localhost:22"))
                .setCount(5)
                .setDeadline(10)
                .setPingInterval(15)
                .setTimeout(20)
            )
        )
        .setCreatedTimestamp(Instant.EPOCH.toString())
        .setUpdatedTimestamp(Instant.EPOCH.plusSeconds(1).toString());

    assertThat(json.write(detailedMonitorOutput))
        .isEqualToJson("/DetailedMonitorOutputTest/remote.json", JSONCompareMode.STRICT);
  }
}