package org.example.capstone.nutrition.repository;

import org.example.capstone.nutrition.domain.Nutrition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutritionRepository extends JpaRepository<Nutrition, Long> {
}
