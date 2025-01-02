package net.laurus.component;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.laurus.config.IpmiNetworkConfig;
import net.laurus.network.IPv4Address;
import net.laurus.network.Subnet;
import net.laurus.network.SubnetMask;

/**
 * Manages subnet-related operations for iLO devices.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Initializing the subnet based on configuration.</li>
 *   <li>Generating a range of IPv4 addresses within the subnet.</li>
 *   <li>Checking whether an address belongs to the subnet.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Getter
public class SubnetManager {

    private final IpmiNetworkConfig config;
    private IPv4Address baseAddress;
    private SubnetMask subnetMask;
    private Subnet subnet;

    /**
     * Initializes the subnet fields based on the network configuration provided in {@link IpmiNetworkConfig}.
     * <p>
     * This method is executed automatically after the bean is constructed.
     * It validates the base IP address and subnet mask, then calculates the subnet.
     * 
     * @throws RuntimeException If the provided IP address or subnet mask is invalid.
     */
    @PostConstruct
    private void initializeSubnet() {
        try {
            baseAddress = new IPv4Address(config.getBaseIp());
            subnetMask = new SubnetMask(config.getSubnetMask());
            subnet = new Subnet(baseAddress, subnetMask);
            log.info("Initialized IloSubnetManager: base IP={}, subnet mask={}", baseAddress, subnetMask);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid network configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Generates all IPv4 addresses within the defined subnet range.
     * <p>
     * This method calculates the start and end of the subnet range and generates
     * a list of all valid IPv4 addresses within the range.
     * 
     * @return A list of {@link IPv4Address} objects representing the entire subnet range.
     */
    public List<IPv4Address> generateAddressRange() {
        List<IPv4Address> addresses = new ArrayList<>();
        int start = subnet.calculateNetworkStart();
        int end = subnet.calculateNetworkEnd();
        for (int i = start; i <= end; i++) {
            addresses.add(IPv4Address.fromInteger(i));
        }
        return addresses;
    }

    /**
     * Checks whether a given IPv4 address belongs to the defined subnet.
     * <p>
     * This method verifies if the provided {@link IPv4Address} falls within the subnet range.
     * 
     * @param address The {@link IPv4Address} to check.
     * @return {@code true} if the address is within the subnet, {@code false} otherwise.
     */
    public boolean isAddressInSubnet(IPv4Address address) {
        return subnet.containsAddress(address);
    }
}
