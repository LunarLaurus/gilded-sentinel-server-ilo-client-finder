package net.laurus.component;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.laurus.network.IPv4Address;

@Component
@Slf4j
public class RegistrationCache {

    /**
     * Check if the client is already registered.
     * 
     * @param clientIp the unique key for the client
     * @return true if the client is already registered, false otherwise
     */
    @Cacheable(value = "clientRegistrationCache")
    public boolean isClientRegistered(IPv4Address clientIp) {
        log.debug("Client {} is not yet registered. Returning false.", clientIp);
        return false; // Default return when the key is not in the cache
    }

    /**
     * Mark the client as registered.
     * 
     * @param clientIp the unique key for the client
     * @return true to indicate registration
     */
    @CachePut(value = "clientRegistrationCache")
    public boolean registerClient(IPv4Address clientIp) {
        log.info("Marking client {} as registered.", clientIp);
        return true; // Cache the fact that this client is registered
    }
}
