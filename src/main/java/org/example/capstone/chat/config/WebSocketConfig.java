package org.example.capstone.chat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.global.util.JwtUtil;
import org.example.capstone.user.domain.User;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // CORS 설정 - 명시적으로 허용할 출처를 지정
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // 개발 중에는 모든 출처 허용, 프로덕션에서는 구체적인 도메인으로 변경
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null) {
                    // 세션 ID와 명령 로깅
                    log.debug("WebSocket 메시지 수신: 명령={}, 세션ID={}",
                            accessor.getCommand(), accessor.getSessionId());

                    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                        // CONNECT 명령 시 헤더에서 토큰 추출 및 처리
                        processAuthToken(accessor);
                    } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                        // SEND 명령 시 헤더에서 토큰 확인 및 처리
                        log.debug("WebSocket 메시지 전송 - 현재 인증 정보: {}",
                                accessor.getUser() != null ? accessor.getUser().getName() : "인증 없음");

                        // 메시지 헤더에서 토큰 확인
                        String authHeader = accessor.getFirstNativeHeader("Authorization");
                        if (authHeader != null && authHeader.startsWith("Bearer ")) {
                            log.debug("메시지 헤더에서 토큰 발견: {}", authHeader.substring(0, 20) + "...");
                            processAuthToken(accessor);
                        }

                        // 세션에 저장된 인증 정보가 있으면 SecurityContext에 설정
                        if (accessor.getUser() != null) {
                            log.debug("메시지 처리를 위해 세션의 인증 정보 적용: {}", accessor.getUser().getName());
                            SecurityContextHolder.getContext().setAuthentication(
                                    (UsernamePasswordAuthenticationToken) accessor.getUser());
                        }
                    }
                }

                return message;
            }

            // 인증 토큰 처리 헬퍼 메서드
            private void processAuthToken(StompHeaderAccessor accessor) {
                String authHeader = accessor.getFirstNativeHeader("Authorization");
                log.debug("Authorization 헤더: {}", authHeader);

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);

                    try {
                        // JWT 토큰 검증
                        if (jwtUtil.validateToken(token)) {
                            String username = jwtUtil.getUsername(token);
                            String email = jwtUtil.getUserEmail(token);

                            log.debug("WebSocket 인증 성공: 사용자={}, 이메일={}", username, email);

                            // User 객체 생성 및 인증 정보 설정
                            User user = User.builder()
                                    .username(username)
                                    .email(email)
                                    .build();

                            CustomUserDetails userDetails = new CustomUserDetails(user);

                            // 인증 객체 생성
                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(
                                            userDetails,
                                            null,
                                            userDetails.getAuthorities()
                                    );

                            // 헤더에 인증 객체 설정
                            accessor.setUser(auth);

                            // SecurityContext에도 동일한 인증 객체 설정
                            SecurityContextHolder.getContext().setAuthentication(auth);
                            log.debug("인증 정보 SecurityContext에 저장 완료");
                        }
                    } catch (Exception e) {
                        log.error("WebSocket 인증 실패: {}", e.getMessage(), e);
                    }
                } else {
                    log.warn("Authorization 헤더가 없거나 형식이 잘못되었습니다: {}", authHeader);
                }
            }
        });
    }
}