package net.laurus.controller;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides endpoints for system health checks.
 * <p>
 * Includes liveness, readiness, and memory usage checks for monitoring the
 * application.
 */
@RestController
public class HealthCheckController {

	private static final String HEALTH_OK = "OK";
	private static final String UNHEALTHY = "UNHEALTHY";
	private static final String HEALTH_FAIL = "FAIL";

	private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

	/**
	 * General health check endpoint.
	 * <p>
	 * Returns an HTTP 200 response if the application is healthy.
	 *
	 * @return {@code "OK"} if healthy, or {@code "UNHEALTHY"} if memory is
	 *         critically high.
	 */
	@GetMapping("/health")
	public ResponseEntity<String> healthCheck() {
		return checkMemoryHealth() ? ResponseEntity.ok(HEALTH_OK)
				: ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(UNHEALTHY);
	}

	/**
	 * Liveness check endpoint.
	 * <p>
	 * Verifies if the application is running properly.
	 *
	 * @return {@code "OK"} if the application is live.
	 */
	@GetMapping("/liveness")
	public ResponseEntity<String> livenessCheck() {
		return ResponseEntity.ok(HEALTH_OK);
	}

	/**
	 * Readiness check endpoint.
	 * <p>
	 * Verifies if all dependencies are ready to handle requests.
	 *
	 * @return {@code "OK"} if dependencies are ready, or {@code "UNHEALTHY"} if
	 *         not.
	 */
	@GetMapping("/readiness")
	public ResponseEntity<String> readinessCheck() {
		return checkDependencies() ? ResponseEntity.ok(HEALTH_OK)
				: ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(UNHEALTHY);
	}

	/**
	 * Checks memory usage and returns detailed health information.
	 * <p>
	 * Provides memory usage in MB and indicates if usage exceeds 80% of the max
	 * heap.
	 *
	 * @return {@code "OK"} with usage details if healthy, or {@code "FAIL"} with
	 *         details if high memory usage is detected.
	 */
	@GetMapping("/health/memory")
	public ResponseEntity<String> memoryHealthCheck() {
		MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
		long usedMemory = heapUsage.getUsed() / (1024 * 1024); // MB
		long maxMemory = heapUsage.getMax() / (1024 * 1024); // MB

		if (usedMemory < maxMemory * 0.8) {
			return ResponseEntity.ok(String.format("%s: %dMB used out of %dMB", HEALTH_OK, usedMemory, maxMemory));
		}

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
				String.format("%s: Memory usage high (%dMB used out of %dMB)", HEALTH_FAIL, usedMemory, maxMemory));
	}

	/**
	 * Simulates dependency readiness checks.
	 *
	 * @return {@code true} if all dependencies are ready.
	 */
	private boolean checkDependencies() {
		return true; // Replace with actual checks
	}

	/**
	 * Checks if memory usage is within safe limits (less than 80% of the max heap).
	 *
	 * @return {@code true} if memory usage is safe, {@code false} otherwise.
	 */
	private boolean checkMemoryHealth() {
		MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
		return heapUsage.getUsed() < (heapUsage.getMax() * 0.8);
	}
}
