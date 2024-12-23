package net.laurus;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = { 
		"net.laurus.config",
		"net.laurus.component",
		"net.laurus.service",
		"net.laurus.controller" })
@EnableScheduling
@EnableAsync
@EnableRabbit
@EnableConfigurationProperties
public class ExecuteSpring {

	public static void main(String[] args) {
		SpringApplication.run(ExecuteSpring.class, args);
	}

}
