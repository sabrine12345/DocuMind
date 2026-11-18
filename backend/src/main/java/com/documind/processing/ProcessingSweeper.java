package com.documind.processing;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recovery mechanism: any job still QUEUED after the grace period is
 * re-published. This makes the pipeline resilient to broker restarts and to a
 * lost publish, without relying on the frontend.
 */
@Component
public class ProcessingSweeper {

    private static final Logger log = LoggerFactory.getLogger(ProcessingSweeper.class);

    private final ProcessingJobRepository jobs;
    private final ProcessingPublisher publisher;
    private final ProcessingProperties properties;

    public ProcessingSweeper(ProcessingJobRepository jobs, ProcessingPublisher publisher,
            ProcessingProperties properties) {
        this.jobs = jobs;
        this.publisher = publisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${documind.processing.sweep-interval-ms:5000}", initialDelayString = "4000")
    public void republishStaleJobs() {
        if (!properties.isSweepEnabled()) {
            return;
        }
        Instant threshold = Instant.now().minusSeconds(Math.max(properties.getStaleAfterSeconds(), 2));
        List<ProcessingJobEntity> stale = jobs
                .findTop20ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(ProcessingJobStatus.QUEUED, threshold);
        for (ProcessingJobEntity job : stale) {
            log.debug("Re-publishing stale processing job {}", job.getId());
            publisher.publish(new ProcessingMessage(job.getId(), job.getWorkspaceId(), job.getDocumentId(),
                    job.getAttempt()));
        }
    }
}
