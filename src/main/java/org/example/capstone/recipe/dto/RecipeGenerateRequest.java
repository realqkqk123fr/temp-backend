package org.example.capstone.recipe.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class RecipeGenerateRequest {
    private MultipartFile image;
    private String instructions;
    private String username;
    private String sessionId;
}
