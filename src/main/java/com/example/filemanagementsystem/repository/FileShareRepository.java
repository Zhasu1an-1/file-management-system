package com.example.filemanagementsystem.repository;

import com.example.filemanagementsystem.model.FileShare;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileShareRepository extends JpaRepository<FileShare, Long> {
    boolean existsByFileIdAndViewerUsername(UUID fileId, String username);
}
