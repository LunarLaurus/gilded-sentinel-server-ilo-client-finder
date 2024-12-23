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
    private String iloUsername;
    private String iloPassword;
    private String allowedIp;
    
}
