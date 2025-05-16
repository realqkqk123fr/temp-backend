package org.example.capstone.recipe.repository;

import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    List<Recipe> findByUser(User user);
}
