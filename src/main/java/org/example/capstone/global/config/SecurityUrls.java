package org.example.capstone.global.config;

import java.util.Arrays;
import java.util.List;

public class SecurityUrls {

    /**
     * 인증 생략할 URL 목록
     */
    public static final List<String> AUTH_WHITELIST = Arrays.asList(
            "/api/auth/login",  // 로그인
            "/api/auth/register",   // 회원가입
            "/docs/**",  // swagger
            "/v3/api-docs/**",   // swagger api
            "/ws/**",   // WebSocket 엔드포인트
            "/topic/**",  // STOMP 토픽
            "/queue/**",  // STOMP 큐
            "/app/**"     // STOMP 애플리케이션 접두사
    );

    /**
     * cors 허용할 URL
     */
    public static final List<String> ALLOWED_ORIGIN = Arrays.asList(
            "http://localhost:3000",
            "http://localhost:8080",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:8080"
    );
}