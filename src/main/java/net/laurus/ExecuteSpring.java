package net.laurus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = { 
		"net.laurus.app",
		"net.laurus.client",
		"net.laurus.component",
		"net.laurus.controller",
		"net.laurus.queue",
		"net.laurus.service" })
@EnableAsync
@EnableConfigurationProperties
public class ExecuteSpring {
	
	public static void main(String[] args) {
		SpringApplication.run(ExecuteSpring.class, args);
	}	

}
