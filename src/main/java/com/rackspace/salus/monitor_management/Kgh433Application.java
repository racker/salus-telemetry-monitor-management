package com.rackspace.salus.monitor_management;

import com.rackspace.salus.monitor_management.entities.Monitor;
import com.rackspace.salus.monitor_management.repositories.MonitorRepository;
import com.rackspace.salus.telemetry.model.AgentType;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import java.util.Collections;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.transaction.ChainedTransactionManager;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

//@SpringBootApplication
@EntityScan({
		"com.rackspace.salus.telemetry.model",
		"com.rackspace.salus.monitor_management.entities"
})
@EnableTransactionManagement
public class Kgh433Application {

//	public static void main(String[] args) {
//		ConfigurableApplicationContext ctx = SpringApplication.run(Kgh433Application.class, args);
//		Map<String, PlatformTransactionManager> tms = ctx.getBeansOfType(PlatformTransactionManager.class);
//		System.out.println(tms);
//		ctx.close();
//	}
//
//	@Bean
//	public ApplicationRunner runner(Foo foo) {
//		return args -> foo.sendToKafkaAndDB();
//	}
//
//	@Bean
//	public JpaTransactionManager transactionManager() {
//		return new JpaTransactionManager();
//	}
//
//	@Bean
//	public ChainedTransactionManager chainedTxM(JpaTransactionManager jpa, KafkaTransactionManager<?, ?> kafka) {
//		return new ChainedTransactionManager(jpa, kafka);
//	}
//
//	@Component
//	public static class Foo {
//
//		@Autowired
//		private KafkaTemplate<Object, Object> template;
//
//		@Autowired
//		private MonitorRepository repo;
//
//
//		//@Transactional(transactionManager = "chainedTxM")
//		public void sendToKafkaAndDB() throws Exception {
//
//			final String content = "{\n"
//					+ "  \"type\": \"mem\"\n"
//					+ "}";
//
//			Monitor monitor = new Monitor()
//					.setAgentType(AgentType.TELEGRAF)
//					.setSelectorScope(ConfigSelectorScope.LOCAL)
//					.setLabelSelector(Collections.singletonMap("os","linux"))
//					.setContent(content);
//
//			this.repo.save(monitor);
//			System.out.println(this.template.send("kgh433", "bar").get());
//			System.out.println(this.template.send("kgh433", "baz").get());
//		}
//
//	}

}