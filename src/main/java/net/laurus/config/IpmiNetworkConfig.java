package net.laurus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "system.network")
@Getter
@Setter
public class IpmiNetworkConfig {

    private String baseIp;
    private String subnetMask;
}
