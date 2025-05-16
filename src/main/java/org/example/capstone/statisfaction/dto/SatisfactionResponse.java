package org.example.capstone.statisfaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.user.domain.User;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionResponse {     //플라스크로 보내는 만족도

    private User user;
    private Recipe recipe;
    private int rate;
    private String comment;

}
