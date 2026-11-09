package com.example.filemanagementsystem.model;

import com.example.filemanagementsystem.storage.StorageType;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "files")
public class FileMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 16)
    private String fileType;

    @Column(nullable = false)
    private int sizeBytes;

    @Column(nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_username")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    private Folder folder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StorageType storageType;

    protected FileMetadata() {
    }

    public FileMetadata(String name, String fileType, int sizeBytes, User owner, StorageType storageType) {
        this.name = name;
        this.fileType = fileType;
        this.sizeBytes = sizeBytes;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.owner = owner;
        this.storageType = storageType;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFileType() {
        return fileType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public User getOwner() {
        return owner;
    }

    public Folder getFolder() {
        return folder;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void moveTo(Folder folder) {
        this.folder = folder;
    }
}
