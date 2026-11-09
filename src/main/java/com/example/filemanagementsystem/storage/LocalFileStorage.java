package com.example.filemanagementsystem.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class LocalFileStorage implements FileStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private final Path directory;

    public LocalFileStorage(@Value("${app.storage.directory}") Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override
    public StorageType type() {
        return StorageType.LOCAL;
    }

    @Override
    public void save(UUID id, byte[] content) {
        Path path = directory.resolve(id.toString());
        try {
            Files.createDirectories(directory);
            OutputStream stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            try (stream) {
                stream.write(content);
            } catch (IOException e) {
                remove(path);
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save local file content", e);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    remove(path);
                }
            }
        });
    }

    @Override
    public byte[] read(UUID id) {
        try (InputStream stream = Files.newInputStream(directory.resolve(id.toString()),
                LinkOption.NOFOLLOW_LINKS)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read local file content", e);
        }
    }

    @Override
    public void delete(UUID id) {
        Path path = directory.resolve(id.toString());
        // Keep the content readable if the metadata transaction rolls back.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                remove(path);
            }
        });
    }

    private void remove(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not clean up local content at {}", path, e);
        }
    }
}
