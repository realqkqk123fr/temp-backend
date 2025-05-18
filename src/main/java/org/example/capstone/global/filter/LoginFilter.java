package org.example.capstone.global.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.global.exception.CustomException;
import org.example.capstone.global.exception.ErrorCode;
import org.example.capstone.global.util.JwtUtil;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.example.capstone.user.login.dto.LoginRequest;
import org.example.capstone.user.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

        // 클라이언트 요청에서 email, password 추출
        try {
            // 요청 본문에서 JSON 데이터를 파싱
            LoginRequest loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class);

            String email = loginRequest.getEmail();
            String username = userRepository.findByEmail(email).getUsername();
            String password = loginRequest.getPassword();

            // 스프링 시큐리티에서 username과 password를 검증하기 위해서는 token에 담아야 함
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(username, password, null);

            // token 검증을 위한 AuthenticationManager로 전달
            return authenticationManager.authenticate(authToken);
        } catch (IOException e) {
            log.error("JSON 파싱 중 오류 발생");
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
    }

    //로그인 성공시 실행하는 메소드 (여기서 JWT를 발급하면 됨)
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authentication) throws IOException {

        // UserDetails
        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();

        // 사용자 ID 확인 및 로깅
        Long userId = customUserDetails.getUserId();
        log.info("인증 성공 - 사용자: {}, 이메일: {}, ID: {}",
                customUserDetails.getUsername(),
                customUserDetails.getUserEmail(),
                userId);

        if (userId == null) {
            log.warn("인증된 사용자의 ID가 null입니다. CustomUserDetails: {}", customUserDetails);
        }

        // AccessToken 발급
        String accessToken = jwtUtil.createAccessToken(customUserDetails);

        // RefreshToken 발급
        String refreshToken = jwtUtil.createRefreshToken(customUserDetails);

        // 헤더에 AccessToken 추가 - 수정: 올바른 헤더 설정 방법
        response.setHeader("Authorization", "Bearer " + accessToken);

        // CORS 문제를 방지하기 위해 Access-Control-Expose-Headers 헤더 추가
        response.setHeader("Access-Control-Expose-Headers", "Authorization");

        // 쿠키에 refreshToken 추가
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true); // HttpOnly 설정
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtUtil.getRefreshExpirationTime() / 1000)); // 쿠키 maxAge는 초 단위 이므로, 밀리초를 1000으로 나눔
        response.addCookie(cookie);

        // JSON 응답 생성
        Map<String, String> tokenMap = new HashMap<>();
        tokenMap.put("accessToken", accessToken);
        tokenMap.put("refreshToken", refreshToken);
        tokenMap.put("username", customUserDetails.getUsername());

        // JSON 응답 전송
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), tokenMap);
    }

    //로그인 실패시 실행하는 메소드
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException {
        log.error("로그인 실패: {}", failed.getMessage());
        response.setStatus(401);

        // JSON 오류 응답 생성
        Map<String, String> errorMap = new HashMap<>();
        errorMap.put("error", "로그인에 실패했습니다");
        errorMap.put("message", failed.getMessage());

        // JSON 응답 전송
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), errorMap);
    }
}