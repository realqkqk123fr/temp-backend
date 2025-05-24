package org.example.capstone.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeUpdateResult {
    private Long recipeId;
    private String recipeName;
    private boolean success;
    private String message;
    private LocalDateTime updatedAt;

    // 변경된 재료 정보
    private List<IngredientChange> ingredientChanges;

    // 변경된 조리법 정보
    private List<InstructionChange> instructionChanges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientChange {
        private String originalName;
        private String originalAmount;
        private String newName;
        private String newAmount;
        private String changeReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstructionChange {
        private int stepNumber;
        private String originalInstruction;
        private String newInstruction;
        private List<String> changes; // 구체적인 변경 사항들
    }
}
