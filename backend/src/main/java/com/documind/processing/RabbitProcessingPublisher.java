package com.documind.processing;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * RabbitMQ publisher used by the docker/production profiles.
 */
@Component
@ConditionalOnProperty(name = "documind.processing.mode", havingValue = "rabbit")
public class RabbitProcessingPublisher implements ProcessingPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitProcessingPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(ProcessingMessage message) {
        Runnable send = () -> rabbitTemplate.convertAndSend(
                RabbitProcessingConfig.EXCHANGE, RabbitProcessingConfig.ROUTING_KEY, message);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
