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

// org.example.capstone.chat.service.FlaskChatService.java

@Service
@RequiredArgsConstructor
@Slf4j
public class FlaskChatService {

    @Value("${flask.api.endpoints.chat}")
    private String flaskChatEndpoint;

    @Value("${flask.api.base-url}")
    private String flaskBaseUrl;

    private final ObjectMapper objectMapper;

    public ChatResponse sendRequestToFlask(ChatRequest chatRequest) throws IOException {
        // URL 로깅 추가
        String fullUrl = flaskBaseUrl + flaskChatEndpoint;
        log.info("Flask API 요청 URL: {}", fullUrl);

        // URL 유효성 확인
        if (fullUrl == null || fullUrl.trim().isEmpty() || !fullUrl.startsWith("http")) {
            log.error("잘못된 Flask API URL: {}", fullUrl);
            throw new IllegalArgumentException("유효하지 않은 Flask API URL: " + fullUrl);
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 여기서 수정: 완전한 URL 사용
            HttpPost httpPost = new HttpPost(fullUrl);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            // Flask API 파라미터 추가
            builder.addTextBody("message", chatRequest.getMessage(), ContentType.TEXT_PLAIN);
            builder.addTextBody("username", chatRequest.getUsername(), ContentType.TEXT_PLAIN);

            // 세션 ID가 있다면 추가
            if (chatRequest.getSessionId() != null) {
                builder.addTextBody("sessionId", chatRequest.getSessionId(), ContentType.TEXT_PLAIN);
            }

            HttpEntity multipart = builder.build();
            httpPost.setEntity(multipart);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                HttpEntity responseEntity = response.getEntity();
                String responseString = EntityUtils.toString(responseEntity);
                log.info("Flask API Chat Response: {}", responseString);

                // 응답 파싱
                ChatResponse chatResponse = objectMapper.readValue(responseString, ChatResponse.class);
                return chatResponse;
            }
        }
    }
}