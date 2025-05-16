package org.example.capstone.nutrition.controller;


import lombok.RequiredArgsConstructor;
import org.example.capstone.nutrition.dto.NutritionDTO;
import org.example.capstone.nutrition.service.NutritionService;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class NutritionController {

    private final NutritionService nutritionService;

    @GetMapping("/api/recipe/{recipeId}/nutrition")
    public ResponseEntity<NutritionDTO> getNutrition(@PathVariable Long recipeId,
                                                     @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(nutritionService.fetchAndSaveNutritionFromFlask(recipeId, userDetails));
    }
}
