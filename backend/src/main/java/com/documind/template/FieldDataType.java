package com.documind.template;

/**
 * Supported data types for extraction fields. Kept intentionally simple so a
 * future AI extraction service can map its output onto these definitions.
 */
public enum FieldDataType {
    TEXT,
    NUMBER,
    DATE,
    CURRENCY,
    BOOLEAN
}
