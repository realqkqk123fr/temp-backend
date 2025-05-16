package org.example.capstone.chat.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ChatRequest {
    private String message;
    private String username;
    private MultipartFile image;
    private String sessionId;       //추가
}
