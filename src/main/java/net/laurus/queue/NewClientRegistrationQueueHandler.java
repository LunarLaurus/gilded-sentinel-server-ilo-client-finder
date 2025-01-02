package net.laurus.queue;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.laurus.component.RegistrationCache;
import net.laurus.data.dto.ipmi.ilo.IloRegistrationRequest;
import net.laurus.ilo.AuthenticatedIloClient;
import net.laurus.ilo.UnauthenticatedEndpoint;
import net.laurus.ilo.UnauthenticatedIloClient;
import net.laurus.network.IPv4Address;
import net.laurus.service.AuthService;
import net.laurus.service.ClientUpdateService;
import net.laurus.util.NetworkUtil;

@Service
@Slf4j
@RequiredArgsConstructor
public class NewClientRegistrationQueueHandler {

	@Getter
	private final RegistrationCache registrationHandler;
	private final ClientUpdateService iloClientService;
	private final AuthService iloAuthService;
    private final RabbitTemplate rabbitQueue;
	
	public void putNewRegistrationRequestOnQueue(IloRegistrationRequest clientObject) {
        rabbitQueue.convertAndSend("newClientRequestQueue", clientObject);		
	}

	@RabbitListener(queues = "newClientRequestQueue")
	private void listenToNewClientRequestQueue(IloRegistrationRequest clientObject) {
		processNewClientRequest(clientObject);
	}

	public void processNewClientRequest(IloRegistrationRequest clientObject) {
		IPv4Address cacheKey = clientObject.getIloAddress();
		if (!registrationHandler.isClientRegistered(cacheKey)) {
			try {
				log.info("Processing new client registration: {}", cacheKey);

				if (!NetworkUtil.ping(cacheKey, 5000)) {
					log.info("Client timed out after 5 seconds.");
				} else {
					UnauthenticatedIloClient unauthClient = UnauthenticatedEndpoint
							.getIloClient(clientObject.getIloAddress());
					if (unauthClient != null) {
						iloClientService.addUnauthenticatedClient(unauthClient);
					}

					AuthenticatedIloClient authClient = AuthenticatedIloClient.from(iloAuthService.getDefaultIloUser(),
							clientObject.getIloAddress(), unauthClient);
					if (authClient != null) {
						iloClientService.addAuthenticatedClient(authClient);
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
