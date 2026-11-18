package com.documind.processing;

import java.util.UUID;

/**
 * Message exchanged with the processing queue. It intentionally references
 * identifiers only so the architecture can later be consumed by an external
 * Python AI worker without sharing JPA entities.
 */
public record ProcessingMessage(UUID jobId, UUID workspaceId, UUID documentId, int attempt) {
}
