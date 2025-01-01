package net.laurus.component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.laurus.config.IpmiNetworkConfig;
import net.laurus.data.Bitmap;
import net.laurus.data.dto.ipmi.ilo.IloRegistrationRequest;
import net.laurus.exception.NetworkConfigurationException;
import net.laurus.network.IPv4Address;
import net.laurus.network.Subnet;
import net.laurus.network.SubnetMask;
import net.laurus.service.RedisClient;
import net.laurus.thread.LaurusThreadFactory;
import net.laurus.util.NetworkUtil;

/**
 * IloClientFinder is responsible for generating a list of IP addresses within a
 * given subnet.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IloClientFinder {

	private static final List<Integer> NON_ILO_ACTIVE_ADDRESSES = new ArrayList<>();

	private final IpmiNetworkConfig config;
	private final IloMessageQueueHandler queue;
	private final RedisClient redisClient;

	private IPv4Address baseAddress;
	private SubnetMask subnetMaskAddress;
	private Subnet subnet;
	private Bitmap activeClients;

	private List<IPv4Address> cachedAddresses;
	private ExecutorService threadPool;
	private final List<Integer> blacklistedDeviceHashes = new ArrayList<>();
	private final ReentrantLock cacheLock = new ReentrantLock();

	/**
	 * Initializes the necessary fields based on the provided network configuration.
	 * Throws an exception if the provided IP addresses or subnet masks are invalid.
	 */
	@PostConstruct
	private void build() throws NetworkConfigurationException {
		try {
			baseAddress = new IPv4Address(config.getBaseIp());
			subnetMaskAddress = new SubnetMask(config.getSubnetMask());
			subnet = new Subnet(baseAddress, subnetMaskAddress);
			threadPool = Executors.newFixedThreadPool(subnetMaskAddress.toPrefixLength(),
					new LaurusThreadFactory("Cthulu", true));
			log.info("Subnet initialized with base IP: {} and subnet mask: {}", baseAddress, subnetMaskAddress);
		} catch (IllegalArgumentException e) {
			log.error("Invalid IP address or subnet mask", e);
			throw new NetworkConfigurationException("Invalid network configuration provided.", e);
		}
	}

	/**
	 * Returns the cached list of IP addresses if already generated; otherwise,
	 * generates and caches them.
	 *
	 * @return A list of IPv4 addresses within the subnet.
	 */
	@Scheduled(initialDelay = 1000)
	public List<IPv4Address> generateAddresses() {
		// Use a lock to avoid concurrent generation issues
		log.info("cacheLock: Enabled");
		cacheLock.lock();
		try {
			if (cachedAddresses == null) {
				log.info("Generating and caching addresses for subnet.");
				cachedAddresses = generateAddressRange();
			}
		} finally {
			cacheLock.unlock();
			log.info("cacheLock: Disabled");
		}
		return cachedAddresses;
	}

	/**
	 * Generates all IPv4 addresses within the defined subnet range. Optimized to
	 * generate addresses only once.
	 *
	 * @return A list of IPv4 addresses within the subnet.
	 */
	private List<IPv4Address> generateAddressRange() {
		List<IPv4Address> addresses = new ArrayList<>();

		int networkStart = subnet.calculateNetworkStart();
		int networkEnd = subnet.calculateNetworkEnd();

		// Log the calculated network range
		log.info("Generating addresses from {} to {}", IPv4Address.fromInteger(networkStart),
				IPv4Address.fromInteger(networkEnd));

		// Generate all IP addresses within the subnet
		for (int i = networkStart; i <= networkEnd; i++) {
			addresses.add(IPv4Address.fromInteger(i));
		}

		return addresses;
	}

	/**
	 * Example of how you might check if a specific address is within the subnet.
	 *
	 * @param address The IPv4 address to check.
	 * @return True if the address is within the subnet, otherwise false.
	 */
	public boolean isAddressInSubnet(IPv4Address address) {
		return subnet.containsAddress(address);
	}

	@Scheduled(fixedRate = 1000 * 60 * 10, initialDelay = 1000 * 5)
	private void scanNetworkForClients() {
		if (cachedAddresses == null) {
			return;
		}

		log.info("Scanning for active clients.");
		activeClients = NetworkUtil.mapClientsToBitmap(cachedAddresses, IloClientFinder::isIloClient, subnetMaskAddress,
				threadPool, blacklistedDeviceHashes);

		for (int index : activeClients.getActiveIndexes()) {
			IPv4Address ip = cachedAddresses.get(index);
			if (ip == null) {
				continue;
			}

			String redisValue = redisClient.getStringValue(ip.getAddress());
			if (!"true".equals(redisValue)) { // Safely compare against "true"
				log.info("Registering new Ilo client at index {}", index);
				redisClient.setStringValue(ip.getAddress(), "true");
				redisClient.setStringValue(ip.getAddress() + "-health", "5");
				queue.processNewClientRequest(
						new IloRegistrationRequest(ip, "Discovery-" + ip.getAddress().replace(".", "")));
			}
		}
	}


	@Scheduled(fixedRate = 1000 * 60)
	private void heartbeatClients() {
		if (cachedAddresses != null && activeClients != null) {
			if (cachedAddresses.isEmpty() || activeClients.countSetBits() <= 0) {
				return;
			}
			for (int i : activeClients.getActiveIndexes()) {
				var ip = cachedAddresses.get(i);
				if (ip != null) {
					if (redisClient.keyExists(ip.getAddress() + "-health")) {
						if (isIloClient(ip)) {
							boolean beat = incrementOrCapAtFive(ip);
							log.info("Did heartbeat increment? " + beat);
						} else {
							boolean beat = decrementOrZero(ip);
							log.info("Did heartbeat decrement? " + beat);
						}
						boolean clientAlive = isIloClientAlive(ip);
						redisClient.setBooleanValue(ip.getAddress(), clientAlive);
					}
				}
			}
		}
	}

	private boolean incrementOrCapAtFive(IPv4Address ip) {
		String key = ip.getAddress() + "-health";
		long valueForIp = redisClient.getKeyValue(key);
		if (valueForIp < 5) {
			redisClient.incrementKey(key);
			return true;
		}
		return false;
	}

	private boolean decrementOrZero(IPv4Address ip) {
		String key = ip.getAddress() + "-health";
		long valueForIp = redisClient.getKeyValue(key);
		if (valueForIp > 0) {
			redisClient.decrementKey(key);
			return true;
		}
		return false;
	}

	private boolean isIloClientAlive(IPv4Address ip) {
		long valueForIp = redisClient.getKeyValue(ip.getAddress() + "-health");
		return valueForIp > 0;
	}

	public static boolean isIloClient(IPv4Address ip) {
		try {
			if (NON_ILO_ACTIVE_ADDRESSES.contains(ip.hashCode()) || !NetworkUtil.ping(ip, 5000)) {
				return false;
			}
			String xmlData = NetworkUtil.fetchDataFromEndpoint("https://" + ip.toString() + "/xmldata?item=all");
			boolean client = xmlData != null && xmlData.length() > 10;
			if ("Bad".equals(xmlData)) {
				client = false;
				log.info("Blacklisting " + ip.getAddress() + " as it's active, but not an Ilo client.");
				NON_ILO_ACTIVE_ADDRESSES.add(ip.hashCode());
			}
			if (client && !xmlData.startsWith("<RIMP>")) {
				client = false;
				log.info("Blacklisting " + ip.getAddress()
						+ " as it's active, but not an Ilo client. Investigate:\n		" + xmlData);
				NON_ILO_ACTIVE_ADDRESSES.add(ip.hashCode());
			}
			return client;
		} catch (Exception e) {
		}
		return false;
	}

	/**
	 * Clears the cached addresses to force regeneration.
	 */
	public void clearCache() {
		cacheLock.lock();
		try {
			cachedAddresses = null;
			log.info("Cache cleared.");
		} finally {
			cacheLock.unlock();
		}
	}
}
