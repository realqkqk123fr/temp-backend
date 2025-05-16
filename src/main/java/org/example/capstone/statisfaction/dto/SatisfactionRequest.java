package org.example.capstone.statisfaction.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionRequest {      //사용자가 입력한 만족도

    private int rate;
    private String comment;


}
