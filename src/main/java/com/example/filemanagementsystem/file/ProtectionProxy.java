package com.example.filemanagementsystem.file;

import com.example.filemanagementsystem.api.Users;
import com.example.filemanagementsystem.model.FileMetadata;
import com.example.filemanagementsystem.model.Role;
import com.example.filemanagementsystem.repository.FileRepository;
import com.example.filemanagementsystem.repository.FileShareRepository;
import com.example.filemanagementsystem.repository.FolderRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

@Primary
@Service
@Transactional
public class ProtectionProxy implements FileAccessService {
    private final RealFileService real;
    private final Users users;
    private final FileRepository files;
    private final FileShareRepository shares;
    private final FolderRepository folders;

    ProtectionProxy(RealFileService real, Users users, FileRepository files, FileShareRepository shares,
                    FolderRepository folders) {
        this.real = real;
        this.users = users;
        this.files = files;
        this.shares = shares;
        this.folders = folders;
    }

    @Override
    public FileInfo create(String actor, FileRequests.Create request) {
        Users.requireOwnerRole(users.require(actor));
        return real.create(actor, request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FileInfo> list(String actor, String search, UUID folderId) {
        users.require(actor);
        return real.list(actor, search, folderId);
    }

    @Override
    public FileInfo.Details read(String actor, UUID id) {
        var user = users.require(actor);
        var file = files.findForRead(id).orElseThrow(ProtectionProxy::missingFile);
        boolean ownsFile = user.getRole() == Role.OWNER && file.getOwner().getUsername().equals(actor);
        boolean sharedViewer = user.getRole() == Role.VIEWER && shares.existsByFileIdAndViewerUsername(id, actor);
        if (!ownsFile && !sharedViewer) {
            throw new ResponseStatusException(FORBIDDEN, "This file has not been shared with you.");
        }
        return real.read(actor, id);
    }

    @Override
    public FileInfo rename(String actor, UUID id, FileRequests.Rename request) {
        requireFileOwner(actor, id);
        return real.rename(actor, id, request);
    }

    @Override
    public void delete(String actor, UUID id) {
        requireFileOwner(actor, id);
        real.delete(actor, id);
    }

    @Override
    public void share(String actor, UUID id, FileRequests.Share request) {
        requireFileOwner(actor, id);
        if (users.require(request.viewer()).getRole() != Role.VIEWER) {
            throw new ResponseStatusException(BAD_REQUEST, "Choose a Viewer to share with.");
        }
        real.share(actor, id, request);
    }

    @Override
    public FileInfo move(String actor, UUID id, FileRequests.Move request) {
        requireFileOwner(actor, id);
        if (request.folderId() != null) {
            var folder = folders.findForUpdate(request.folderId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Folder not found."));
            if (!folder.getOwner().getUsername().equals(actor)) {
                throw new ResponseStatusException(FORBIDDEN, "You can only move files into your own folders.");
            }
        }
        return real.move(actor, id, request);
    }

    private void requireFileOwner(String actor, UUID id) {
        Users.requireOwnerRole(users.require(actor));
        FileMetadata file = files.findForUpdate(id).orElseThrow(ProtectionProxy::missingFile);
        if (!file.getOwner().getUsername().equals(actor)) {
            throw new ResponseStatusException(FORBIDDEN, "Only the file owner can perform this action.");
        }
    }

    private static ResponseStatusException missingFile() {
        return new ResponseStatusException(NOT_FOUND, "File not found.");
    }
}
