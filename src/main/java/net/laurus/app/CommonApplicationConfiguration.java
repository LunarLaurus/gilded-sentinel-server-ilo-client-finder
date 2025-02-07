package net.laurus.app;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import net.laurus.spring.properties.SystemProperties;
import net.laurus.spring.service.IloAuthService;
import net.laurus.spring.service.RabbitQueueService;

@Configuration
@ConfigurationPropertiesScan("net.laurus.spring.properties")
@RequiredArgsConstructor
public class CommonApplicationConfiguration {

	private final SystemProperties systemProps;

	private final AmqpAdmin queueAdmin;

	private final RabbitTemplate queueTemplate;

	@Bean
	public IloAuthService iloAuthService() {
		return new IloAuthService(systemProps);
	}

	@Bean
	public RabbitQueueService rabbitQueueService() {
		return new RabbitQueueService(queueAdmin, queueTemplate);
	}

}