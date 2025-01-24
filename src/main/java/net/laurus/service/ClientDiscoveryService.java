package net.laurus.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.laurus.client.IloNetworkClient;
import net.laurus.component.NetworkCache;
import net.laurus.component.SubnetManager;
import net.laurus.data.Bitmap;
import net.laurus.data.dto.ipmi.ilo.IloRegistrationRequest;
import net.laurus.network.IPv4Address;
import net.laurus.queue.NewClientRegistrationQueueHandler;
import net.laurus.util.NetworkUtil;

/**
 * Service responsible for discovering and processing iLO clients in the network.
 * <p>
 * This service periodically scans the network for iLO clients and processes newly discovered clients.
 * It relies on {@link NetworkCache}, {@link IloNetworkClient}, and {@link SubnetManager} for caching, 
 * validating, and managing network clients.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientDiscoveryService {

    private static final int NETWORK_SCAN_RATE_MS = 1000 * 60 * 5; // 5 minutes
    private static final int NETWORK_SCAN_INITIAL_DELAY_MS = 1000 * 5; // 5 seconds
    private static final int CLIENT_PROCESS_RATE_MS = 1000 * 30; // 30 seconds
    private static final int CLIENT_PROCESS_INITIAL_DELAY_MS = 1000 * 30; // 30 seconds

    private final NetworkCache networkCache;
    private final IloNetworkClient networkClient;
    private final NewClientRegistrationQueueHandler newClientQueue;
    private final SubnetManager subnetManager;

    /**
     * Initializes the service by populating the {@link NetworkCache} with the generated address range.
     */
    @PostConstruct
    public void initializeCache() {
        List<IPv4Address> addressRange = subnetManager.generateAddressRange();
        networkCache.setCachedAddresses(addressRange);
        log.info("Initialized network cache with {} addresses.", addressRange.size());
    }

    /**
     * Periodically scans the network to identify active iLO clients.
     * <p>
     * This method runs every 5 minutes with an initial delay of 5 seconds.
     * It maps active clients to a bitmap and updates the {@link NetworkCache}.
     * </p>
     */
    @Scheduled(fixedRate = NETWORK_SCAN_RATE_MS, initialDelay = NETWORK_SCAN_INITIAL_DELAY_MS)
    public void scanNetworkForClients() {
        List<IPv4Address> addresses = networkCache.getCachedAddresses();
        if (addresses.isEmpty()) {
            log.info("No client addresses found in network cache.");
            return;
        }

        log.info("Scanning {} addresses for active clients.", addresses.size());
        CompletableFuture<Bitmap> futureBitmap = NetworkUtil.mapClientsToBitmapAsync(
                addresses,
                networkClient::isIloClient,
                subnetManager.getSubnetMask(),
                networkCache.getBlacklist()
        );

        futureBitmap.thenAccept(bitmap -> {
            networkCache.setActiveClients(bitmap);
            log.info("Updated active client bitmap with {} active clients.", bitmap.getActiveIndexes().size());
        }).exceptionally(e -> {
            log.error("Error during network scan: {}", e.getMessage());
            return null;
        });
    }

    /**
     * Periodically processes newly discovered active clients.
     * <p>
     * This method runs every 30 seconds with an initial delay of 30 seconds.
     * It iterates over the active client bitmap, checking each address for registration or blacklist status,
     * and registers unregistered clients.
     * </p>
     */
    @Scheduled(fixedRate = CLIENT_PROCESS_RATE_MS, initialDelay = CLIENT_PROCESS_INITIAL_DELAY_MS)
    public void processDiscoveredClients() {
        Bitmap activeClients = networkCache.getActiveClients();
        List<Integer> activeIndexes = activeClients.getActiveIndexes();

        if (activeIndexes.isEmpty()) {
            log.debug("No active clients found in bitmap.");
            return;
        }

        log.info("Processing {} active clients.", activeIndexes.size());
        activeIndexes.parallelStream()
            .map(index -> networkCache.getCachedAddresses().get(index))
            .filter(ip -> !newClientQueue.getRegistrationHandler().isClientRegistered(ip)) // Early check
            .forEach(this::processClient);
    }

    /**
     * Processes a single client, ensuring it is not blacklisted before registration.
     *
     * @param ip The {@link IPv4Address} to process.
     */
    private void processClient(IPv4Address ip) {
        try {
            if (networkCache.isBlacklisted(ip)) {
                log.debug("Skipping blacklisted IP: {}", ip.toString());
                return;
            }

            log.info("Registering new iLO client: {}", ip.toString());
            newClientQueue.processNewClientRequest(new IloRegistrationRequest(ip));
        } catch (Exception e) {
            log.error("Error processing client {}: {}", ip.toString(), e.getMessage());
        }
    }
}
