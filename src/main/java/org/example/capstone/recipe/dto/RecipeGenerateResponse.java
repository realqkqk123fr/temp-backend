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
    private Long userId;        // 소유자 ID 필드 추가
    private boolean substituteFailure; // 추가: 대체 실패 여부

    // 추가: 대체 재료 관련 정보
    private SubstitutionInfo substitutionInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubstitutionInfo {
        private String originalIngredient;
        private String substituteIngredient;
        private Double similarityScore;
        private String estimatedAmount;
        private String substitutionReason;
        private List<String> cookingTips;
    }
}
