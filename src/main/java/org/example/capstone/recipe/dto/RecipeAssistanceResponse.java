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
    // 필요한 추가 필드 (총 조리시간, 난이도 등)
}