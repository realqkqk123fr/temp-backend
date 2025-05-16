package org.example.capstone.chat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.chat.dto.ChatRequest;
import org.example.capstone.chat.dto.ChatResponse;
import org.example.capstone.chat.service.FlaskChatService;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final FlaskChatService flaskChatService;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatRequest chatRequest, SimpMessageHeaderAccessor headerAccessor) {
        // 헤더에서 인증 정보 가져오기
        Principal user = headerAccessor.getUser();

        // sessionId 파라미터 처리 추가
        String sessionId = chatRequest.getSessionId();

        // 로그 추가
        log.debug("채팅 메시지 수신 - 헤더 인증 정보: {}, 세션 ID: {}",
                user != null ? user.getName() : "인증 정보 없음",
                sessionId);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.debug("SecurityContext 인증 정보: {}",
                authentication != null ? authentication.getName() : "인증 정보 없음");

        // 인증 정보 확인
        if (user == null && authentication == null) {
            log.warn("인증되지 않은 사용자의 메시지 요청");
            return;
        }

        try {
            // 사용자 이름 설정
            String username = null;
            if (user != null) {
                username = user.getName();
            } else if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
                CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
                username = userDetails.getUsername();
            } else {
                log.warn("사용자 이름을 확인할 수 없습니다.");
                return;
            }

            log.debug("메시지 처리 - 사용자: {}, 메시지: {}, 세션: {}",
                    username, chatRequest.getMessage(), sessionId);
            chatRequest.setUsername(username);

            // WebSocket으로는 이미지 전송 불가 - REST API로만 이미지 처리
            chatRequest.setImage(null);

            // Flask 서버에 요청 전송
            ChatResponse response = flaskChatService.sendRequestToFlask(chatRequest);
            log.debug("Flask 응답 수신: {}", response);

            // 응답을 사용자에게 전송
            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/messages",
                    response
            );

            log.debug("응답 메시지 전송 완료: {}", username);

        } catch (IOException e) {
            log.error("Flask 서버와 통신 중 오류 발생: {}", e.getMessage(), e);

            // 사용자 이름 확보
            String errorUsername = "unknown";
            if (user != null) {
                errorUsername = user.getName();
            } else if (authentication != null && authentication.getName() != null) {
                errorUsername = authentication.getName();
            }

            // 오류 메시지 전송
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setUsername("시스템");
            errorResponse.setMessage("서버 오류가 발생했습니다: " + e.getMessage());

            messagingTemplate.convertAndSendToUser(
                    errorUsername,
                    "/queue/messages",
                    errorResponse
            );
        }
    }
}