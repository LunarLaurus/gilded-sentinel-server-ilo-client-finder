package net.laurus.component;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.laurus.config.SecretsConfig;
import net.laurus.config.SystemConfig;
import net.laurus.ilo.AbstractIloAuthService;
import net.laurus.network.IloUser;

@Getter
@Component
@RequiredArgsConstructor
public class IloAuthService extends AbstractIloAuthService {

	private final SecretsConfig secretsConfig;

	private final SystemConfig systemConfig;

	private IloUser defaultIloUser;

	@Override
	public boolean isObfuscated() {
		return secretsConfig.obfuscate();
	}

	@Override
	@PostConstruct
	protected void postConstruct() {
		defaultIloUser = new IloUser(getConfigSuppliedIloUsername(), getConfigSuppliedIloPassword(), isObfuscated());
		this.getIloAuthDataMap().put(getConfigSuppliedIloUsername(), defaultIloUser);
	}

	@Override
	protected String getConfigSuppliedIloUsername() {
		return systemConfig.getIloUsername();
	}

	@Override
	protected String getConfigSuppliedIloPassword() {
		return systemConfig.getIloPassword();
	}

}
