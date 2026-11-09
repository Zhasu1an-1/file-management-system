package com.example.filemanagementsystem.storage;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseFileStorage implements FileStorage {
    private final JdbcTemplate jdbc;

    public DatabaseFileStorage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StorageType type() {
        return StorageType.DATABASE;
    }

    @Override
    public void save(UUID id, byte[] content) {
        jdbc.update("INSERT INTO file_contents (file_id, content) VALUES (?, ?)", id, content);
    }

    @Override
    public byte[] read(UUID id) {
        return jdbc.queryForObject("SELECT content FROM file_contents WHERE file_id = ?", byte[].class, id);
    }

    @Override
    public void delete(UUID id) {
        jdbc.update("DELETE FROM file_contents WHERE file_id = ?", id);
    }
}
