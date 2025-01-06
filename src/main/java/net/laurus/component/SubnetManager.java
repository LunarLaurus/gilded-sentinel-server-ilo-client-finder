package net.laurus.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.laurus.config.SystemConfig;
import net.laurus.network.IPv4Address;
import net.laurus.network.Subnet;
import net.laurus.network.SubnetMask;

/**
 * Manages subnet-related operations for iLO devices.
 * <p>
 * Responsibilities include:
 * <ul>
 * <li>Initializing the subnet based on the provided configuration.</li>
 * <li>Generating a range of IPv4 addresses within the subnet.</li>
 * <li>Checking whether an address belongs to the subnet.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Getter
public class SubnetManager {

	private final SystemConfig config;
	private IPv4Address baseAddress;
	private SubnetMask subnetMask;
	private Subnet subnet;

	/**
	 * Initializes the subnet based on the network configuration provided in
	 * {@link SystemConfig}.
	 * <p>
	 * This method validates the base IP address and subnet mask, then calculates
	 * the subnet.
	 *
	 * @throws RuntimeException If the provided IP address or subnet mask is
	 *                          invalid.
	 */
	@PostConstruct
	private void initializeSubnet() {
		try {
			baseAddress = new IPv4Address(config.getIlo().getNetwork().getBaseIp());
			subnetMask = new SubnetMask(config.getIlo().getNetwork().getSubnetMask());
			subnet = new Subnet(baseAddress, subnetMask);
			log.info("Initialized SubnetManager: base IP={}, subnet mask={}", baseAddress, subnetMask);
		} catch (IllegalArgumentException e) {
			throw new RuntimeException("Invalid network configuration: " + e.getMessage(), e);
		}
	}

	/**
	 * Generates all IPv4 addresses within the defined subnet range.
	 * <p>
	 * This method calculates the start and end of the subnet range and generates a
	 * list of all valid IPv4 addresses within the range.
	 *
	 * @return An immutable list of {@link IPv4Address} objects representing the
	 *         entire subnet range.
	 */
	public List<IPv4Address> generateAddressRange() {
		int start = subnet.calculateNetworkStart();
		int end = subnet.calculateNetworkEnd();
		List<IPv4Address> addresses = new ArrayList<>(end - start + 1);

		for (int i = start; i <= end; i++) {
			addresses.add(IPv4Address.fromInteger(i));
		}

		return Collections.unmodifiableList(addresses);
	}

	/**
	 * Checks whether a given IPv4 address belongs to the defined subnet.
	 *
	 * @param address The {@link IPv4Address} to check.
	 * @return {@code true} if the address is within the subnet, {@code false}
	 *         otherwise.
	 */
	public boolean isAddressInSubnet(IPv4Address address) {
		return subnet.containsAddress(address);
	}
}
