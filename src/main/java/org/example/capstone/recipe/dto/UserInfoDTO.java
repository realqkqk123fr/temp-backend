package org.example.capstone.recipe.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.statisfaction.domain.Satisfaction;
import org.example.capstone.user.domain.User;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoDTO {

    private User user;
    private List<Recipe> recipes;
    private List<Satisfaction> satisfactions;
}
