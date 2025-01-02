package net.laurus;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = { 
		"net.laurus.client",
		"net.laurus.component",
		"net.laurus.config",
		"net.laurus.controller",
		"net.laurus.queue",
		"net.laurus.service", })
@EnableScheduling
@EnableAsync
@EnableCaching
@EnableRabbit
@EnableConfigurationProperties
public class ExecuteSpring {

	public static void main(String[] args) {
		SpringApplication.run(ExecuteSpring.class, args);
	}

}
