package com.documind.document;

/**
 * Lifecycle of a document inside the platform. It is always driven by the
 * backend processing pipeline, never by the frontend.
 */
public enum DocumentStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED
}
