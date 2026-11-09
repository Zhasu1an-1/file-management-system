package com.example.filemanagementsystem.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {
    private final Users users;

    public UserController(Users users) {
        this.users = users;
    }

    @GetMapping("/api/users")
    public List<Users.UserInfo> list() {
        return users.list();
    }
}
