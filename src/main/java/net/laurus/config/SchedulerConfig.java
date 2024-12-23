package net.laurus.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import net.laurus.thread.LaurusThreadFactory;

@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

    public Executor taskExecutor() {
        return Executors.newScheduledThreadPool(10, new LaurusThreadFactory("Yogg-Saron", false));
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskExecutor());
    }
}
