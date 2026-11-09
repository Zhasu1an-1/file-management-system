package com.example.filemanagementsystem.file;

import com.example.filemanagementsystem.api.Names;
import com.example.filemanagementsystem.api.Users;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileAccessService files;

    public FileController(FileAccessService files) {
        this.files = files;
    }

    @PostMapping
    @ResponseStatus(CREATED)
    public FileInfo create(@RequestHeader(Users.HEADER) String actor, @Valid @RequestBody FileRequests.Create request) {
        return files.create(actor, request);
    }

    @GetMapping
    public List<FileInfo> list(@RequestHeader(Users.HEADER) String actor,
                               @RequestParam(defaultValue = "") @Size(max = Names.MAX_LENGTH) String search,
                               @RequestParam(required = false) UUID folderId) {
        return files.list(actor, search.strip(), folderId);
    }

    @GetMapping("/{id}")
    public FileInfo.Details read(@RequestHeader(Users.HEADER) String actor, @PathVariable UUID id) {
        return files.read(actor, id);
    }

    @PatchMapping("/{id}/name")
    public FileInfo rename(@RequestHeader(Users.HEADER) String actor, @PathVariable UUID id,
                           @Valid @RequestBody FileRequests.Rename request) {
        return files.rename(actor, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void delete(@RequestHeader(Users.HEADER) String actor, @PathVariable UUID id) {
        files.delete(actor, id);
    }

    @PostMapping("/{id}/shares")
    @ResponseStatus(NO_CONTENT)
    public void share(@RequestHeader(Users.HEADER) String actor, @PathVariable UUID id,
                      @Valid @RequestBody FileRequests.Share request) {
        files.share(actor, id, request);
    }

    @PutMapping("/{id}/folder")
    public FileInfo move(@RequestHeader(Users.HEADER) String actor, @PathVariable UUID id,
                         @RequestBody FileRequests.Move request) {
        return files.move(actor, id, request);
    }
}
