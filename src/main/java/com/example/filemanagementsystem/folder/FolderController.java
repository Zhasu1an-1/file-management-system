package com.example.filemanagementsystem.folder;

import com.example.filemanagementsystem.api.Names;
import com.example.filemanagementsystem.api.Users;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/folders")
public class FolderController {
    private final FolderService folders;

    public FolderController(FolderService folders) {
        this.folders = folders;
    }

    @GetMapping
    public List<FolderService.FolderInfo> list(@RequestHeader(Users.HEADER) String actor) {
        return folders.list(actor);
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public FolderService.FolderInfo create(@RequestHeader(Users.HEADER) String actor,
                                           @Valid @RequestBody Create request) {
        return folders.create(actor, request.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void delete(@RequestHeader(Users.HEADER) String actor, @PathVariable UUID id) {
        folders.delete(actor, id);
    }

    public record Create(
            @NotBlank @Size(max = Names.MAX_LENGTH) @Pattern(regexp = Names.PATTERN, message = Names.MESSAGE)
            String name) {
    }
}
