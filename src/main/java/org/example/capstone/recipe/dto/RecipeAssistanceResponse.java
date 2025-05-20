// RecipeAssistanceResponse.java
package org.example.capstone.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeAssistanceResponse {
    private Long id;
    private String name;
    private String description;
    private List<InstructionDTO> instructions;
    private List<IngredientDTO> ingredients;
    private int totalCookingTime;   // 총 조리 시간 (분)
    private int totalCookingTimeSeconds; // 총 조리 시간 (초)
    private String difficulty;      // 난이도
    private String servings;        // 인분 수
}