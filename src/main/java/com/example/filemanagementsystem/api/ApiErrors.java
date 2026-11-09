package com.example.filemanagementsystem.api;

import java.io.UncheckedIOException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Message> status(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(new Message(error.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Message> invalidFields(MethodArgumentNotValidException error) {
        String detail = error.getFieldErrors().stream()
                .map(field -> field.getField() + " " + field.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(". "));
        return ResponseEntity.badRequest().body(new Message(detail));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Message> invalidBody() {
        return ResponseEntity.badRequest().body(new Message("Check the JSON fields and their values."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Message> invalidParameter(MethodArgumentTypeMismatchException error) {
        return ResponseEntity.badRequest().body(new Message("Invalid value for " + error.getName() + "."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Message> conflict() {
        return ResponseEntity.status(CONFLICT)
                .body(new Message("That name is already in use or the change conflicts with an existing record."));
    }

    @ExceptionHandler(UncheckedIOException.class)
    ResponseEntity<Message> storageError(UncheckedIOException error) {
        log.error("Local file storage failed", error);
        return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new Message(error.getMessage() + "."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Message> unexpected(Exception error) {
        if (error instanceof ErrorResponse response && response.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                    .body(new Message(response.getBody().getDetail()));
        }
        log.error("Request failed", error);
        return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new Message("The server could not complete the request."));
    }

    public record Message(String message) {
    }
}
