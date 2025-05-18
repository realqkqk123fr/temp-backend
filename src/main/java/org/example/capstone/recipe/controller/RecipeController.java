package org.example.capstone.recipe.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.global.exception.CustomException;
import org.example.capstone.global.exception.ErrorCode;
import org.example.capstone.nutrition.dto.NutritionDTO;
import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.recipe.dto.*;
import org.example.capstone.recipe.service.FlaskRecipeService;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 통합된 레시피 컨트롤러
 * - 기본 레시피 조회/저장 기능
 * - 이미지 기반 레시피 생성 기능
 * - 레시피 영양 정보 조회 기능
 * - 대체 재료 기반 레시피 생성 기능
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class RecipeController {

    private final FlaskRecipeService recipeService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 실시간 어시스턴스 API
     */
    @GetMapping("/api/recipe/{recipeId}/cooking-assistance")
    public ResponseEntity<RecipeAssistanceResponse> getCookingAssistance(
            @PathVariable Long recipeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        // 데이터베이스에서 레시피 조회
        Recipe recipe = recipeService.getRecipeById(recipeId, userDetails);

        // 어시스턴스 응답 생성
        RecipeAssistanceResponse response = recipeService.createAssistanceResponse(recipe);

        return ResponseEntity.ok(response);
    }

    /**
     * 사용자 정보 전송 API
     */
    @PostMapping("/api/chat")
    public void sendInfoToFlask(@AuthenticationPrincipal CustomUserDetails userDetails) {
        recipeService.sendUserInfoToFlask(userDetails);
    }

    // 영양 정보 API는 NutritionController에서 처리하도록 제거

    /**
     * 이미지 분석 및 레시피 생성 API
     */
    @PostMapping("/api/recipe/generate")
    public ResponseEntity<?> generateRecipeFromImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam("instructions") String instructions,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            log.info("레시피 생성 요청 처리 - 사용자: {}", userDetails.getUsername());

            // 사용자 ID 명시적으로 확인 및 로깅
            String username = userDetails.getUsername();
            Long userId = userDetails.getUserId();
            log.info("레시피 생성 요청 처리 - 사용자: {}, ID: {}", username, userId);

            // 세션 ID 생성
            String sessionId = UUID.randomUUID().toString();

            /// 요청 객체 생성 - 현재 로그인한 사용자 정보 추가
            RecipeGenerateRequest request = new RecipeGenerateRequest();
            request.setImage(image);
            request.setInstructions(instructions);
            request.setUsername(userDetails.getUsername());
            request.setSessionId(sessionId);

            // 현재 사용자의 ID도 명시적으로 저장
            request.setUserId(userId);  // 이 필드가 RecipeGenerateRequest에 추가되어야 함

            log.info("요청 객체 설정 완료 - 사용자: {}, ID: {}", request.getUsername(), request.getUserId());

            // Flask 서버에 요청 전송
            RecipeGenerateResponse flaskResponse = recipeService.generateRecipeFromImage(request);

            // 응답 검증 로깅
            log.info("생성된 레시피 정보 - ID: {}, 이름: {}, 소유자 ID: {}",
                    flaskResponse.getId(), flaskResponse.getName(), flaskResponse.getUserId());

            // 레시피 저장은 서비스 내부에서 처리되므로 ID 값을 가져옴
            // 통합된 서비스가 적절히 구현되었다고 가정

            // 레시피 생성 알림 메시지를 WebSocket으로 전송
            sendRecipeNotification(userDetails.getUsername(), flaskResponse.getName());

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
    // src/main/java/org/example/capstone/recipe/controller/RecipeController.java

    @PostMapping("/api/recipe/substitute")
    public ResponseEntity<?> substituteIngredient(
            @RequestBody SubstituteIngredientRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            log.info("대체 재료 요청 처리 - 사용자: {}, 원재료: {}, 대체재료: {}",
                    userDetails.getUsername(), request.getOriginalIngredient(), request.getSubstituteIngredient());

            // Flask 서버에 대체 재료 요청
            RecipeGenerateResponse response = recipeService.substituteIngredient(request).block();

            if (response != null) {
                // 대체 불가능한 경우 검사 - 설명에 "적절하지 않" 또는 "생성할 수 없" 문구가 포함된 경우
                boolean isSubstituteFailure =
                        (response.getDescription() != null && (
                                response.getDescription().contains("적절하지 않") ||
                                        response.getDescription().contains("생성할 수 없"))) ||
                                (response.getIngredients() == null || response.getIngredients().isEmpty()) ||
                                (response.getInstructions() == null || response.getInstructions().isEmpty());

                if (isSubstituteFailure) {
                    // 대체 실패 시 명확한 오류 응답 반환
                    return ResponseEntity.badRequest().body(
                            Map.of(
                                    "success", false,
                                    "message", String.format("%s를 %s로 대체할 수 없습니다: %s",
                                            request.getOriginalIngredient(),
                                            request.getSubstituteIngredient(),
                                            response.getDescription())
                            )
                    );
                }

                // 대체 레시피 생성 알림 메시지를 WebSocket으로 전송
                sendSubstituteRecipeNotification(
                        userDetails.getUsername(),
                        request.getOriginalIngredient(),
                        request.getSubstituteIngredient(),
                        response.getName()
                );

                // 정상 응답
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(
                                "success", false,
                                "message", "대체 재료 처리 중 오류가 발생했습니다."
                        ));
            }
        } catch (Exception e) {
            log.error("대체 재료 요청 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "대체 재료 요청 처리 중 오류가 발생했습니다: " + e.getMessage()
                    ));
        }
    }

    /**
     * 이미지 업로드 API
     */
    @PostMapping("/api/recipe/upload")
    public ResponseEntity<?> uploadRecipeImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam("instructions") String instructions,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            log.info("사용자 {} 이미지 업로드 요청 처리 중", userDetails.getUsername());

            // 위의 generateRecipeFromImage 메서드와 동일한 기능이므로
            // 해당 메서드를 호출하여 중복 코드 제거
            return generateRecipeFromImage(image, instructions, userDetails);
        } catch (Exception e) {
            log.error("이미지 업로드 처리 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "이미지 업로드 처리 중 오류가 발생했습니다.", "details", e.getMessage()));
        }
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
        // 대체 가능 여부에 따라 다른 메시지 전송
        boolean isSuccessful = recipeName != null && !recipeName.isBlank() &&
                !recipeName.contains("적절하지 않") &&
                !recipeName.contains("생성할 수 없");

        String messageText = isSuccessful
                ? original + "를 " + substitute + "로 대체한 레시피가 생성되었습니다: " + recipeName
                : original + "를 " + substitute + "로 대체할 수 없습니다.";

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "recipe_substituted");
        notification.put("message", messageText);
        notification.put("username", "시스템");
        notification.put("success", isSuccessful);

        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/messages",
                notification
        );
    }
}