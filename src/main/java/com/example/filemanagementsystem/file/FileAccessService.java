package com.example.filemanagementsystem.file;

import java.util.List;
import java.util.UUID;

public interface FileAccessService {
    FileInfo create(String actor, FileRequests.Create request);

    List<FileInfo> list(String actor, String search, UUID folderId);

    FileInfo.Details read(String actor, UUID id);

    FileInfo rename(String actor, UUID id, FileRequests.Rename request);

    void delete(String actor, UUID id);

    void share(String actor, UUID id, FileRequests.Share request);

    FileInfo move(String actor, UUID id, FileRequests.Move request);
}
