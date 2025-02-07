package net.laurus.queue;

import static net.laurus.spring.service.RabbitQueueService.DEFAULT_QUEUE_CONFIG;
import static net.laurus.util.RabbitMqUtils.processPayload;

import java.io.IOException;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.laurus.component.RegistrationCache;
import net.laurus.data.dto.ipmi.ilo.IloRegistrationRequest;
import net.laurus.ilo.AuthenticatedIloClient;
import net.laurus.ilo.UnauthenticatedEndpoint;
import net.laurus.ilo.UnauthenticatedIloClient;
import net.laurus.network.IPv4Address;
import net.laurus.service.ClientHeartbeatService;
import net.laurus.service.ClientUpdateService;
import net.laurus.spring.service.IloAuthService;
import net.laurus.spring.service.RabbitQueueService;
import net.laurus.util.NetworkUtil;

@Service
@Slf4j
@RequiredArgsConstructor
public class NewClientRegistrationQueueHandler {

	@Getter
	private final RegistrationCache registrationHandler;
	private final ClientHeartbeatService heartbeat;
	private final ClientUpdateService iloClientService;
	private final IloAuthService iloAuthService;
	private final RabbitQueueService rabbitQueueService;

	public static final String QUEUE_NAME_NEW_CLIENT_REQUEST = "newClientRequestQueue";
	
	@PostConstruct
	public void setupQueues() {
		if (!rabbitQueueService.doesQueueExist(QUEUE_NAME_NEW_CLIENT_REQUEST)){
			rabbitQueueService.createQueue(QUEUE_NAME_NEW_CLIENT_REQUEST, DEFAULT_QUEUE_CONFIG);
		}
	}

	/**
	 * Sends an object to a RabbitMQ queue.
	 *
	 * @param clientObject The object to send.
	 */
	public void putNewRegistrationRequestOnQueue(IloRegistrationRequest clientObject) {
		rabbitQueueService.sendMessage(QUEUE_NAME_NEW_CLIENT_REQUEST, clientObject, true);
	}

	@RabbitListener(queues = QUEUE_NAME_NEW_CLIENT_REQUEST)
	private void listenToNewClientRequestQueue(byte[] registrationRequest) {
		try {
			processNewClientRequest(processPayload(registrationRequest, IloRegistrationRequest.class));
		} catch (IOException e) {
			log.error("Error processing request on newClientRequestQueue: {}", e.getMessage());
		}
	}

	public void processNewClientRequest(IloRegistrationRequest clientObject) {
		IPv4Address cacheKey = clientObject.getIloAddress();
		if (!registrationHandler.isClientRegistered(cacheKey)) {
			try {
				log.info("Processing new client registration: {}", cacheKey);

				if (!NetworkUtil.ping(cacheKey, 5000)) {
					log.info("Client timed out after 5 seconds.");
				} else {
					registrationHandler.registerClient(cacheKey);
					heartbeat.updateClientTimestamp(cacheKey, System.currentTimeMillis());
					UnauthenticatedIloClient unauthClient = UnauthenticatedEndpoint
							.getIloClient(clientObject.getIloAddress());
					if (unauthClient != null) {
						iloClientService.addUnauthenticatedClient(unauthClient);
						AuthenticatedIloClient authClient = AuthenticatedIloClient
								.from(iloAuthService.getDefaultIloUser(), clientObject.getIloAddress(), unauthClient);
						if (authClient != null) {
							iloClientService.addAuthenticatedClient(authClient);
						}
					}
				}
			} catch (Exception e) {
				log.error("Error registering client {}: {}", clientObject, e.getMessage());
				e.printStackTrace();
			}
		} else {
			log.warn("Client with key {} is already registered. Ignoring request.", cacheKey);
			return;
		}
	}

}
