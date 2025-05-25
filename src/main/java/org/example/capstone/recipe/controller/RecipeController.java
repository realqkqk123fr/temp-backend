package org.example.capstone.recipe.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.global.exception.CustomException;
import org.example.capstone.global.exception.ErrorCode;
import org.example.capstone.nutrition.dto.NutritionDTO;
import org.example.capstone.nutrition.service.NutritionService;
import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.recipe.dto.*;
import org.example.capstone.recipe.service.FlaskRecipeService;
import org.example.capstone.user.domain.User;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.example.capstone.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.util.*;

import static org.example.capstone.global.exception.ErrorCode.USER_NOT_FOUND;

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
    private final NutritionService nutritionService;
    private final UserRepository userRepository;

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

            // 사용자 정보 가져오기
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(USER_NOT_FOUND));

            // 사용자 식습관과 선호도 설정
            request.setUserHabit(user.getHabit());
            request.setUserPreference(user.getPreference());

            log.info("요청 객체 설정 완료 - 사용자: {}, ID: {}, 식습관: {}, 선호도: {}",
                    request.getUsername(), request.getUserId(),
                    request.getUserHabit(), request.getUserPreference());

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
     * 대체 재료 요청 API (LLM 기반 판단 적용)
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
            List<String> missingFields = new ArrayList<>();
            if (request.getOriginalIngredient() == null || request.getOriginalIngredient().trim().isEmpty()) {
                missingFields.add("원재료");
            }
            if (request.getSubstituteIngredient() == null || request.getSubstituteIngredient().trim().isEmpty()) {
                missingFields.add("대체재료");
            }
            if (request.getRecipeName() == null || request.getRecipeName().trim().isEmpty()) {
                missingFields.add("레시피명");
            }

            if (!missingFields.isEmpty()) {
                String errorMessage = String.join(", ", missingFields) + "를 입력해주세요.";
                log.warn("필수 필드 누락: {}", errorMessage);

                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", errorMessage,
                        "substituteFailure", true,
                        "error", "MISSING_REQUIRED_FIELDS"
                ));
            }

            // 같은 재료인지 확인
            if (request.getOriginalIngredient().trim().equalsIgnoreCase(request.getSubstituteIngredient().trim())) {
                log.warn("동일한 재료로 대체 시도: {}", request.getOriginalIngredient());
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "같은 재료로는 대체할 수 없습니다.",
                        "substituteFailure", true,
                        "error", "SAME_INGREDIENT"
                ));
            }

            // Flask 서버에 대체 재료 요청 (LLM 판단)
            RecipeGenerateResponse response = recipeService.substituteIngredient(request).block();

            if (response != null) {
                // Flask 응답에서 대체 실패 여부 확인 (LLM 판단 결과)
                if (response.isSubstituteFailure()) {
                    // 대체 실패 시 명확한 오류 응답 반환
                    String errorMessage = response.getDescription();
                    if (errorMessage == null || errorMessage.isEmpty()) {
                        errorMessage = String.format(
                                "%s를 %s로 대체할 수 없습니다. 자세한 이유는 제공되지 않았습니다.",
                                request.getOriginalIngredient(),
                                request.getSubstituteIngredient()
                        );
                    }

                    log.info("대체 재료 실패(LLM 판단): {}", errorMessage);

                    return ResponseEntity.ok().body(Map.of(
                            "success", false,
                            "message", errorMessage,
                            "description", errorMessage,
                            "substituteFailure", true,
                            "originalIngredient", request.getOriginalIngredient(),
                            "substituteIngredient", request.getSubstituteIngredient(),
                            "error", "SUBSTITUTE_NOT_POSSIBLE_LLM"
                    ));
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
                log.info("대체 재료 성공(LLM 판단): {} -> {}, 새 레시피: {}",
                        request.getOriginalIngredient(),
                        request.getSubstituteIngredient(),
                        response.getName());

                // 성공 응답에 메타데이터 추가
                Map<String, Object> successResponse = new HashMap<>();
                successResponse.put("success", true);
                successResponse.put("id", response.getId());
                successResponse.put("name", response.getName());
                successResponse.put("description", response.getDescription());
                successResponse.put("ingredients", response.getIngredients());
                successResponse.put("instructions", response.getInstructions());
                successResponse.put("userId", response.getUserId());
                successResponse.put("substituteFailure", false);

                // 대체 정보 추가
                if (response.getSubstitutionInfo() != null) {
                    successResponse.put("substitutionInfo", response.getSubstitutionInfo());
                } else {
                    // 기본 대체 정보 생성
                    Map<String, Object> substitutionInfo = new HashMap<>();
                    substitutionInfo.put("original", request.getOriginalIngredient());
                    substitutionInfo.put("substitute", request.getSubstituteIngredient());
                    successResponse.put("substitutionInfo", substitutionInfo);
                }

                return ResponseEntity.ok(successResponse);
            } else {
                log.error("Flask 서버로부터 null 응답 수신");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of(
                                "success", false,
                                "message", "AI 서버에서 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요.",
                                "substituteFailure", true,
                                "error", "NO_RESPONSE_FROM_AI"
                        ));
            }
        } catch (Exception e) {
            log.error("대체 재료 요청 중 오류 발생: {}", e.getMessage(), e);

            // 예외 타입에 따른 구체적인 오류 메시지
            String errorMessage = buildExceptionErrorMessage(e);
            String errorCode = getErrorCodeFromException(e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", errorMessage,
                            "error", errorCode,
                            "substituteFailure", true,
                            "details", e.getMessage() != null ? e.getMessage() : "알 수 없는 오류"
                    ));
        }
    }

    /**
     * 대체 실패 여부 검사 (강화된 로직)
     */
    private boolean checkSubstituteFailure(RecipeGenerateResponse response, SubstituteIngredientRequest request) {
        // 1. 명시적 실패 플래그 확인
        if (response.isSubstituteFailure()) {
            log.debug("명시적 실패 플래그 감지");
            return true;
        }

        // 2. 설명 텍스트에서 실패 키워드 검사
        if (response.getDescription() != null) {
            String description = response.getDescription().toLowerCase();
            String[] failureKeywords = {
                    "적절하지 않", "생성할 수 없", "대체할 수 없", "불가능",
                    "적절하지 않아", "유사도", "레시피를 생성할 수 없습니다"
            };

            for (String keyword : failureKeywords) {
                if (description.contains(keyword)) {
                    log.debug("설명에서 실패 키워드 감지: {}", keyword);
                    return true;
                }
            }
        }

        // 3. 레시피 이름에서 실패 표시 검사
        if (response.getName() != null) {
            String name = response.getName().toLowerCase();
            if (name.contains("적절하지 않") || name.contains("생성할 수 없")) {
                log.debug("레시피 이름에서 실패 표시 감지");
                return true;
            }
        }

        // 4. 필수 데이터 누락 확인
        if (response.getIngredients() == null || response.getIngredients().isEmpty()) {
            log.debug("재료 데이터 누락 감지");
            return true;
        }

        if (response.getInstructions() == null || response.getInstructions().isEmpty()) {
            log.debug("조리법 데이터 누락 감지");
            return true;
        }

        if (response.getName() == null || response.getName().trim().isEmpty()) {
            log.debug("레시피 이름 누락 감지");
            return true;
        }

        // 5. 재료 목록에서 대체 재료 확인
        boolean substituteFound = false;
        if (response.getIngredients() != null) {
            for (IngredientDTO ingredient : response.getIngredients()) {
                if (ingredient.getName() != null &&
                        ingredient.getName().toLowerCase().contains(request.getSubstituteIngredient().toLowerCase())) {
                    substituteFound = true;
                    break;
                }
            }
        }

        if (!substituteFound) {
            log.debug("대체 재료가 최종 재료 목록에 포함되지 않음");
            return true;
        }

        return false;
    }

    /**
     * 오류 메시지 생성
     */
    private String buildErrorMessage(RecipeGenerateResponse response, SubstituteIngredientRequest request) {
        if (response.getDescription() != null && !response.getDescription().trim().isEmpty()) {
            return response.getDescription();
        }

        if (response.getName() != null && response.getName().contains("적절하지 않")) {
            return response.getName();
        }

        // 기본 오류 메시지
        return String.format(
                "%s를 %s로 대체할 수 없습니다. 재료의 특성이 너무 달라 적절한 레시피를 만들 수 없습니다. " +
                        "더 유사한 특성을 가진 재료로 시도해보세요.",
                request.getOriginalIngredient(),
                request.getSubstituteIngredient()
        );
    }

    /**
     * 예외로부터 오류 메시지 생성
     */
    private String buildExceptionErrorMessage(Exception e) {
        if (e instanceof java.util.concurrent.TimeoutException) {
            return "AI 처리 시간이 너무 오래 걸리고 있습니다. 잠시 후 다시 시도해주세요.";
        } else if (e instanceof java.net.ConnectException) {
            return "AI 서버와 연결할 수 없습니다. 네트워크 상태를 확인하고 잠시 후 다시 시도해주세요.";
        } else if (e.getMessage() != null && e.getMessage().contains("404")) {
            return "요청한 리소스를 찾을 수 없습니다. 서버 설정을 확인해주세요.";
        } else if (e.getMessage() != null && e.getMessage().contains("500")) {
            return "AI 서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
        } else if (e.getMessage() != null && e.getMessage().contains("timeout")) {
            return "요청 시간이 초과되었습니다. AI 분석에 시간이 오래 걸리고 있습니다.";
        } else {
            return "대체 재료 처리 중 예상치 못한 오류가 발생했습니다. 다시 시도해주세요.";
        }
    }

    /**
     * 예외로부터 오류 코드 생성
     */
    private String getErrorCodeFromException(Exception e) {
        if (e instanceof java.util.concurrent.TimeoutException) {
            return "TIMEOUT_ERROR";
        } else if (e instanceof java.net.ConnectException) {
            return "CONNECTION_ERROR";
        } else if (e.getMessage() != null && e.getMessage().contains("404")) {
            return "RESOURCE_NOT_FOUND";
        } else if (e.getMessage() != null && e.getMessage().contains("500")) {
            return "AI_SERVER_ERROR";
        } else {
            return "INTERNAL_ERROR";
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
     * 영양 정보 갱신 API
     * RecipeController에 추가할 코드
     */
    @GetMapping("/api/recipe/{recipeId}/refresh-nutrition")
    public ResponseEntity<NutritionDTO> refreshNutrition(
            @PathVariable Long recipeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("영양 정보 갱신 요청 - 레시피 ID: {}, 사용자: {}",
                recipeId, userDetails != null ? userDetails.getUsername() : "인증되지 않음");

        try {
            // nutritionService에 추가된 메서드 호출
            NutritionDTO updatedNutrition = nutritionService.refreshNutritionByRecipeId(recipeId, userDetails);
            return ResponseEntity.ok(updatedNutrition);
        } catch (Exception e) {
            log.error("영양 정보 갱신 처리 중 오류 발생: {}", e.getMessage(), e);

            // 오류 발생 시에도 클라이언트에는 기본 영양 정보 반환 (사용자 경험 유지)
            NutritionDTO defaultNutrition = NutritionDTO.builder()
                    .calories(500.0)
                    .carbohydrate(30.0)
                    .protein(25.0)
                    .fat(15.0)
                    .sugar(5.0)
                    .sodium(400.0)
                    .saturatedFat(3.0)
                    .transFat(0.0)
                    .cholesterol(50.0)
                    .build();

            return ResponseEntity.ok(defaultNutrition);
        }
    }

    /**
     * 레시피 생성 알림을 WebSocket으로 전송
     */
    private void sendRecipeNotification(String username, String recipeName) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "recipe_generated");
            notification.put("message", "새로운 레시피가 생성되었습니다: " + recipeName);
            notification.put("username", "시스템");

            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/messages",
                    notification
            );

            log.debug("레시피 생성 알림 전송 완료: {} -> {}", username, recipeName);
        } catch (Exception e) {
            log.warn("레시피 생성 알림 전송 실패: {}", e.getMessage());
        }
    }

    /**
     * 대체 레시피 생성 알림을 WebSocket으로 전송
     */
    private void sendSubstituteRecipeNotification(String username, String original, String substitute, String recipeName) {
        try {
            // 대체 가능 여부에 따라 다른 메시지 전송
            boolean isSuccessful = recipeName != null && !recipeName.isBlank() &&
                    !recipeName.contains("적절하지 않") &&
                    !recipeName.contains("생성할 수 없");

            String messageText = isSuccessful
                    ? String.format("%s를 %s로 대체한 레시피가 생성되었습니다: %s", original, substitute, recipeName)
                    : String.format("%s를 %s로 대체할 수 없습니다. 다른 재료를 시도해보세요.", original, substitute);

            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "recipe_substituted");
            notification.put("message", messageText);
            notification.put("username", "시스템");
            notification.put("success", isSuccessful);
            notification.put("originalIngredient", original);
            notification.put("substituteIngredient", substitute);

            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/messages",
                    notification
            );

            log.debug("대체 레시피 알림 전송 완료: {} -> {} (성공: {})", original, substitute, isSuccessful);
        } catch (Exception e) {
            log.warn("대체 레시피 알림 전송 실패: {}", e.getMessage());
        }
    }
}