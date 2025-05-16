package org.example.capstone.user.mypage.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.global.exception.CustomException;
import org.example.capstone.global.exception.ErrorCode;
import org.example.capstone.user.domain.User;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.example.capstone.user.mypage.dto.MypageRequest;
import org.example.capstone.user.mypage.dto.MypageResponse;
import org.example.capstone.user.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j  // 로깅 추가
public class MypageService {

    private final UserRepository userRepository;

    public MypageResponse getMypage(CustomUserDetails customUserDetails) {
        try {
            // 디버그 로그 추가
            log.debug("마이페이지 요청: 사용자={}",
                    customUserDetails != null ? customUserDetails.getUserEmail() : "null");

            // NPE 방지
            if (customUserDetails == null) {
                log.error("사용자 정보가 없습니다.");
                throw new CustomException(ErrorCode.USER_NOT_FOUND);
            }

            // 수정: 이메일 또는 사용자명으로 사용자 검색
            User user = null;
            String email = customUserDetails.getUserEmail();
            String username = customUserDetails.getUsername();

            if (email != null) {
                log.debug("이메일로 사용자 검색: {}", email);
                user = userRepository.findByEmail(email);
            }

            // 이메일로 찾지 못한 경우 사용자명으로 시도
            if (user == null && username != null) {
                log.debug("사용자명으로 사용자 검색: {}", username);
                user = userRepository.findByUsername(username);
            }

            // 사용자가 없는 경우 처리
            if (user == null) {
                log.error("사용자를 찾을 수 없습니다. 이메일: {}, 사용자명: {}", email, username);
                throw new CustomException(ErrorCode.USER_NOT_FOUND);
            }

            log.debug("사용자 정보 조회 성공: {}", user.getUsername());
            return new MypageResponse(user);
        } catch (CustomException e) {
            // 예외 상세 로깅
            log.error("마이페이지 조회 중 오류 발생: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            // 예외 상세 로깅
            log.error("마이페이지 조회 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public MypageResponse updateMypage(CustomUserDetails customUserDetails, MypageRequest request) {
        try {
            // 디버그 로그 추가
            log.debug("마이페이지 업데이트 요청: 사용자={}, 요청={}",
                    customUserDetails != null ? customUserDetails.getUserEmail() : "null",
                    request);

            // NPE 방지
            if (customUserDetails == null) {
                log.error("사용자 정보가 없습니다.");
                throw new CustomException(ErrorCode.USER_NOT_FOUND);
            }

            // 수정: 이메일 또는 사용자명으로 사용자 검색
            User user = null;
            String email = customUserDetails.getUserEmail();
            String username = customUserDetails.getUsername();

            if (email != null) {
                log.debug("이메일로 사용자 검색: {}", email);
                user = userRepository.findByEmail(email);
            }

            // 이메일로 찾지 못한 경우 사용자명으로 시도
            if (user == null && username != null) {
                log.debug("사용자명으로 사용자 검색: {}", username);
                user = userRepository.findByUsername(username);
            }

            // 사용자가 없는 경우 처리
            if (user == null) {
                log.error("사용자를 찾을 수 없습니다. 이메일: {}, 사용자명: {}", email, username);
                throw new CustomException(ErrorCode.USER_NOT_FOUND);
            }

            log.debug("사용자 정보 업데이트 시작: {}", user.getUsername());
            User updatedUser = userRepository.save(user.updateUser(request));
            log.debug("사용자 정보 업데이트 완료: {}", updatedUser.getUsername());

            return new MypageResponse(updatedUser);
        } catch (CustomException e) {
            // 예외 상세 로깅
            log.error("마이페이지 업데이트 중 오류 발생: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            // 예외 상세 로깅
            log.error("마이페이지 업데이트 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}