package org.example.capstone.user.register.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.global.exception.CustomException;
import org.example.capstone.global.exception.ErrorCode;
import org.example.capstone.user.domain.User;
import org.example.capstone.user.register.dto.RegisterRequest;
import org.example.capstone.user.register.dto.RegisterResponse;
import org.example.capstone.user.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    /**
     * 회원가입
     */
    public RegisterResponse register(RegisterRequest request) {

        //중복 검증
        if (userRepository.existsByEmail(request.getEmail())) {
            log.error("이미 사용중인 아이디 입니다. 요청 아이디: {}", request.getUsername());
            throw new CustomException(ErrorCode.EXISTING_EMAIL);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(bCryptPasswordEncoder.encode(request.getPassword()))
                .age(request.getAge())
                .height(request.getHeight())
                .weight(request.getWeight())
                .habit(request.getHabit())
                .preference(request.getPreference())
                .build();

        userRepository.save(user);
        log.info("회원가입 완료");
        log.info("user={}", user.toString());
        return new RegisterResponse(user);
    }

}
