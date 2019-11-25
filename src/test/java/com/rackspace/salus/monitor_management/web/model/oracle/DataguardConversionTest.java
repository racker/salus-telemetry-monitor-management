package com.rackspace.salus.monitor_management.web.model.oracle;

import static com.rackspace.salus.monitor_management.web.model.ConversionHelpers.assertCommon;
import static com.rackspace.salus.monitor_management.web.model.ConversionHelpers.createMonitor;
import static com.rackspace.salus.test.JsonTestUtils.readContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.rackspace.salus.monitor_management.services.MonitorConversionService;
import com.rackspace.salus.monitor_management.utils.MetadataUtils;
import com.rackspace.salus.monitor_management.web.converter.PatchHelper;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorInput;
import com.rackspace.salus.monitor_management.web.model.DetailedMonitorOutput;
import com.rackspace.salus.monitor_management.web.model.LocalMonitorDetails;
import com.rackspace.salus.monitor_management.web.model.MonitorCU;
import com.rackspace.salus.policy.manage.web.client.PolicyApi;
import com.rackspace.salus.telemetry.entities.Monitor;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.repositories.MonitorRepository;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@JsonTest
@Import({MonitorConversionService.class, MetadataUtils.class})
public class DataguardConversionTest {
  @Configuration
  public static class TestConfig { }

  @MockBean
  PatchHelper patchHelper;

  @MockBean
  PolicyApi policyApi;

  @MockBean
  MonitorRepository monitorRepository;

  @Autowired
  MonitorConversionService conversionService;

  @Autowired
  MetadataUtils metadataUtils;

  @Test
  public void convertToOutput_rman() throws IOException {
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_dataguard.json");

    Monitor monitor = createMonitor(content, "convertToOutput", AgentType.ORACLE,
        ConfigSelectorScope.LOCAL
    );

    final DetailedMonitorOutput result = conversionService.convertToOutput(monitor);

    final Dataguard dataguardPlugin = assertCommon(result, monitor, Dataguard.class, "convertToOutput");
    assertThat(dataguardPlugin.getFilePath());
    assertThat(dataguardPlugin.getDatabaseNames());
  }

  @Test
  public void convertFromInput_dataguard() throws IOException, JSONException {
    final Map<String, String> labels = new HashMap<>();
    labels.put("os", "linux");
    labels.put("test", "convertFromInput");

    final LocalMonitorDetails details = new LocalMonitorDetails();
    final Dataguard plugin = new Dataguard();
    plugin.setFilePath("./oracleDatabaseOutput");
    final List<String> databaseNames = new LinkedList<>();
    databaseNames.add("backupDB");
    databaseNames.add("prodDB");
    plugin.setDatabaseNames(databaseNames);
    details.setPlugin(plugin);

    DetailedMonitorInput input = new DetailedMonitorInput()
        .setName("name-a")
        .setLabelSelector(labels)
        .setDetails(details);
    final MonitorCU result = conversionService.convertFromInput(
        RandomStringUtils.randomAlphabetic(10), null, input);

    assertThat(result).isNotNull();
    assertThat(result.getLabelSelector()).isEqualTo(labels);
    assertThat(result.getAgentType()).isEqualTo(AgentType.ORACLE);
    assertThat(result.getMonitorName()).isEqualTo("name-a");
    assertThat(result.getSelectorScope()).isEqualTo(ConfigSelectorScope.LOCAL);
    final String content = readContent("/ConversionTests/MonitorConversionServiceTest_dataguard.json");
    JSONAssert.assertEquals(content, result.getContent(), true);
  }

}
