package net.laurus.queue;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.laurus.ilo.AuthenticatedIloClient;
import net.laurus.ilo.UnauthenticatedIloClient;

@Component
@Slf4j
@RequiredArgsConstructor
public class SendClientUpdatesQueueHandler {

    private final RabbitTemplate rabbitTemplate;

    public void sendUnauthenticatedIloClientData(UnauthenticatedIloClient clientObject) {
        log.info("Updating UnauthenticatedIloClient: {}", clientObject);
        rabbitTemplate.convertAndSend("unauthenticatedIloClientQueue", clientObject);
    }

    public void sendAuthenticatedIloClientData(AuthenticatedIloClient clientObject) {
        log.info("Updating AuthenticatedIloClient: {}", clientObject);
        rabbitTemplate.convertAndSend("authenticatedIloClientQueue", clientObject);
    }
    
}
