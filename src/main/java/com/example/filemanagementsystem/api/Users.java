package com.example.filemanagementsystem.api;

import com.example.filemanagementsystem.model.Role;
import com.example.filemanagementsystem.model.User;
import com.example.filemanagementsystem.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

@Component
public class Users {
    public static final String HEADER = "X-User";
    private final UserRepository users;

    public Users(UserRepository users) {
        this.users = users;
    }

    public User require(String username) {
        return users.findById(username)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Choose a known user."));
    }

    public static void requireOwnerRole(User user) {
        if (user.getRole() != Role.OWNER) {
            throw new ResponseStatusException(FORBIDDEN, "This action requires the Owner role.");
        }
    }

    public List<UserInfo> list() {
        return users.findAll(Sort.by("username")).stream()
                .map(user -> new UserInfo(user.getUsername(), user.getDisplayName(), user.getRole()))
                .toList();
    }

    public record UserInfo(String username, String displayName, Role role) {
    }
}
