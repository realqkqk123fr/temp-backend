// FlaskRecipeService.java
package org.example.capstone.recipe.service;

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
import org.example.capstone.global.exception.CustomException;
import org.example.capstone.global.exception.ErrorCode;
import org.example.capstone.nutrition.dto.NutritionDTO;
import org.example.capstone.recipe.dto.RecipeGenerateRequest;
import org.example.capstone.recipe.dto.RecipeGenerateResponse;
import org.example.capstone.recipe.dto.SubstituteIngredientRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlaskRecipeService {

    @Value("${flask.api.endpoints.recipe-generate}")
    private String recipeGenerateEndpoint;

    @Value("${flask.api.endpoints.nutrition}")
    private String nutritionEndpoint;

    @Value("${flask.api.endpoints.substitute}")
    private String substituteEndpoint;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * 이미지 분석 및 레시피 생성 요청을 Flask 서버로 전송
     */
    public RecipeGenerateResponse generateRecipeFromImage(RecipeGenerateRequest request) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(recipeGenerateEndpoint);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("instructions", request.getInstructions(), ContentType.TEXT_PLAIN);
            builder.addTextBody("username", request.getUsername(), ContentType.TEXT_PLAIN);

            if (request.getSessionId() != null) {
                builder.addTextBody("sessionId", request.getSessionId(), ContentType.TEXT_PLAIN);
            }

            if (request.getImage() != null && !request.getImage().isEmpty()) {
                builder.addBinaryBody(
                        "image",
                        request.getImage().getInputStream(),
                        ContentType.MULTIPART_FORM_DATA,
                        request.getImage().getOriginalFilename()
                );
            }

            HttpEntity multipart = builder.build();
            uploadFile.setEntity(multipart);

            try (CloseableHttpResponse response = httpClient.execute(uploadFile)) {
                HttpEntity responseEntity = response.getEntity();
                String responseString = EntityUtils.toString(responseEntity);
                log.info("Flask API Response for Recipe Generate: {}", responseString);
                return objectMapper.readValue(responseString, RecipeGenerateResponse.class);
            }
        } catch (Exception e) {
            log.error("Flask Recipe API 통신 중 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 레시피 ID에 해당하는 영양 정보 조회
     */
    public Mono<NutritionDTO> getNutritionInfo(Long recipeId, String ingredients) {
        log.debug("레시피 {}의 영양 정보 요청", recipeId);

        return webClient.post()
                .uri(nutritionEndpoint)
                .bodyValue(Map.of("ingredients", ingredients))
                .retrieve()
                .bodyToMono(NutritionDTO.class)
                .doOnSuccess(nutrition -> log.debug("레시피 {}의 영양 정보 응답 성공", recipeId))
                .doOnError(e -> log.error("레시피 {}의 영양 정보 요청 실패: {}", recipeId, e.getMessage()));
    }

    /**
     * 대체 재료 요청 처리
     */
    public Mono<RecipeGenerateResponse> substituteIngredient(SubstituteIngredientRequest request) {
        log.debug("대체 재료 요청: 원재료={}, 대체재료={}, 레시피={}",
                request.getOriginalIngredient(), request.getSubstituteIngredient(), request.getRecipeName());

        return webClient.post()
                .uri(substituteEndpoint)
                .bodyValue(Map.of(
                        "ori", request.getOriginalIngredient(),
                        "sub", request.getSubstituteIngredient(),
                        "recipe", request.getRecipeName()
                ))
                .retrieve()
                .bodyToMono(RecipeGenerateResponse.class)
                .doOnSuccess(recipe -> log.debug("대체 재료 요청 성공: {}", recipe.getName()))
                .doOnError(e -> log.error("대체 재료 요청 실패: {}", e.getMessage()));
    }
}