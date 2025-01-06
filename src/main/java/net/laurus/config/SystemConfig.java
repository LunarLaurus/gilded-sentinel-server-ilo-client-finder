package net.laurus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import net.laurus.network.IPv4Address;

@Configuration
@ConfigurationProperties(prefix = "system")
@Getter
@Setter
public class SystemConfig {

    private boolean obfuscateSecrets;
    private String allowedIp;
    private IloConfig ilo;

    @Getter
    @Setter
    public static class IloConfig {
    	
        private String username;
        private String password;
        private int clientTimeoutConnect;
        private int clientTimeoutRead;
        private NetworkConfig network;

        @Getter
        @Setter
        public static class NetworkConfig {
            private String baseIp;
            private String subnetMask;

            private IPv4Address baseAddress;

            @PostConstruct
            public void setupBaseIp() {
            	baseAddress = new IPv4Address(getBaseIp());
            }
        }
    }
}
