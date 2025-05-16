package org.example.capstone.recipe.dto;

import lombok.Data;

@Data
public class SubstituteIngredientRequest {
    private String originalIngredient;
    private String substituteIngredient;
    private String recipeName;
    private Long recipeId;
    private String sessionId;
}