package org.example.capstone.statisfaction.controller;


import lombok.RequiredArgsConstructor;
import org.example.capstone.statisfaction.dto.SatisfactionRequest;
import org.example.capstone.statisfaction.service.SatisfactionService;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
@RequiredArgsConstructor
public class SatisfactionController {

    private final SatisfactionService satisfactionService;

    @PostMapping("/api/recipe/{recipeId}/satisfaction")
    public ResponseEntity<?> saveSatisfaction(@PathVariable Long recipeId,
                                              @RequestBody SatisfactionRequest satisfactionRequest,
                                              @AuthenticationPrincipal CustomUserDetails userDetails){
        satisfactionService.saveSatisafction(recipeId, userDetails, satisfactionRequest);
        return ResponseEntity.ok("만족도가 저장되었습니다.");
    }
}
