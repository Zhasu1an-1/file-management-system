package com.example.filemanagementsystem.model;

import jakarta.persistence.*;

@Entity
@Table(name = "file_shares")
public class FileShare {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private FileMetadata file;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viewer_username")
    private User viewer;

    protected FileShare() {
    }

    public FileShare(FileMetadata file, User viewer) {
        this.file = file;
        this.viewer = viewer;
    }
}
