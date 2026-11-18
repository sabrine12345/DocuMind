package com.documind.document.dto;

import java.io.InputStream;

/**
 * Internal streaming payload. Never serialised to JSON.
 */
public record StoredDocument(InputStream stream, long size, String contentType, String fileName) {
}
