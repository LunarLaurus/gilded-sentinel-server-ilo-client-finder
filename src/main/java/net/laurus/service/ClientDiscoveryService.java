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

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientDiscoveryService {

    private final NetworkCache networkCache;
    private final IloNetworkClient networkClient;
    private final NewClientRegistrationQueueHandler newClientQueue;
    private final SubnetManager subnetManager;
    
    @PostConstruct
    public void setup() {
        networkCache.setCachedAddresses(subnetManager.generateAddressRange());
    }

    @Scheduled(fixedRate = 1000 * 60 * 10, initialDelay = 1000 * 5)
    public void scanNetworkForClients() {
        final List<IPv4Address> addresses = networkCache.getCachedAddresses();
        if (addresses.isEmpty()) {
            log.info("Found no client addresses in network cache.");
            return;
        }
        log.info("Found "+addresses.size()+" client addresses in network cache.");

        CompletableFuture<Bitmap> futureBitmap = NetworkUtil.mapClientsToBitmapAsync(
                addresses,
                networkClient::isIloClientAsync,
                subnetManager.getSubnetMask(),
                networkCache.getBlacklist()
        );
        try {
            log.info("Populating client bitmap.");
            networkCache.setActiveClients(futureBitmap.join());
            log.info("Done discovering clients.");
        } catch (Exception e) {
            log.error("Error scanning network: {}", e.getMessage());
        }
    }
    

    @Scheduled(fixedRate = 1000 * 15, initialDelay = 1000 * 30)
    public void processDiscoveredClients() {
        final List<Integer> activeindicies = networkCache.getActiveClients().getActiveIndexes();
        if (activeindicies.isEmpty()) {
            log.debug("Found no active indicies in client bitmap.");
            return;
        }
        log.info("Found "+activeindicies.size()+" active indicies in client bitmap.");
        processClientRequests(networkCache.getActiveClients());
    }
    
    private void processClientRequests(Bitmap bitmap) {
        log.info("Processing mapped clients bitmap. Active: "+bitmap.getActiveIndexes().size());
        bitmap.getActiveIndexes().stream().forEach( addressIndex -> {
            IPv4Address ip = networkCache.getCachedAddresses().get(addressIndex);
            boolean blacklisted = networkCache.isBlacklisted(ip);
            boolean ipRegistered = newClientQueue.getRegistrationHandler().isClientRegistered(ip);
            if (!blacklisted && !ipRegistered) {
                log.info("Registering new Ilo client: {}", ip.getAddress());
                newClientQueue.processNewClientRequest(new IloRegistrationRequest(ip));
            }
        });
    }

}
