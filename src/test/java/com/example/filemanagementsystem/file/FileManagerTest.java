package com.example.filemanagementsystem.file;

import com.example.filemanagementsystem.model.FileMetadata;
import com.example.filemanagementsystem.storage.FileStorage;
import com.example.filemanagementsystem.storage.StorageType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FileManagerTest {
    @Test
    void dispatchesAllContentOperationsToTheFilesRecordedStorage() {
        FileStorage local = mock(FileStorage.class);
        FileStorage database = mock(FileStorage.class);
        when(local.type()).thenReturn(StorageType.LOCAL);
        when(database.type()).thenReturn(StorageType.DATABASE);
        var manager = new FileManager(List.of(database, local));

        var first = file(StorageType.LOCAL);
        var second = file(StorageType.DATABASE);
        byte[] firstBytes = {1};
        byte[] secondBytes = {2};
        when(local.read(first.getId())).thenReturn(firstBytes);
        when(database.read(second.getId())).thenReturn(secondBytes);

        manager.save(first, firstBytes);
        manager.save(second, secondBytes);
        assertThat(manager.read(first)).containsExactly(firstBytes);
        assertThat(manager.read(second)).containsExactly(secondBytes);
        manager.delete(first);
        manager.delete(second);

        verify(local).save(first.getId(), firstBytes);
        verify(local).delete(first.getId());
        verify(database).save(second.getId(), secondBytes);
        verify(database).delete(second.getId());
    }

    private FileMetadata file(StorageType type) {
        FileMetadata file = mock(FileMetadata.class);
        when(file.getId()).thenReturn(UUID.randomUUID());
        when(file.getStorageType()).thenReturn(type);
        return file;
    }
}
