package org.example.capstone.recipe.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstructionDTO {

    private String instruction;
    private int cookingTime;        // 분 단위
    private int cookingTimeSeconds; // 초 단위 (추가)
    private int stepNumber;         // 단계 번호
}
