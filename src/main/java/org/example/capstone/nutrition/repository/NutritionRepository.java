package org.example.capstone.nutrition.repository;

import org.example.capstone.nutrition.domain.Nutrition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NutritionRepository extends JpaRepository<Nutrition, Long> {

    /**
     * 레시피 ID로 영양 정보 찾기
     * @param recipeId 레시피 ID
     * @return 영양 정보 (있는 경우)
     */
    @Query("SELECT n FROM Nutrition n WHERE n.recipe.id = :recipeId")
    Optional<Nutrition> findByRecipeId(@Param("recipeId") Long recipeId);
}