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
public class RecipeGenerateResponse {
    private Long id;
    private String name;
    private String description;
    private List<IngredientDTO> ingredients;
    private List<InstructionDTO> instructions;
    private String imageUrl;
}
