package org.example.capstone.user.repository;

import org.example.capstone.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findById(Long id);
    User findByUsername(String username);
    User findByEmail(String email);
    boolean existsByEmail(String email);
}
