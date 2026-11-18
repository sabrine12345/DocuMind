package com.documind.extraction;

/**
 * Lifecycle of an extracted field.
 *
 * PENDING   - prepared from a template, no value yet.
 * EXTRACTED - value produced by the automated extraction pipeline (future AI service).
 * EDITED    - value entered or corrected by a human.
 * VALIDATED - value explicitly confirmed by a validator.
 */
public enum ExtractionFieldStatus {
    PENDING,
    EXTRACTED,
    EDITED,
    VALIDATED
}
