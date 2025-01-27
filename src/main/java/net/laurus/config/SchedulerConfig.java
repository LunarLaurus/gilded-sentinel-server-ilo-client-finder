package net.laurus.config;

import static java.util.concurrent.TimeUnit.HOURS;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import com.github.benmanes.caffeine.cache.Caffeine;

import net.laurus.thread.LaurusThreadFactory;

@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

	public Executor taskExecutor() {
		return Executors.newScheduledThreadPool(10, new LaurusThreadFactory("Yogg-Saron", false));
	}

	@Override
	public void configureTasks(@NonNull ScheduledTaskRegistrar taskRegistrar) {
		taskRegistrar.setScheduler(taskExecutor());
	}

	@Bean
	public Caffeine<Object, Object> caffeineConfig() {
		return Caffeine.newBuilder().expireAfterAccess(1, HOURS).maximumSize(1000);
	}

	@Bean
	public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager();
		cacheManager.setCaffeine(caffeine);
		return cacheManager;
	}
}
