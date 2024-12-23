package net.laurus.component;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.laurus.data.dto.ipmi.ilo.IloRegistrationRequest;
import net.laurus.ilo.AuthenticatedIloClient;
import net.laurus.ilo.UnauthenticatedIloClient;
import net.laurus.service.RedisClient;

@Service
@Slf4j
public class IloMessageQueueHandler {

	@Lazy
    private final RabbitTemplate rabbitTemplate;
	@Lazy
    private final IloMappingService iloMappingService;
	@Lazy
    private final RedisClient redisClient;

    @Autowired
    public IloMessageQueueHandler(RabbitTemplate rabbitTemplate, IloMappingService iloMappingService, RedisClient redisClient) {
        this.rabbitTemplate = rabbitTemplate;
        this.iloMappingService = iloMappingService;
        this.redisClient = redisClient;
    }

    public void sendUnauthenticatedIloClientData(UnauthenticatedIloClient clientObject) {
        rabbitTemplate.convertAndSend("unauthenticatedIloClientQueue", clientObject);
    }

    public void sendAuthenticatedIloClientData(AuthenticatedIloClient clientObject) {
        rabbitTemplate.convertAndSend("authenticatedIloClientQueue", clientObject);
    }

    @RabbitListener(queues = "newClientRequestQueue")
    public void processNewClientRequest(IloRegistrationRequest clientObject) {
        log.info("Received request to map new client: " + clientObject);        
        String clientKey = clientObject.getIloAddress().getAddress();
        if (redisClient.keyExists(clientKey) && (redisClient.getBooleanValue(clientKey) == false)) {
            iloMappingService.mapClient(clientObject);
        }
        else {
        	log.warn("Ilo Client "+ clientKey + " is marked active in Redis, ignoring.");	
        }        
    }
}
