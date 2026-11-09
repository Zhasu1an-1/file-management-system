package com.example.filemanagementsystem.folder;

import com.example.filemanagementsystem.api.Users;
import com.example.filemanagementsystem.model.Folder;
import com.example.filemanagementsystem.repository.FileRepository;
import com.example.filemanagementsystem.repository.FolderRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

@Service
public class FolderService {
    private final Users users;
    private final FolderRepository folders;
    private final FileRepository files;

    public FolderService(Users users, FolderRepository folders, FileRepository files) {
        this.users = users;
        this.folders = folders;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public List<FolderInfo> list(String actor) {
        users.require(actor);
        return folders.findVisible(actor).stream().map(FolderInfo::from).toList();
    }

    @Transactional
    public FolderInfo create(String actor, String name) {
        var user = users.require(actor);
        Users.requireOwnerRole(user);
        return FolderInfo.from(folders.saveAndFlush(new Folder(name, user)));
    }

    @Transactional
    public void delete(String actor, UUID id) {
        Users.requireOwnerRole(users.require(actor));
        var folder = folders.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Folder not found."));
        if (!folder.getOwner().getUsername().equals(actor)) {
            throw new ResponseStatusException(FORBIDDEN, "Only the folder owner can delete it.");
        }
        if (files.existsByFolderId(id)) {
            throw new ResponseStatusException(CONFLICT, "Move or delete the folder's files before deleting it.");
        }
        folders.delete(folder);
    }

    public record FolderInfo(UUID id, String name) {
        static FolderInfo from(Folder folder) {
            return new FolderInfo(folder.getId(), folder.getName());
        }
    }
}
