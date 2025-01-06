package net.laurus.queue;

import static net.laurus.util.RabbitMqUtils.preparePayload;

import java.io.IOException;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.laurus.ilo.AuthenticatedIloClient;
import net.laurus.ilo.UnauthenticatedIloClient;

/**
 * Handles queue operations for sending updates to client queues.
 * <p>
 * Provides methods to send authenticated and unauthenticated client data
 * to their respective RabbitMQ queues with efficient compression.
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SendClientUpdatesQueueHandler {

    private final RabbitTemplate rabbitQueue;

    /**
     * Sends unauthenticated client data to the queue.
     *
     * @param clientObject The unauthenticated IloClient object to send.
     */
    public void sendUnauthenticatedIloClientData(UnauthenticatedIloClient clientObject) {
        log.info("Updating UnauthenticatedIloClient: {}", clientObject);
        send("unauthenticatedIloClientQueue", clientObject);
    }

    /**
     * Sends authenticated client data to the queue.
     *
     * @param clientObject The authenticated IloClient object to send.
     */
    public void sendAuthenticatedIloClientData(AuthenticatedIloClient clientObject) {
        log.info("Updating AuthenticatedIloClient: {}", clientObject);
        send("authenticatedIloClientQueue", clientObject);
    }

    /**
     * Sends a serialized and compressed object to the specified RabbitMQ queue.
     * <p>
     * Handles serialization, compression, and error logging internally.
     * </p>
     *
     * @param queueName    The name of the queue to send the object to.
     * @param clientObject The object to send, which will be serialized and compressed.
     */
    private void send(String queueName, Object clientObject) {
        try {
            byte[] compressedData = preparePayload(clientObject, true);
            rabbitQueue.convertAndSend(queueName, compressedData);
            log.debug("Successfully sent data to queue: {}", queueName);
        } catch (IOException e) {
            log.error("Error placing request on {}: {}", queueName, e.getMessage(), e);
        }
    }
}
