package net.laurus.component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import net.laurus.ilo.AuthenticatedIloClient;
import net.laurus.ilo.UnauthenticatedIloClient;
import net.laurus.network.IPv4Address;

@Service
public class IloClientService {

	private static final int TASK_INTERVAL_IN_MILLIS_UNAUTH = 15000;
	private static final int TASK_INTERVAL_IN_MILLIS_AUTH = 5000;

    private final Map<IPv4Address, Boolean> hasClientBeenMapped = new ConcurrentHashMap<>();
    private final Map<String, UnauthenticatedIloClient> unauthenticatedClientData = new ConcurrentHashMap<>();
    private final Map<String, AuthenticatedIloClient> authenticatedClientData = new ConcurrentHashMap<>();

	@Lazy
    private final RabbitTemplate rabbitTemplate;

    @Autowired
    public IloClientService(@Lazy RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    public void addUnauthenticatedClient(String clientIloUuid, UnauthenticatedIloClient client) {
        unauthenticatedClientData.put(clientIloUuid, client);
    }

    public void addAuthenticatedClient(String clientIloUuid, AuthenticatedIloClient client) {
        authenticatedClientData.put(clientIloUuid, client);
    }

    public boolean hasClientBeenMapped(IPv4Address iloClientIp) {
        return hasClientBeenMapped.getOrDefault(iloClientIp, false);
    }

    public UnauthenticatedIloClient getUnauthenticatedClientData(String clientIloUuid) {
        return unauthenticatedClientData.get(clientIloUuid);
    }

    public AuthenticatedIloClient getAuthenticatedClientData(String clientIloUuid) {
        return authenticatedClientData.get(clientIloUuid);
    }

    public void updateUnauthenticatedClientData(UnauthenticatedIloClient client) {
        if (client.canUpdate()) {
            client.update();
        }
    }

    public void updateAuthenticatedClientData(AuthenticatedIloClient client) {
        client.update();
    }
    
    @Scheduled(fixedDelay = TASK_INTERVAL_IN_MILLIS_UNAUTH)
    public void updateUnauthenticatedClientData() {
        for (UnauthenticatedIloClient client : unauthenticatedClientData.values()) {
            updateUnauthenticatedClientData(client);
            rabbitTemplate.convertAndSend("unauthenticatedIloClientQueue", client);
        }
    }

    @Scheduled(fixedDelay = TASK_INTERVAL_IN_MILLIS_AUTH)
    public void updateAuthenticatedClientData() {
        for (AuthenticatedIloClient client : authenticatedClientData.values()) {
            updateAuthenticatedClientData(client);
            rabbitTemplate.convertAndSend("authenticatedIloClientQueue", client);
        }
    }

	public Collection<UnauthenticatedIloClient> getUnauthenticatedClientData() {
		return unauthenticatedClientData.values();
	}

	public Collection<AuthenticatedIloClient> getAuthenticatedClientData() {
		return authenticatedClientData.values();
	}

    
}

