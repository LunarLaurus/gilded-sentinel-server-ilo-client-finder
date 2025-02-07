package net.laurus.queue;

import static net.laurus.spring.service.RabbitQueueService.DEFAULT_QUEUE_CONFIG;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.laurus.ilo.AuthenticatedIloClient;
import net.laurus.ilo.UnauthenticatedIloClient;
import net.laurus.spring.service.RabbitQueueService;

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

	private final RabbitQueueService rabbitQueueService;

	public static final String QUEUE_NAME_UNAUTHENTICATED = "unauthenticatedIloClientQueue";
	public static final String QUEUE_NAME_AUTHENTICATED = "authenticatedIloClientQueue";
	
	@PostConstruct
	public void setupQueues() {
		if (!rabbitQueueService.doesQueueExist(QUEUE_NAME_UNAUTHENTICATED)){
			rabbitQueueService.createQueue(QUEUE_NAME_UNAUTHENTICATED, DEFAULT_QUEUE_CONFIG);
		}
		if (!rabbitQueueService.doesQueueExist(QUEUE_NAME_AUTHENTICATED)){
			rabbitQueueService.createQueue(QUEUE_NAME_AUTHENTICATED, DEFAULT_QUEUE_CONFIG);
		}
	}

    /**
     * Sends unauthenticated client data to the queue.
     *
     * @param clientObject The unauthenticated IloClient object to send.
     */
    public void sendUnauthenticatedIloClientData(UnauthenticatedIloClient clientObject) {
        log.info("Updating UnauthenticatedIloClient: {}", clientObject);
        send(QUEUE_NAME_UNAUTHENTICATED, clientObject);
    }

    /**
     * Sends authenticated client data to the queue.
     *
     * @param clientObject The authenticated IloClient object to send.
     */
    public void sendAuthenticatedIloClientData(AuthenticatedIloClient clientObject) {
        log.info("Updating AuthenticatedIloClient: {}", clientObject);
        send(QUEUE_NAME_AUTHENTICATED, clientObject);
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
        rabbitQueueService.sendMessage(queueName, clientObject, true);
		log.debug("Successfully sent data to queue: {}", queueName);
    }
}
