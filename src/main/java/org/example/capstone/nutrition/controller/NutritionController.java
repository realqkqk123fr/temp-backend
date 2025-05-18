package org.example.capstone.nutrition.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.nutrition.dto.NutritionDTO;
import org.example.capstone.nutrition.service.NutritionService;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class NutritionController {

    private final NutritionService nutritionService;

    /**
     * 영양 정보 조회 API
     * 어떤 오류가 발생해도 사용자에게는 응답 반환
     */
    @GetMapping("/api/recipe/{recipeId}/nutrition")
    public ResponseEntity<NutritionDTO> getNutrition(
            @PathVariable Long recipeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("영양정보 요청 - 레시피 ID: {}, 사용자: {}",
                recipeId, userDetails != null ? userDetails.getUsername() : "인증되지 않음");

        NutritionDTO result;

        try {
            // 정상 경로
            result = nutritionService.getNutritionByRecipeId(recipeId, userDetails);
        } catch (Exception e) {
            // 오류 처리 및 로깅
            log.error("영양정보 요청 처리 중 오류: {}", e.getMessage());

            // 오류 발생해도 기본값 반환
            result = NutritionDTO.builder()
                    .calories(500.0)
                    .carbohydrate(30.0)
                    .protein(25.0)
                    .fat(15.0)
                    .sugar(5.0)
                    .sodium(400.0)
                    .saturatedFat(3.0)
                    .transFat(0.0)
                    .cholesterol(50.0)
                    .build();
        }

        return ResponseEntity.ok(result);
    }
}