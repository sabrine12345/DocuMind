package com.documind.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Development friendly filesystem storage. Keys are relative and confined to
 * the configured root directory.
 */
@Service
@ConditionalOnProperty(name = "documind.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path root;

    public LocalStorageService(StorageProperties properties) {
        this.root = Paths.get(properties.getLocation()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new StorageException("Unable to create the local storage directory.", ex);
        }
        log.info("Local document storage initialised at {}", root);
    }

    @Override
    public void put(String key, InputStream stream, long size, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new StorageException("Unable to store the document.", ex);
        }
    }

    @Override
    public StoredObject get(String key) {
        Path target = resolve(key);
        try {
            if (!Files.exists(target)) {
                throw new StorageException("The stored document is no longer available.");
            }
            return new StoredObject(Files.newInputStream(target), Files.size(target));
        } catch (IOException ex) {
            throw new StorageException("Unable to read the document.", ex);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            throw new StorageException("Unable to delete the stored document.", ex);
        }
    }

    @Override
    public StorageProvider provider() {
        return StorageProvider.LOCAL;
    }

    @Override
    public String bucket() {
        return root.toString();
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new StorageException("Invalid storage key.");
        }
        return target;
    }
}
