package com.example.filemanagementsystem.file;

import com.example.filemanagementsystem.model.FileMetadata;
import com.example.filemanagementsystem.storage.FileStorage;
import com.example.filemanagementsystem.storage.StorageType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class FileManager {
    private final Map<StorageType, FileStorage> storage;

    public FileManager(List<FileStorage> implementations) {
        storage = implementations.stream()
                .collect(Collectors.toUnmodifiableMap(FileStorage::type, Function.identity()));
    }

    public void save(FileMetadata file, byte[] content) {
        storage.get(file.getStorageType()).save(file.getId(), content);
    }

    public byte[] read(FileMetadata file) {
        return storage.get(file.getStorageType()).read(file.getId());
    }

    public void delete(FileMetadata file) {
        storage.get(file.getStorageType()).delete(file.getId());
    }
}
