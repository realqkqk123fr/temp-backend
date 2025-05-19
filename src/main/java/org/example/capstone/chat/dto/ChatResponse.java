package org.example.capstone.chat.dto;

import lombok.Data;

@Data
public class ChatResponse {
    private String message;
    private String username;
    private String imageUrl;
    private String sessionId; // 새로 추가된 필드
}
