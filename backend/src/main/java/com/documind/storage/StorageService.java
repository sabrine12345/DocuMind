package com.documind.storage;

import java.io.InputStream;

/**
 * Storage abstraction. Physical paths and object keys never leave the backend:
 * the API only exposes document identifiers.
 */
public interface StorageService {

    void put(String key, InputStream stream, long size, String contentType);

    StoredObject get(String key);

    boolean exists(String key);

    void delete(String key);

    StorageProvider provider();

    String bucket();

    record StoredObject(InputStream stream, long size) {
    }
}
