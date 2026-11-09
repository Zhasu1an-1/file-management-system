package com.example.filemanagementsystem.file;

import com.example.filemanagementsystem.model.FileMetadata;
import com.example.filemanagementsystem.model.FileShare;
import com.example.filemanagementsystem.repository.FileRepository;
import com.example.filemanagementsystem.repository.FileShareRepository;
import com.example.filemanagementsystem.repository.FolderRepository;
import com.example.filemanagementsystem.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class RealFileService implements FileAccessService {
    private final FileManager manager;
    private final FileRepository files;
    private final FileShareRepository shares;
    private final FolderRepository folders;
    private final UserRepository users;

    RealFileService(FileManager manager, FileRepository files, FileShareRepository shares,
                    FolderRepository folders, UserRepository users) {
        this.manager = manager;
        this.files = files;
        this.shares = shares;
        this.folders = folders;
        this.users = users;
    }

    @Override
    public FileInfo create(String actor, FileRequests.Create request) {
        byte[] bytes = request.content().getBytes(StandardCharsets.UTF_8);
        var file = files.saveAndFlush(new FileMetadata(request.name(), request.fileType().toLowerCase(Locale.ROOT),
                bytes.length, users.getReferenceById(actor), request.storageType()));
        manager.save(file, bytes);
        return FileInfo.from(file);
    }

    @Override
    public List<FileInfo> list(String actor, String search, UUID folderId) {
        return files.findVisible(actor, search, folderId == null, folderId).stream().map(FileInfo::from).toList();
    }

    @Override
    public FileInfo.Details read(String actor, UUID id) {
        var file = files.getReferenceById(id);
        return new FileInfo.Details(FileInfo.from(file), new String(manager.read(file), StandardCharsets.UTF_8));
    }

    @Override
    public FileInfo rename(String actor, UUID id, FileRequests.Rename request) {
        var file = files.getReferenceById(id);
        file.rename(request.name());
        files.flush();
        return FileInfo.from(file);
    }

    @Override
    public void delete(String actor, UUID id) {
        var file = files.getReferenceById(id);
        manager.delete(file);
        files.delete(file);
    }

    @Override
    public void share(String actor, UUID id, FileRequests.Share request) {
        if (!shares.existsByFileIdAndViewerUsername(id, request.viewer())) {
            shares.save(new FileShare(files.getReferenceById(id), users.getReferenceById(request.viewer())));
        }
    }

    @Override
    public FileInfo move(String actor, UUID id, FileRequests.Move request) {
        var file = files.getReferenceById(id);
        var folder = request.folderId() == null ? null : folders.getReferenceById(request.folderId());
        file.moveTo(folder);
        return FileInfo.from(file);
    }
}
