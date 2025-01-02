package net.laurus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

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
        }
    }
}
