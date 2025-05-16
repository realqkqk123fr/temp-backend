package org.example.capstone.user.register.dto;


import lombok.Getter;

@Getter
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private int age;
    private int height;
    private int weight;
    private String habit;
    private String preference;
}
