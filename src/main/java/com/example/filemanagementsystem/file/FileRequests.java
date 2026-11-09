package com.example.filemanagementsystem.file;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.filemanagementsystem.api.Names;
import com.example.filemanagementsystem.storage.StorageType;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class FileRequests {
    private FileRequests() {
    }

    public record Create(
            @NotBlank @Size(max = Names.MAX_LENGTH) @Pattern(regexp = Names.PATTERN, message = Names.MESSAGE)
            String name,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{1,16}", message = "must be 1–16 letters or digits")
            String fileType,
            @NotBlank @Size(max = 100_000) String content,
            @NotNull StorageType storageType) {
    }

    public record Rename(
            @NotBlank @Size(max = Names.MAX_LENGTH) @Pattern(regexp = Names.PATTERN, message = Names.MESSAGE)
            String name) {
    }

    public record Share(@NotBlank @Size(max = 32) String viewer) {
    }

    public record Move(@JsonProperty(required = true) UUID folderId) {
    }
}
