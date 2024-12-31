package net.laurus.controller;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

    private static final String HEALTH_OK = "OK";
    private static final String UNHEALTHY = "UNHEALTHY";
    private static final String HEALTH_FAIL = "FAIL";

    // Combined health check to avoid duplicate code
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok(HEALTH_OK);
    }

    // Liveness check: Is the app running properly?
    @GetMapping("/liveness")
    public ResponseEntity<String> livenessCheck() {
        return checkOverallHealth();
    }

    // Readiness check: Are dependencies ready to handle requests?
    @GetMapping("/readiness")
    public ResponseEntity<String> readinessCheck() {
        return checkDependencies() 
        		? ResponseEntity.ok(HEALTH_OK) 
        		: ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(UNHEALTHY);
    }

    private boolean checkDependencies() {
		return true;
	}

	// Combined health check logic to avoid code duplication
    private ResponseEntity<String> checkOverallHealth() {
        if (checkMemoryHealth()) {
            return ResponseEntity.ok(HEALTH_OK);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(UNHEALTHY);
    }

    // System memory usage health check
    private boolean checkMemoryHealth() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long usedMemory = heapUsage.getUsed() / (1024 * 1024);  // Convert to MB
        long maxMemory = heapUsage.getMax() / (1024 * 1024);    // Convert to MB
        return usedMemory < (maxMemory * 0.8);  // Example: OK if < 80% of max memory used
    }

    // Memory health detailed check (returns memory usage data)
    @GetMapping("/health/memory")
    public ResponseEntity<String> memoryHealthCheck() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long usedMemory = heapUsage.getUsed() / (1024 * 1024);  // Convert to MB
        long maxMemory = heapUsage.getMax() / (1024 * 1024);    // Convert to MB

        return usedMemory < (maxMemory * 0.8)
            ? ResponseEntity.ok(HEALTH_OK + ": " + usedMemory + "MB used out of " + maxMemory + "MB")
            : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(HEALTH_FAIL + ": Memory usage high (" + usedMemory + "MB used out of " + maxMemory + "MB)");
    }
}
