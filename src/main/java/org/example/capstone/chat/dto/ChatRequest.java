package org.example.capstone.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ChatRequest {
    private String message;
    private String username;
    private String sessionId;       //추가

    // 이미지 필드 제거하거나 JsonIgnore 추가
    @JsonIgnore
    private MultipartFile image;  // JSON 직렬화에서 제외
}
