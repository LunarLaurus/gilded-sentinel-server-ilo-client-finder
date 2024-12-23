package net.laurus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for Redis.
 */
@Configuration
@ConfigurationProperties(prefix = "redis")
@Getter
@Setter
public class RedisConfig {

	/**
	 * Redis server host.
	 */
	private String host = "192.168.0.21";

	/**
	 * Redis server port.
	 */
	private int port = 6379;

	/**
	 * Maximum number of total connections in the pool.
	 */
	private int maxTotal = 50;

	/**
	 * Maximum number of idle connections in the pool.
	 */
	private int maxIdle = 30;

	/**
	 * Minimum number of idle connections in the pool.
	 */
	private int minIdle = 10;
	
}
