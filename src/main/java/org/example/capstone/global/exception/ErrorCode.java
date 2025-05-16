package org.example.capstone.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * errorcode 추가하여 사용
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 발생했습니다"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),


    //회원가입 로그인 에러
    EXISTING_EMAIL(HttpStatus.CONFLICT, "이미 존재하는 아이디 입니다."),
    PASSWORD_NOT_MATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 유저 입니다."),

    INVALID_USER(HttpStatus.BAD_REQUEST, "사용자가 일치 하지 않습니다."),

    RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "레시피를 찾을 수 없습니다."),
    NUTRITION_NOT_FOUND(HttpStatus.NOT_FOUND, "영양 성분 정보를 가져올 수 없습니다.");


    private final HttpStatus status;
    private final String message;


}
