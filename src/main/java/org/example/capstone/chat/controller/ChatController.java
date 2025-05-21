// ChatController.java
package org.example.capstone.chat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.chat.dto.ChatRequest;
import org.example.capstone.chat.dto.ChatResponse;
import org.example.capstone.chat.service.FlaskChatService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.security.Principal;
import java.util.UUID;

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

        // 로그 추가
        log.debug("채팅 메시지 수신 - 헤더 인증 정보: {}, 세션 ID: {}",
                user != null ? user.getName() : "인증 정보 없음",
                chatRequest.getSessionId());

        // 인증 정보 확인
        if (user == null) {
            log.warn("인증되지 않은 사용자의 메시지 요청");
            return;
        }

        try {
            // 사용자 이름 설정
            String username = user.getName();
            log.debug("메시지 처리 - 사용자: {}, 메시지: {}", username, chatRequest.getMessage());
            chatRequest.setUsername(username);

            // 세션 ID 확인 및 처리
            if (chatRequest.getSessionId() == null || chatRequest.getSessionId().isEmpty()) {
                // 세션 ID가 없으면 생성
                chatRequest.setSessionId(UUID.randomUUID().toString());
                log.debug("새 세션 ID 생성: {}", chatRequest.getSessionId());
            } else {
                log.debug("기존 세션 ID 사용: {}", chatRequest.getSessionId());
            }

            // Flask 서버에 요청 전송 (순수 채팅 메시지만)
            ChatResponse response = flaskChatService.sendRequestToFlask(chatRequest);
            log.debug("Flask 채팅 응답 수신: {}", response.getMessage());

            // 응답에 세션 ID 설정
            response.setSessionId(chatRequest.getSessionId());
            log.debug("응답에 세션 ID 설정: {}", response.getSessionId());

            // 응답을 사용자에게 전송
            messagingTemplate.convertAndSendToUser(
                    username,
                    "/queue/messages",
                    response
            );

            log.debug("응답 메시지 전송 완료: {}", username);

        } catch (IOException e) {
            log.error("Flask 서버와 통신 중 오류 발생: {}", e.getMessage(), e);

            // 오류 메시지 전송
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setUsername("시스템");
            errorResponse.setMessage("서버 오류가 발생했습니다: " + e.getMessage());

            messagingTemplate.convertAndSendToUser(
                    user.getName(),
                    "/queue/messages",
                    errorResponse
            );
        }
    }
}