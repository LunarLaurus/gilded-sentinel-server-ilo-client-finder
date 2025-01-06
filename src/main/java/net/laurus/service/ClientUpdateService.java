package net.laurus.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.laurus.component.RegistrationCache;
import net.laurus.ilo.AuthenticatedIloClient;
import net.laurus.ilo.UnauthenticatedIloClient;
import net.laurus.queue.SendClientUpdatesQueueHandler;

@Service
@RequiredArgsConstructor
public class ClientUpdateService {

	private static final int TASK_INTERVAL_UNAUTH_MS = 10000;
	private static final int TASK_INTERVAL_AUTH_MS = 1000;

	private final ExecutorService updateExecutor = Executors.newWorkStealingPool();
	private final Map<String, UnauthenticatedIloClient> unauthenticatedClients = new ConcurrentHashMap<>();
	private final Map<String, AuthenticatedIloClient> authenticatedClients = new ConcurrentHashMap<>();

	private final ClientHeartbeatService heartbeat;
	private final RegistrationCache registrationHandler;
	private final SendClientUpdatesQueueHandler clientQueue;

	public void addUnauthenticatedClient(UnauthenticatedIloClient client) {
		unauthenticatedClients.put(client.getIloUuid(), client);
		clientQueue.sendUnauthenticatedIloClientData(client);
	}

	public void addAuthenticatedClient(AuthenticatedIloClient client) {
		authenticatedClients.put(client.getIloUuid(), client);
		clientQueue.sendAuthenticatedIloClientData(client);
	}

	@Scheduled(fixedDelay = TASK_INTERVAL_UNAUTH_MS)
	public void updateUnauthenticatedClients() {
		unauthenticatedClients.values().forEach(client -> updateExecutor.execute(() -> {
			if (registrationHandler.isClientRegistered(client.getIloAddress())
					&& client.canUpdate()) {
				client.update();
				heartbeat.updateClientTimestamp(client.getIloAddress(), client.getLastUpdateTime());
				clientQueue.sendUnauthenticatedIloClientData(client);
			}
		}));
	}

	@Scheduled(fixedDelay = TASK_INTERVAL_AUTH_MS)
	public void updateAuthenticatedClients() {
		authenticatedClients.values().forEach(client -> updateExecutor.execute(() -> {
			if (registrationHandler.isClientRegistered(client.getIloAddress())
					&& client.canUpdate()) {
				client.update();
				heartbeat.updateClientTimestamp(client.getIloAddress(), client.getLastUpdateTime());
				clientQueue.sendAuthenticatedIloClientData(client);
			}
		}));
	}
	
}
