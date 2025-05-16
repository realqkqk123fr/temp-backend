// RecipeGenerateController.java
package org.example.capstone.recipe.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.global.exception.CustomException;
import org.example.capstone.global.exception.ErrorCode;
import org.example.capstone.nutrition.dto.NutritionDTO;
import org.example.capstone.recipe.domain.Ingredient;
import org.example.capstone.recipe.domain.Instruction;
import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.recipe.dto.RecipeGenerateRequest;
import org.example.capstone.recipe.dto.RecipeGenerateResponse;
import org.example.capstone.recipe.dto.SubstituteIngredientRequest;
import org.example.capstone.recipe.repository.RecipeRepository;
import org.example.capstone.recipe.service.FlaskRecipeService;
import org.example.capstone.recipe.service.RecipeService;
import org.example.capstone.user.domain.User;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.example.capstone.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class RecipeGenerateController {

    private final FlaskRecipeService flaskRecipeService;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate; // WebSocket으로 메시지 전송용

    /**
     * 이미지 분석 및 레시피 생성 API
     */
    @PostMapping("/api/recipe/generate")
    public ResponseEntity<?> generateRecipe(
            @RequestParam("image") MultipartFile image,
            @RequestParam("instructions") String instructions,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            log.info("레시피 생성 요청 처리 - 사용자: {}", userDetails.getUsername());

            // 세션 ID 생성
            String sessionId = UUID.randomUUID().toString();

            // 요청 객체 생성
            RecipeGenerateRequest request = new RecipeGenerateRequest();
            request.setImage(image);
            request.setInstructions(instructions);
            request.setUsername(userDetails.getUsername());
            request.setSessionId(sessionId);

            // Flask 서버에 요청 전송
            RecipeGenerateResponse flaskResponse = flaskRecipeService.generateRecipeFromImage(request);

            // 레시피 저장
            Recipe savedRecipe = saveRecipe(flaskResponse, userDetails);

            // 저장된 레시피 ID 설정
            flaskResponse.setId(savedRecipe.getId());

            // 레시피 생성 알림 메시지를 WebSocket으로 전송
            sendRecipeNotification(userDetails.getUsername(), savedRecipe.getName());

            return ResponseEntity.ok(flaskResponse);
        } catch (Exception e) {
            log.error("레시피 생성 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "레시피 생성 중 오류가 발생했습니다.", "details", e.getMessage()));
        }
    }

    /**
     * 대체 재료 요청 API
     */
    @PostMapping("/api/recipe/substitute")
    public ResponseEntity<RecipeGenerateResponse> substituteIngredient(
            @RequestBody SubstituteIngredientRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            log.info("대체 재료 요청 처리 - 사용자: {}, 원재료: {}, 대체재료: {}",
                    userDetails.getUsername(), request.getOriginalIngredient(), request.getSubstituteIngredient());

            // Flask 서버에 대체 재료 요청
            RecipeGenerateResponse response = flaskRecipeService.substituteIngredient(request).block();

            if (response != null) {
                // 레시피 저장
                Recipe savedRecipe = saveRecipe(response, userDetails);
                response.setId(savedRecipe.getId());

                // 대체 레시피 생성 알림 메시지를 WebSocket으로 전송
                sendSubstituteRecipeNotification(
                        userDetails.getUsername(),
                        request.getOriginalIngredient(),
                        request.getSubstituteIngredient(),
                        savedRecipe.getName()
                );
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("대체 재료 요청 중 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 레시피 저장 메서드
     */
    private Recipe saveRecipe(RecipeGenerateResponse recipeResponse, CustomUserDetails userDetails) {
        // 사용자 조회
        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 레시피 엔티티 생성
        Recipe recipe = Recipe.builder()
                .name(recipeResponse.getName())
                .description(recipeResponse.getDescription())
                .user(user)
                .build();

        // 재료 엔티티 생성
        List<Ingredient> ingredients = new ArrayList<>();
        if (recipeResponse.getIngredients() != null) {
            for (org.example.capstone.recipe.dto.IngredientDTO dto : recipeResponse.getIngredients()) {
                Ingredient ingredient = Ingredient.builder()
                        .name(dto.getName())
                        .recipe(recipe)
                        .build();
                ingredients.add(ingredient);
            }
        }
        recipe.setIngredients(ingredients);

        // 조리 단계 엔티티 생성
        List<Instruction> instructions = new ArrayList<>();
        if (recipeResponse.getInstructions() != null) {
            for (org.example.capstone.recipe.dto.InstructionDTO dto : recipeResponse.getInstructions()) {
                Instruction instruction = Instruction.builder()
                        .instruction(dto.getInstruction())
                        .cookingTime(dto.getCookingTime())
                        .recipe(recipe)
                        .build();
                instructions.add(instruction);
            }
        }
        recipe.setInstructions(instructions);

        // 레시피 저장
        return recipeRepository.save(recipe);
    }

    /**
     * 레시피 생성 알림을 WebSocket으로 전송
     */
    private void sendRecipeNotification(String username, String recipeName) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "recipe_generated");
        notification.put("message", "새로운 레시피가 생성되었습니다: " + recipeName);
        notification.put("username", "시스템");

        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/messages",
                notification
        );
    }

    /**
     * 대체 레시피 생성 알림을 WebSocket으로 전송
     */
    private void sendSubstituteRecipeNotification(String username, String original, String substitute, String recipeName) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "recipe_substituted");
        notification.put("message", original + "를 " + substitute + "로 대체한 레시피가 생성되었습니다: " + recipeName);
        notification.put("username", "시스템");

        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/messages",
                notification
        );
    }
}