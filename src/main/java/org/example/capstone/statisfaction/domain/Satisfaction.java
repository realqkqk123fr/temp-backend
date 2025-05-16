package org.example.capstone.statisfaction.domain;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.user.domain.User;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Satisfaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int rate;
    private String comment;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToOne
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;
}
