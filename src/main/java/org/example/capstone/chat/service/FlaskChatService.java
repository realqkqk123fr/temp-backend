package org.example.capstone.chat.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.example.capstone.chat.dto.ChatRequest;
import org.example.capstone.chat.dto.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlaskChatService {

    @Value("${flask.api.endpoints.chat}")
    private String flaskChatEndpoint;

    @Value("${flask.api.base-url}")
    private String flaskBaseUrl;

    private final ObjectMapper objectMapper;

    /**
     * 초기화 메서드 - 객체 매퍼 설정
     */
    @PostConstruct
    public void init() {
        // UTF-8 인코딩 설정
        objectMapper.configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);
        // NULL 값은 JSON에 포함하지 않음
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * 채팅 요청을 Flask 서버로 전송
     *
     * @param chatRequest 채팅 요청 객체
     * @return ChatResponse 응답 객체
     * @throws IOException 통신 오류 발생 시
     */
    public ChatResponse sendRequestToFlask(ChatRequest chatRequest) throws IOException {
        // Flask API URL 구성
        String fullUrl = flaskBaseUrl + flaskChatEndpoint;
        log.info("Flask API 요청 URL: {}", fullUrl);

        // 전송할 데이터 로깅
        log.info("전송할 메시지: {}", chatRequest.getMessage());
        log.info("전송할 사용자명: {}", chatRequest.getUsername());
        log.info("전송할 세션 ID: {}", chatRequest.getSessionId());

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // HTTP POST 요청 생성
            HttpPost httpPost = new HttpPost(fullUrl);

            // JSON 요청 본문 구성
            Map<String, String> requestBody = new HashMap<>();
            if (chatRequest.getMessage() != null) {
                requestBody.put("message", chatRequest.getMessage());
            }
            if (chatRequest.getUsername() != null) {
                requestBody.put("username", chatRequest.getUsername());
            }
            if (chatRequest.getSessionId() != null) {
                requestBody.put("sessionId", chatRequest.getSessionId());
            }

            // Map을 JSON 문자열로 변환
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.info("JSON 요청 본문: {}", jsonBody);

            // StringEntity로 요청 본문 설정 (UTF-8 인코딩 명시)
            StringEntity entity = new StringEntity(jsonBody, StandardCharsets.UTF_8);
            entity.setContentType("application/json; charset=UTF-8");
            httpPost.setEntity(entity);

            // 요청 헤더 설정
            httpPost.setHeader("Content-Type", "application/json; charset=UTF-8");
            httpPost.setHeader("Accept", "application/json");

            // 요청 전송 및 응답 수신
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                HttpEntity responseEntity = response.getEntity();

                // 응답 본문을 UTF-8로 디코딩하여 문자열로 변환
                String responseString = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
                log.info("Flask API Chat Response: {}", responseString);

                // 응답 본문이 비어있는지 확인
                if (responseString == null || responseString.trim().isEmpty()) {
                    log.error("Flask API 응답이 비어있습니다.");
                    ChatResponse errorResponse = new ChatResponse();
                    errorResponse.setMessage("서버에서 빈 응답이 반환되었습니다.");
                    errorResponse.setUsername("시스템");
                    return errorResponse;
                }

                try {
                    // 응답 JSON을 ChatResponse 객체로 파싱
                    ChatResponse chatResponse = objectMapper.readValue(responseString, ChatResponse.class);
                    return chatResponse;
                } catch (Exception e) {
                    // JSON 파싱 오류 처리
                    log.error("응답 파싱 오류: {}", e.getMessage(), e);
                    ChatResponse errorResponse = new ChatResponse();
                    errorResponse.setMessage("응답 처리 중 오류가 발생했습니다: " + e.getMessage());
                    errorResponse.setUsername("시스템");
                    return errorResponse;
                }
            }
        } catch (Exception e) {
            // 네트워크 통신 오류 처리
            log.error("Flask 서버 통신 오류: {}", e.getMessage(), e);
            throw new IOException("Flask 서버와 통신 중 오류 발생: " + e.getMessage(), e);
        }
    }
}