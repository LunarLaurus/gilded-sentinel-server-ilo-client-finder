package net.laurus.service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.laurus.component.NetworkCache;
import net.laurus.network.IPv4Address;

/**
 * Monitors the heartbeat of registered clients and ensures their
 * responsiveness.
 * <p>
 * This service:
 * <ul>
 * <li>Caches and updates `lastUpdatedTimeForClientService` for clients using
 * Caffeine.</li>
 * <li>Periodically checks client activity via heartbeats.</li>
 * <li>Logs unresponsive or inactive clients and manages blacklisting.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientHeartbeatService {

	private static final long CACHE_EXPIRY_SECONDS = 600; // Cache expiry time in seconds (10 minutes)
	private static final long HEARTBEAT_INTERVAL_MS = 60_000; // 60 seconds
	private static final long INITIAL_DELAY_MS = 10_000; // 10 seconds

	private Cache<IPv4Address, Long> clientCache;

	private final AuthService authService;
	private final NetworkCache networkCache;

	@Value("${client.responsiveness.threshold.ms:300000}") // Default: 5 minutes in milliseconds
	private long responsivenessThresholdMs;

	/**
	 * Initializes the Caffeine cache with an eviction policy.
	 */
	@PostConstruct
	public void initializeCache() {
		clientCache = Caffeine.newBuilder().expireAfterWrite(CACHE_EXPIRY_SECONDS, TimeUnit.SECONDS).maximumSize(1000)
				.build();

		log.info("ClientCacheService initialized with expiry time of {} seconds and max size of 1000.",
				CACHE_EXPIRY_SECONDS);
	}

	/**
	 * Updates the `lastUpdatedTimeForClientService` for a client. If the client
	 * does not already exist in the cache, it is added.
	 *
	 * @param clientIp  The {@link IPv4Address} of the client.
	 * @param timestamp The timestamp in milliseconds since epoch.
	 */
	public void updateClientTimestamp(IPv4Address clientIp, long timestamp) {
		clientCache.put(clientIp, timestamp);
		log.info("Updated lastUpdatedTimeForClientService for client {}: {}", clientIp.getAddress(),
				Instant.ofEpochMilli(timestamp));
	}

	/**
	 * Retrieves the `lastUpdatedTimeForClientService` for a client.
	 *
	 * @param clientIp The {@link IPv4Address} of the client.
	 * @return The timestamp in milliseconds since epoch, or {@code null} if the
	 *         client is not cached.
	 */
	private Long getClientTimestamp(IPv4Address clientIp) {
		return clientCache.getIfPresent(clientIp);
	}

	/**
	 * Checks if a client is in the cache.
	 *
	 * @param clientIp The {@link IPv4Address} of the client.
	 * @return {@code true} if the client exists in the cache; {@code false}
	 *         otherwise.
	 */
	private boolean isClientCached(IPv4Address clientIp) {
		return clientCache.getIfPresent(clientIp) != null;
	}

	/**
	 * Removes a client from the cache.
	 *
	 * @param clientIp The {@link IPv4Address} of the client.
	 */
	public void evictClient(IPv4Address clientIp) {
		clientCache.invalidate(clientIp);
		log.info("Evicted client {} from cache.", clientIp.getAddress());
	}

	/**
	 * Clears the entire cache.
	 */
	public void clearCache() {
		clientCache.invalidateAll();
		log.info("Cleared all entries from client cache.");
	}

	/**
	 * Periodically sends a heartbeat to all registered clients.
	 * <p>
	 * This method:
	 * <ul>
	 * <li>Checks the status of registered clients.</li>
	 * <li>Logs unresponsive or blacklisted clients.</li>
	 * <li>Marks inactive clients for further processing.</li>
	 * </ul>
	 */
	@Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
	public void monitorRegisteredClients() {
		List<IPv4Address> cachedAddresses = IPv4Address.fromBitmap(
				authService.getSystemConfig().getIlo().getNetwork().getBaseAddress(),
				networkCache.getActiveClients().getActiveIndexes());

		if (cachedAddresses.isEmpty()) {
			log.warn("No cached addresses found. Skipping heartbeat check.");
			return;
		}

		log.info("Starting heartbeat check for {} registered clients.", cachedAddresses.size());

		// Perform heartbeat checks asynchronously
		cachedAddresses.forEach(clientIp -> CompletableFuture.runAsync(() -> checkClientHeartbeat(clientIp)));

		log.info("Heartbeat check completed for all registered clients.");
	}

	/**
	 * Checks the heartbeat of a single registered client.
	 * <p>
	 * If the client is blacklisted, unregistered, or evicted from the cache, it
	 * logs a warning and updates the blacklist as necessary.
	 *
	 * @param clientIp The {@link IPv4Address} of the client to check.
	 */
	private void checkClientHeartbeat(IPv4Address clientIp) {
		try {
			// Skip blacklisted clients
			if (networkCache.isBlacklisted(clientIp)) {
				log.info("Skipping blacklisted client: {}", clientIp.getAddress());
				return;
			}

			// Check if the client is present in the cache
			if (!isClientCached(clientIp)) {
				log.warn("Client {} evicted from cache due to expiry.", clientIp.getAddress());
				return;
			}

			// Check client responsiveness based on the last update time
			boolean isClientResponsive = isClientResponsiveBasedOnLastUpdate(clientIp);

			if (isClientResponsive) {
				log.info("Client {} responded to heartbeat.", clientIp.getAddress());
				updateClientTimestamp(clientIp, System.currentTimeMillis());
			} else {
				log.warn("Client {} failed to respond to heartbeat.", clientIp.getAddress());
			}
		} catch (Exception e) {
			log.error("Error during heartbeat check for client {}: {}", clientIp.getAddress(), e.getMessage(), e);
		}
	}

	/**
	 * Checks if a client is responsive based on their last update time.
	 * <p>
	 * A client is considered "responsive" if the time since their last update is
	 * within the configured responsiveness threshold.
	 *
	 * @param clientIp The {@link IPv4Address} of the client to check.
	 * @return {@code true} if the client is responsive; {@code false} otherwise.
	 */
	private boolean isClientResponsiveBasedOnLastUpdate(IPv4Address clientIp) {
		try {
			// Retrieve the client's last updated time from the registration cache
			Long lastUpdatedTime = getClientTimestamp(clientIp);

			if (lastUpdatedTime == null) {
				log.warn("No lastUpdatedTime found for client {}. Assuming unresponsive.", clientIp.getAddress());
				return false;
			}

			// Calculate time since last update
			long timeSinceLastUpdate = System.currentTimeMillis() - lastUpdatedTime;

			// Determine if the client is responsive
			boolean isResponsive = timeSinceLastUpdate <= responsivenessThresholdMs;

			log.debug("Client {} last updated {} ms ago. Responsive: {}", clientIp.getAddress(), timeSinceLastUpdate,
					isResponsive);

			return isResponsive;
		} catch (Exception e) {
			log.error("Error determining responsiveness for client {}: {}", clientIp.getAddress(), e.getMessage(), e);
			return false;
		}
	}
}
