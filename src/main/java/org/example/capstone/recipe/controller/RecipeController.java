package org.example.capstone.recipe.controller;


import lombok.RequiredArgsConstructor;
import org.example.capstone.recipe.dto.RecipeResponse;
import org.example.capstone.recipe.service.RecipeService;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping("/api/recipe/{recipeId}/asistance")
    public ResponseEntity<RecipeResponse> fetchAndSaveRecipeFromFlask(@PathVariable Long recipeId,
                                                       @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(recipeService.fetchAndSaveRecipeFromFlask(recipeId, userDetails));
    }

    @PostMapping("/api/chat")
    public void sendInfoToFlask(@AuthenticationPrincipal CustomUserDetails userDetails){
        recipeService.sendUserInfoToFlask(userDetails);
    }
}
