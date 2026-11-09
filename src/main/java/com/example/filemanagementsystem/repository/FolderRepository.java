package com.example.filemanagementsystem.repository;

import com.example.filemanagementsystem.model.Folder;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface FolderRepository extends JpaRepository<Folder, UUID> {
    @Query("""
        select f from Folder f where (f.owner.username = :username and f.owner.role = OWNER) or exists (
            select s.id from FileShare s where s.file.folder = f and s.viewer.username = :username
                and s.viewer.role = VIEWER
        )
        order by lower(f.name), f.id
        """)
    List<Folder> findVisible(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Folder f where f.id = :id")
    Optional<Folder> findForUpdate(UUID id);
}
