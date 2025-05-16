package org.example.capstone.recipe.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.chat.dto.ChatRequest;
import org.example.capstone.chat.dto.ChatResponse;
import org.example.capstone.chat.service.FlaskChatService;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class RecipeUploadController {

    private final FlaskChatService flaskChatService;

    @PostMapping("/api/recipe/upload")
    public ResponseEntity<?> uploadRecipeImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam("instructions") String instructions,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            log.info("사용자 {} 이미지 업로드 요청 처리 중", userDetails.getUsername());

            // 1. 이미지 및 지시사항 Flask 서버로 전송
            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setMessage(instructions);
            chatRequest.setUsername(userDetails.getUsername());
            chatRequest.setImage(image);

            // 2. 세션 ID 생성 (UUID 사용)
            String sessionId = UUID.randomUUID().toString();

            // 3. Flask 서버에 전송
            ChatResponse initialResponse = flaskChatService.sendRequestToFlask(chatRequest);

            // 4. 클라이언트에게 세션 ID와 초기 응답 반환
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("initialResponse", initialResponse);
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("이미지 업로드 처리 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "이미지 업로드 처리 중 오류가 발생했습니다.", "details", e.getMessage()));
        }
    }
}