package org.example.capstone.recipe.dto;

import lombok.Data;

@Data
public class SubstituteIngredientRequest {
    private String originalIngredient;
    private String substituteIngredient;
    private String recipeName;
    private Long recipeId;
    private String sessionId;

    // 추가: 기존 레시피 정보 포함 여부
    private boolean includeOriginalRecipe = true;

    // 추가: 대체 재료 수량 자동 조정 여부
    private boolean autoAdjustAmount = true;
}