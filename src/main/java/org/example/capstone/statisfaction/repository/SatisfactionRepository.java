package org.example.capstone.statisfaction.repository;

import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.statisfaction.domain.Satisfaction;
import org.example.capstone.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SatisfactionRepository extends JpaRepository<Satisfaction, Long> {

    Satisfaction findByRecipe(Recipe recipe);

    // 추가: 레시피와 사용자로 만족도 찾기
    Satisfaction findByRecipeAndUser(Recipe recipe, User user);
}
