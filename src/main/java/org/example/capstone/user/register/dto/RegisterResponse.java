package org.example.capstone.user.register.dto;

import lombok.Getter;
import org.example.capstone.user.domain.User;

@Getter
public class RegisterResponse {
    private String username;
    private String email;

    public RegisterResponse(User user) {
        this.username = user.getUsername();
        this.email = user.getEmail();
    }
}
