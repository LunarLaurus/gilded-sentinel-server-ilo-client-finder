package net.laurus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.experimental.Accessors;

@Configuration
@Getter
public class SecretsConfig {

    @Value("${system.obfuscate-secrets:true}")
    @Accessors(fluent = true)
    private boolean obfuscate;
	
}
