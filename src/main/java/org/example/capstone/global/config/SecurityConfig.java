package org.example.capstone.global.config;

import lombok.RequiredArgsConstructor;
import org.example.capstone.global.filter.JwtFilter;
import org.example.capstone.global.filter.LoginFilter;
import org.example.capstone.global.util.JwtUtil;
import org.example.capstone.user.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final AuthenticationConfiguration authenticationConfiguration;
    private final UserRepository userRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // 로그인 경로를 설정하기 위해 LoginFilter 생성
        LoginFilter loginFilter = new LoginFilter(jwtUtil, authenticationManager(authenticationConfiguration), userRepository);
        loginFilter.setFilterProcessesUrl("/api/auth/login");

        return http
                //cors 설정
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                //csrf 설정
                .csrf(AbstractHttpConfigurer::disable)
                //http basic 인증 방식 설정
                .httpBasic(AbstractHttpConfigurer::disable)
                //form 로그인 방식 설정
                .formLogin(AbstractHttpConfigurer::disable)
                //경로별 인가 작업
                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers(SecurityUrls.AUTH_WHITELIST.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated()
                )
                //stateless 설정
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                //필터 위치
                .addFilterBefore(
                        new JwtFilter(jwtUtil),
                        LoginFilter.class
                )
                .addFilterAt(
                        loginFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }

    /**
     * 인증 메니저 설정
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception{
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * CORS 설정
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(){
        CorsConfiguration configuration = new CorsConfiguration();

        // 모든 출처 허용 (개발 중에는 편의를 위해)
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));

        // 모든 HTTP 메서드 허용
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(Collections.singletonList("*"));

        // WebSocket 헤더 허용
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Set-Cookie"));
        configuration.setMaxAge(3600L);

        //모든 경로에 CORS 설정
        UrlBasedCorsConfigurationSource urlBasedCorsConfigurationSource = new UrlBasedCorsConfigurationSource();
        urlBasedCorsConfigurationSource.registerCorsConfiguration("/**", configuration);
        return urlBasedCorsConfigurationSource;
    }

    /**
     * 인코더
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
}