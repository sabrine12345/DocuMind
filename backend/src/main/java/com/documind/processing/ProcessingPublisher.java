package com.documind.processing;

import java.util.UUID;

/**
 * Publishes a job to the processing pipeline. Implementations are selected by
 * {@code documind.processing.mode}.
 */
public interface ProcessingPublisher {
    void publish(ProcessingMessage message);
}
