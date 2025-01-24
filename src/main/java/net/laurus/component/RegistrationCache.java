package net.laurus.component;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import net.laurus.network.IPv4Address;

/**
 * Manages client registration status using caching.
 */
@Component
@Slf4j
public class RegistrationCache {

    /**
     * Checks if the client is registered.
     * 
     * @param clientIp the unique identifier for the client
     * @return true if the client is registered; false otherwise
     */
    @Cacheable(value = "clientRegistrationCache", key = "#clientIp.toString()")
    public boolean isClientRegistered(IPv4Address clientIp) {
        log.debug("Cache miss for client: {}. Assuming not registered.", clientIp.toString());
        return false; // Default return when the key is not in the cache
    }

    /**
     * Marks the client as registered.
     * 
     * @param clientIp the unique identifier for the client
     * @return true to indicate successful registration
     */
    @CachePut(value = "clientRegistrationCache", key = "#clientIp.toString()")
    public boolean registerClient(IPv4Address clientIp) {
        log.info("Registering client: {}", clientIp.toString());
        return true; // Store registration status in the cache
    }
}
