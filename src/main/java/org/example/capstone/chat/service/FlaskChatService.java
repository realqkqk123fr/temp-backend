// FlaskChatService.java
package org.example.capstone.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.example.capstone.chat.dto.ChatRequest;
import org.example.capstone.chat.dto.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlaskChatService {

    @Value("${flask.api.endpoints.chat}")
    private String flaskApiUrl;

    private final ObjectMapper objectMapper;

    public ChatResponse sendRequestToFlask(ChatRequest chatRequest) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(flaskApiUrl);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            // Flask API는 "message" 파라미터 사용
            builder.addTextBody("message", chatRequest.getMessage(), ContentType.TEXT_PLAIN);
            builder.addTextBody("username", chatRequest.getUsername(), ContentType.TEXT_PLAIN);

            // 세션 ID가 있다면 추가
            if (chatRequest.getSessionId() != null) {
                builder.addTextBody("sessionId", chatRequest.getSessionId(), ContentType.TEXT_PLAIN);
            }

            HttpEntity multipart = builder.build();
            uploadFile.setEntity(multipart);

            try (CloseableHttpResponse response = httpClient.execute(uploadFile)) {
                HttpEntity responseEntity = response.getEntity();
                String responseString = EntityUtils.toString(responseEntity);
                log.info("Flask API Chat Response: {}", responseString);

                // 채팅 응답만 파싱
                ChatResponse chatResponse = objectMapper.readValue(responseString, ChatResponse.class);

                return chatResponse;
            }
        }
    }
}