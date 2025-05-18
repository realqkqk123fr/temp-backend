package org.example.capstone.statisfaction.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.global.exception.CustomException;
import org.example.capstone.global.exception.ErrorCode;
import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.recipe.repository.RecipeRepository;
import org.example.capstone.statisfaction.domain.Satisfaction;
import org.example.capstone.statisfaction.dto.SatisfactionRequest;
import org.example.capstone.statisfaction.repository.SatisfactionRepository;
import org.example.capstone.user.domain.User;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.example.capstone.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static org.example.capstone.global.exception.ErrorCode.USER_NOT_FOUND;


@Service
@Slf4j
@RequiredArgsConstructor
public class SatisfactionService {

    private final SatisfactionRepository satisfactionRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

    // 메소드명 오타 수정 (saveSatisafction -> saveSatisfaction)
    public void saveSatisfaction(Long recipeId, CustomUserDetails userDetails, SatisfactionRequest satisfactionRequest) {
        log.debug("만족도 평가 저장 처리 시작: {}", satisfactionRequest);

        // 1. 파라미터 로깅 개선
        log.info("만족도 평가 저장 요청 - 레시피 ID: {}, 평점: {}, 코멘트: {}, 사용자 정보: {}",
                recipeId,
                satisfactionRequest.getRate(),
                satisfactionRequest.getComment(),
                userDetails != null ? "사용자 ID=" + userDetails.getUserId() + ", 이름=" + userDetails.getUsername() : "null");

        // 2. 레시피 조회
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> {
                    log.error("레시피를 찾을 수 없음: ID={}", recipeId);
                    return new CustomException(ErrorCode.RECIPE_NOT_FOUND);
                });

        // 3. 사용자 정보 추출 및 검증 개선
        if (userDetails == null) {
            log.error("사용자 정보가 유효하지 않음 (userDetails is null)");
            throw new CustomException(USER_NOT_FOUND);
        }

        Long userId = userDetails.getUserId();
        if (userId == null) {
            log.error("사용자 ID가 null입니다. userDetails: {}", userDetails);
            throw new CustomException(USER_NOT_FOUND);
        }

        // 4. 사용자 인증 정보 출력
        log.info("인증된 사용자 정보 - ID: {}, 이름: {}, 이메일: {}",
                userId, userDetails.getUsername(), userDetails.getUserEmail());

        // 5. 사용자 조회 방식 개선
        User user = userRepository.findById(userId)
                .orElseGet(() -> {
                    // ID로 찾지 못하면 이메일로 시도
                    String email = userDetails.getUserEmail();
                    if (email != null && !email.isEmpty()) {
                        User foundUser = userRepository.findByEmail(email);
                        if (foundUser != null) {
                            log.info("ID로 사용자를 찾지 못했지만 이메일로 찾음: {}", email);
                            return foundUser;
                        }
                    }

                    // 이름으로 시도
                    String username = userDetails.getUsername();
                    if (username != null && !username.isEmpty()) {
                        User foundUser = userRepository.findByUsername(username);
                        if (foundUser != null) {
                            log.info("ID와 이메일로 사용자를 찾지 못했지만 이름으로 찾음: {}", username);
                            return foundUser;
                        }
                    }

                    log.error("사용자를 찾을 수 없음: ID={}, 이메일={}, 이름={}",
                            userId, userDetails.getUserEmail(), userDetails.getUsername());
                    return null;
                });

        // 6. 사용자를 찾지 못한 경우 처리
        if (user == null) {
            throw new CustomException(USER_NOT_FOUND);
        }

        log.info("사용자 정보 조회 성공 - ID: {}, 이름: {}, 이메일: {}",
                user.getId(), user.getUsername(), user.getEmail());

        // 7. 이미 존재하는 만족도 평가 확인
        Satisfaction existingSatisfaction = satisfactionRepository.findByRecipeAndUser(recipe, user);

        // 8. 만족도 객체 생성 또는 업데이트
        Satisfaction satisfaction;
        if (existingSatisfaction != null) {
            // 기존 만족도 업데이트
            existingSatisfaction.setRate(satisfactionRequest.getRate());
            existingSatisfaction.setComment(satisfactionRequest.getComment());
            satisfaction = existingSatisfaction;
            log.info("기존 만족도 평가 업데이트 - ID: {}, 레시피: {}, 사용자: {}",
                    satisfaction.getId(), recipe.getId(), user.getId());
        } else {
            // 새 만족도 생성
            satisfaction = Satisfaction.builder()
                    .user(user)
                    .recipe(recipe)
                    .rate(satisfactionRequest.getRate())
                    .comment(satisfactionRequest.getComment())
                    .build();
            log.info("새 만족도 평가 생성 - 레시피: {}, 사용자: {}", recipe.getId(), user.getId());
        }

        // 9. 저장
        Satisfaction savedSatisfaction = satisfactionRepository.save(satisfaction);
        log.info("만족도 평가 저장 완료 - ID: {}", savedSatisfaction.getId());
    }
}
