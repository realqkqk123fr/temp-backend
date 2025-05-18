package org.example.capstone.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.global.config.SecurityUrls;
import org.example.capstone.global.util.JwtUtil;
import org.example.capstone.user.domain.User;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.example.capstone.user.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private static final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 디버그 로그 추가
        log.debug("JwtFilter 실행: {}", request.getRequestURI());

        //인증 생략 경로
        if (isWhitelistedPath(request.getRequestURI())) {
            log.debug("인증 생략 경로: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        //request에서 Authorization 헤더 찾음
        String auth = request.getHeader("Authorization");

        //검증
        if (auth == null || !auth.startsWith("Bearer ")) {
            log.error("토큰이 존재하지 않거나 형식이 잘못되었습니다.");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String token = auth.split(" ")[1];

        // 토큰 유효성 검증
        try {
            if (!jwtUtil.validateToken(token)) {
                log.error("JWT토큰이 유효하지 않습니다.");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            // 토큰에서 username과 email 획득
            String username = jwtUtil.getUsername(token);
            String email = jwtUtil.getUserEmail(token);

            log.debug("인증된 사용자: {}, 이메일: {}", username, email);

            // 중요: 토큰에서 얻은 정보를 바탕으로 User 엔티티를 데이터베이스에서 찾아야 함
            User user = null;

            // 이메일로 사용자 찾기 시도
            if (email != null && !email.isEmpty()) {
                user = userRepository.findByEmail(email);
                if (user != null) {
                    log.info("이메일로 사용자 찾음: {}, ID: {}", email, user.getId());
                }
            }

            // 이메일로 찾지 못했다면 사용자명으로 시도
            if (user == null && username != null && !username.isEmpty()) {
                user = userRepository.findByUsername(username);
                if (user != null) {
                    log.info("사용자명으로 사용자 찾음: {}, ID: {}", username, user.getId());
                }
            }

            // 어느 방법으로도 사용자를 찾지 못한 경우
            if (user == null) {
                log.warn("인증 토큰은 유효하지만 DB에서 사용자를 찾을 수 없음: {}, {}", username, email);
                // 새 User 객체 생성 (ID는 여전히 null)
                user = User.builder()
                        .username(username)
                        .email(email)
                        .build();
            }

            // UserDetails에 회원 정보 객체 담기
            CustomUserDetails customUserDetails = new CustomUserDetails(user);

            // 디버깅 로그 추가
            log.info("인증 정보 설정: 사용자={}, ID={}",
                    customUserDetails.getUsername(),
                    customUserDetails.getUserId());

            // 스프링 시큐리티 인증 토큰 생성
            Authentication authToken = new UsernamePasswordAuthenticationToken(
                    customUserDetails,
                    null,
                    customUserDetails.getAuthorities()
            );

            // 세션에 사용자 등록
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (Exception e) {
            log.error("JWT 토큰 처리 중 오류 발생: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 인증 생략 경로 확인
     * 와일드카드 패턴 매칭을 위해 AntPathMatcher 사용
     */
    private boolean isWhitelistedPath(String uri) {
        return SecurityUrls.AUTH_WHITELIST.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }
}