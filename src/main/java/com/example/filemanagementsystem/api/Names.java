package com.example.filemanagementsystem.api;

public final class Names {
    public static final int MAX_LENGTH = 120;
    public static final String PATTERN = "[\\p{L}\\p{N}][\\p{L}\\p{N}\\p{M} ._()\\-]*(?<![ .])";
    public static final String MESSAGE = "must start with a letter or digit, use only letters, digits, spaces, "
            + "dots, underscores, parentheses or hyphens, and cannot end with a space or dot";

    private Names() {
    }
}
