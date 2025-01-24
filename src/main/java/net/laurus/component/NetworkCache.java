package net.laurus.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.laurus.data.Bitmap;
import net.laurus.network.IPv4Address;

/**
 * Handles caching of network-related data for iLO clients.
 * <p>
 * Responsibilities include:
 * <ul>
 *   <li>Maintaining a cache of IPv4 addresses for iLO clients.</li>
 *   <li>Providing thread-safe access to cached addresses.</li>
 *   <li>Managing a blacklist of addresses to ignore during operations.</li>
 * </ul>
 */
@Component
@Slf4j
public class NetworkCache {

    private final ReentrantLock cacheLock = new ReentrantLock();
    private List<IPv4Address> cachedAddresses;
    @Getter
    private final List<IPv4Address> blacklist = Collections.synchronizedList(new ArrayList<>());
    @Getter
    @Setter
    private Bitmap activeClients = new Bitmap(256);

    /**
     * Retrieves the cached list of IPv4 addresses.
     * <p>
     * This method ensures thread-safe access to the cached addresses.
     * If no addresses are cached, an empty list is returned.
     *
     * @return A list of {@link IPv4Address} objects representing cached addresses,
     *         or an empty list if no addresses are cached.
     */
    public List<IPv4Address> getCachedAddresses() {
        cacheLock.lock();
        try {
            return cachedAddresses == null ? Collections.emptyList() : cachedAddresses;
        } finally {
            cacheLock.unlock();
        }
    }

    /**
     * Updates the cached list of IPv4 addresses.
     * <p>
     * This method replaces the current cached addresses with the provided list
     * in a thread-safe manner.
     *
     * @param addresses A list of {@link IPv4Address} objects to be cached.
     */
    public void setCachedAddresses(List<IPv4Address> addresses) {
        cacheLock.lock();
        try {
            cachedAddresses = Collections.unmodifiableList(new ArrayList<>(addresses));
            log.info("Cached {} addresses.", addresses.size());
        } finally {
            cacheLock.unlock();
        }
    }

    /**
     * Clears the cached IPv4 addresses.
     * <p>
     * This forces regeneration of the cache when the cached addresses are next requested.
     */
    public void clearCache() {
        cacheLock.lock();
        try {
            cachedAddresses = null;
            log.info("Cleared address cache.");
        } finally {
            cacheLock.unlock();
        }
    }

    /**
     * Checks if a given IPv4 address is blacklisted.
     * <p>
     * Blacklisted addresses are ignored during network scans or other operations.
     *
     * @param address The {@link IPv4Address} to check.
     * @return {@code true} if the address is blacklisted, {@code false} otherwise.
     */
    public boolean isBlacklisted(IPv4Address address) {
        return blacklist.contains(address);
    }

    /**
     * Adds an IPv4 address to the blacklist.
     * <p>
     * Blacklisted addresses are ignored during network scans or other operations.
     *
     * @param address The {@link IPv4Address} to add to the blacklist.
     */
    public void addToBlacklist(IPv4Address address) {
        blacklist.add(address);
        log.info("Blacklisted address: {}", address.toString());
    }
}
