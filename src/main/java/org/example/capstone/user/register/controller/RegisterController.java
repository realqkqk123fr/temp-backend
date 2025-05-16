package org.example.capstone.user.register.controller;


import lombok.RequiredArgsConstructor;
import org.example.capstone.user.register.dto.RegisterRequest;
import org.example.capstone.user.register.dto.RegisterResponse;
import org.example.capstone.user.register.service.RegisterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RegisterController {

    private final RegisterService registerService;

    @PostMapping("/api/auth/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request){
        return ResponseEntity.ok(registerService.register(request));
    }
}
