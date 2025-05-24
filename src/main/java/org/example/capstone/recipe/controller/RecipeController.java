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
     * 대체 재료 요청 API (개선된 버전)
     */
    @PostMapping("/api/recipe/substitute")
    public ResponseEntity<?> substituteIngredient(
            @RequestBody SubstituteIngredientRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            // 입력값 검증 및 로깅
            log.info("대체 재료 요청 처리 - 사용자: {}, 원재료: '{}', 대체재료: '{}', 레시피: '{}'",
                    userDetails != null ? userDetails.getUsername() : "인증되지 않음",
                    request.getOriginalIngredient(),
                    request.getSubstituteIngredient(),
                    request.getRecipeName());

            // 필수 필드 검증
            if (request.getOriginalIngredient() == null || request.getOriginalIngredient().trim().isEmpty()) {
                log.warn("원재료가 비어있음");
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "원재료를 입력해주세요.",
                                "substituteFailure", true
                        )
                );
            }

            if (request.getSubstituteIngredient() == null || request.getSubstituteIngredient().trim().isEmpty()) {
                log.warn("대체재료가 비어있음");
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "대체재료를 입력해주세요.",
                                "substituteFailure", true
                        )
                );
            }

            if (request.getRecipeName() == null || request.getRecipeName().trim().isEmpty()) {
                log.warn("레시피명이 비어있음");
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "레시피명이 필요합니다.",
                                "substituteFailure", true
                        )
                );
            }

            // 같은 재료인지 확인
            if (request.getOriginalIngredient().trim().equalsIgnoreCase(request.getSubstituteIngredient().trim())) {
                log.warn("동일한 재료로 대체 시도: {}", request.getOriginalIngredient());
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "success", false,
                                "message", "같은 재료로는 대체할 수 없습니다.",
                                "substituteFailure", true
                        )
                );
            }

            // Flask 서버에 대체 재료 요청
            RecipeGenerateResponse response = recipeService.substituteIngredient(request).block();

            if (response != null) {
                // 대체 불가능한 경우 검사
                boolean isSubstituteFailure =
                        response.isSubstituteFailure() || // 명시적 실패 플래그
                                (response.getDescription() != null && (
                                        response.getDescription().contains("적절하지 않") ||
                                                response.getDescription().contains("생성할 수 없") ||
                                                response.getDescription().contains("대체할 수 없") ||
                                                response.getDescription().contains("불가능") ||
                                                response.getDescription().contains("적절하지 않아"))) ||
                                (response.getIngredients() == null || response.getIngredients().isEmpty()) ||
                                (response.getInstructions() == null || response.getInstructions().isEmpty()) ||
                                (response.getName() == null || response.getName().trim().isEmpty());

                if (isSubstituteFailure) {
                    // 대체 실패 시 명확한 오류 응답 반환
                    String errorMessage = response.getDescription() != null ?
                            response.getDescription() :
                            String.format("%s를 %s로 대체할 수 없습니다.",
                                    request.getOriginalIngredient(),
                                    request.getSubstituteIngredient());

                    log.info("대체 재료 실패: {}", errorMessage);

                    return ResponseEntity.ok().body(
                            Map.of(
                                    "success", false,
                                    "message", errorMessage,
                                    "description", errorMessage,
                                    "substituteFailure", true,
                                    "originalIngredient", request.getOriginalIngredient(),
                                    "substituteIngredient", request.getSubstituteIngredient()
                            )
                    );
                }

                // 성공한 경우 WebSocket 알림 전송
                try {
                    sendSubstituteRecipeNotification(
                            userDetails.getUsername(),
                            request.getOriginalIngredient(),
                            request.getSubstituteIngredient(),
                            response.getName()
                    );
                } catch (Exception e) {
                    log.warn("WebSocket 알림 전송 실패 (무시됨): {}", e.getMessage());
                }

                // 성공 응답에 추가 정보 포함
                response.setSubstituteFailure(false);
                log.info("대체 재료 성공: {} -> {}, 새 레시피: {}",
                        request.getOriginalIngredient(),
                        request.getSubstituteIngredient(),
                        response.getName());

                return ResponseEntity.ok(response);
            } else {
                log.error("Flask 서버로부터 null 응답 수신");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(
                                "success", false,
                                "message", "서버에서 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요.",
                                "substituteFailure", true
                        ));
            }
        } catch (Exception e) {
            log.error("대체 재료 요청 중 오류 발생: {}", e.getMessage(), e);

            // 예외 타입에 따른 구체적인 오류 메시지
            String errorMessage;
            if (e instanceof java.util.concurrent.TimeoutException) {
                errorMessage = "요청 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.";
            } else if (e instanceof java.net.ConnectException) {
                errorMessage = "AI 서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.";
            } else if (e.getMessage() != null && e.getMessage().contains("404")) {
                errorMessage = "요청한 리소스를 찾을 수 없습니다.";
            } else {
                errorMessage = "대체 재료 처리 중 오류가 발생했습니다. 다시 시도해주세요.";
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", errorMessage,
                            "error", e.getMessage() != null ? e.getMessage() : "알 수 없는 오류",
                            "substituteFailure", true
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