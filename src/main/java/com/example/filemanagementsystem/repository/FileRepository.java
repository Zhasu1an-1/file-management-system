package com.example.filemanagementsystem.repository;

import com.example.filemanagementsystem.model.FileMetadata;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface FileRepository extends JpaRepository<FileMetadata, UUID> {
    @Query("""
        select f from FileMetadata f
        where ((f.owner.username = :username and f.owner.role = OWNER) or exists (
            select s.id from FileShare s where s.file = f and s.viewer.username = :username
                and s.viewer.role = VIEWER
        ))
        and (:allFolders = true or f.folder.id = :folderId)
        and locate(lower(:search), lower(f.name)) > 0
        order by lower(f.name), f.id
        """)
    List<FileMetadata> findVisible(String username, String search, boolean allFolders, UUID folderId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select f from FileMetadata f where f.id = :id")
    Optional<FileMetadata> findForRead(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FileMetadata f where f.id = :id")
    Optional<FileMetadata> findForUpdate(UUID id);

    boolean existsByFolderId(UUID folderId);
}
