package com.example.filemanagementsystem.file;

import com.example.filemanagementsystem.model.FileMetadata;
import com.example.filemanagementsystem.storage.StorageType;
import java.time.Instant;
import java.util.UUID;

public record FileInfo(UUID id, String name, String fileType, int sizeBytes, Instant createdAt,
                       String owner, UUID folderId, StorageType storageType) {
    static FileInfo from(FileMetadata file) {
        UUID folderId = file.getFolder() == null ? null : file.getFolder().getId();
        return new FileInfo(file.getId(), file.getName(), file.getFileType(), file.getSizeBytes(),
                file.getCreatedAt(), file.getOwner().getUsername(), folderId, file.getStorageType());
    }

    public record Details(FileInfo file, String content) {
    }
}
