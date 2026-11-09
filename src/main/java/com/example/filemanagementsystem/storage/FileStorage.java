package com.example.filemanagementsystem.storage;

import java.util.UUID;

public interface FileStorage {
    StorageType type();

    void save(UUID id, byte[] content);

    byte[] read(UUID id);

    void delete(UUID id);
}
