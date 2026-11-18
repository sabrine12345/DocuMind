package com.documind.storage;

/**
 * Active storage backend. LOCAL is available for development, MINIO mirrors
 * the production S3-compatible setup.
 */
public enum StorageProvider {
    LOCAL,
    MINIO
}
