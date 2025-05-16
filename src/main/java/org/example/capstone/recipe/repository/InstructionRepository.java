package org.example.capstone.recipe.repository;

import org.example.capstone.recipe.domain.Instruction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstructionRepository extends JpaRepository<Instruction, Long> {
}
