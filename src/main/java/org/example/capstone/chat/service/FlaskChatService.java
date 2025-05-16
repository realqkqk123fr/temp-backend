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
            builder.addTextBody("message", chatRequest.getMessage(), ContentType.TEXT_PLAIN);
            builder.addTextBody("username", chatRequest.getUsername(), ContentType.TEXT_PLAIN);

            if (chatRequest.getImage() != null && !chatRequest.getImage().isEmpty()) {
                builder.addBinaryBody(
                        "image",
                        chatRequest.getImage().getInputStream(),
                        ContentType.MULTIPART_FORM_DATA,
                        chatRequest.getImage().getOriginalFilename()
                );
            }

            HttpEntity multipart = builder.build();
            uploadFile.setEntity(multipart);

            try (CloseableHttpResponse response = httpClient.execute(uploadFile)) {
                HttpEntity responseEntity = response.getEntity();
                String responseString = EntityUtils.toString(responseEntity);
                log.info("Flask API Response: {}", responseString);
                return objectMapper.readValue(responseString, ChatResponse.class);
            }
        }
    }
}
