package org.example.capstone.statisfaction.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.global.exception.CustomException;
import org.example.capstone.global.exception.ErrorResponse;
import org.example.capstone.statisfaction.dto.SatisfactionRequest;
import org.example.capstone.statisfaction.service.SatisfactionService;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
@Slf4j
@RequiredArgsConstructor
public class SatisfactionController {

    private final SatisfactionService satisfactionService;

    @PostMapping("/api/recipe/{recipeId}/satisfaction")
    public ResponseEntity<?> saveSatisfaction(@PathVariable Long recipeId,
                                              @RequestBody SatisfactionRequest satisfactionRequest,
                                              @AuthenticationPrincipal CustomUserDetails userDetails){
        // 1. 로깅 추가 - 요청 파라미터와 인증 정보 상세 기록
        log.info("만족도 평가 요청 - 레시피 ID: {}, 평점: {}, 사용자: {}",
                recipeId,
                satisfactionRequest.getRate(),
                userDetails != null ? userDetails.getUsername() : "인증 정보 없음");

        // 2. 사용자 인증 상태 명시적 검증
        if (userDetails == null) {
            log.error("인증된 사용자 정보가 없음 (userDetails is null)");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("인증된 사용자 정보가 없습니다. 다시 로그인해주세요.");
        }

        // 3. 유효성 검사 추가
        if (recipeId == null || recipeId <= 0) {
            log.error("유효하지 않은 레시피 ID: {}", recipeId);
            return ResponseEntity.badRequest().body("유효한 레시피 ID가 필요합니다.");
        }

        try {
            // 4. 서비스 메소드 호출
            satisfactionService.saveSatisfaction(recipeId, userDetails, satisfactionRequest);
            return ResponseEntity.ok("만족도가 저장되었습니다.");
        } catch (CustomException e) {
            // 5. 커스텀 예외 처리 개선
            log.error("만족도 저장 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(e.getErrorCode().getStatus())
                    .body(new ErrorResponse(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            // 6. 일반 예외 처리
            log.error("만족도 저장 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("서버 오류가 발생했습니다: " + e.getMessage());
        }
    }
}
