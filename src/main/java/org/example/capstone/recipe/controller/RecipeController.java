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
    @PostMapping("/api/recipe/substitute")
    public ResponseEntity<RecipeGenerateResponse> substituteIngredient(
            @RequestBody SubstituteIngredientRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            log.info("대체 재료 요청 처리 - 사용자: {}, 원재료: {}, 대체재료: {}",
                    userDetails.getUsername(), request.getOriginalIngredient(), request.getSubstituteIngredient());

            // Flask 서버에 대체 재료 요청
            RecipeGenerateResponse response = recipeService.substituteIngredient(request).block();

            if (response != null) {
                // 대체 레시피 생성 알림 메시지를 WebSocket으로 전송
                sendSubstituteRecipeNotification(
                        userDetails.getUsername(),
                        request.getOriginalIngredient(),
                        request.getSubstituteIngredient(),
                        response.getName()
                );
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("대체 재료 요청 중 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
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