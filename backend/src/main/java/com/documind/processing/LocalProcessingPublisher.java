package com.documind.processing;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Development mode publisher: the job is executed by an in-process worker once
 * the enqueueing transaction has committed. No frontend timer is involved.
 */
@Component
@ConditionalOnProperty(name = "documind.processing.mode", havingValue = "local", matchIfMissing = true)
public class LocalProcessingPublisher implements ProcessingPublisher {

    private final TaskExecutor executor;
    private final ProcessingWorker worker;

    public LocalProcessingPublisher(@Qualifier("processingExecutor") TaskExecutor executor,
            ProcessingWorker worker) {
        this.executor = executor;
        this.worker = worker;
    }

    @Override
    public void publish(ProcessingMessage message) {
        Runnable task = () -> worker.process(message.jobId());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(task);
                }
            });
        } else {
            executor.execute(task);
        }
    }
}
