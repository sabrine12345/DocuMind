package com.documind.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes processing messages from RabbitMQ. Kept intentionally thin: all the
 * preparation logic lives in the worker so it can later be replaced by an
 * external service.
 */
@Component
@ConditionalOnProperty(name = "documind.processing.mode", havingValue = "rabbit")
public class RabbitProcessingListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitProcessingListener.class);

    private final ProcessingWorker worker;

    public RabbitProcessingListener(ProcessingWorker worker) {
        this.worker = worker;
    }

    @RabbitListener(queues = RabbitProcessingConfig.QUEUE)
    public void onMessage(ProcessingMessage message) {
        log.info("Received processing message for job {}", message.jobId());
        worker.process(message.jobId());
    }
}
