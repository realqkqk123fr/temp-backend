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
import org.example.capstone.recipe.domain.Ingredient;
import org.example.capstone.recipe.domain.Instruction;
import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.recipe.dto.*;
import org.example.capstone.recipe.repository.IngredientRepository;
import org.example.capstone.recipe.repository.InstructionRepository;
import org.example.capstone.recipe.repository.RecipeRepository;
import org.example.capstone.statisfaction.domain.Satisfaction;
import org.example.capstone.statisfaction.repository.SatisfactionRepository;
import org.example.capstone.user.domain.User;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.example.capstone.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.example.capstone.global.exception.ErrorCode.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FlaskRecipeService {

    // Flask API 엔드포인트 관련 설정
    @Value("${flask.api.base-url}")
    private String flaskBaseUrl;

    @Value("${flask.api.endpoints.chat}")
    private String chatEndpoint;

    @Value("${flask.api.endpoints.recipe-generate}")
    private String recipeGenerateEndpoint;

    @Value("${flask.api.endpoints.substitute}")
    private String substituteEndpoint;

    // 웹 클라이언트 및 Repository 관련 필드
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final InstructionRepository instructionRepository;
    private final UserRepository userRepository;
    private final SatisfactionRepository satisfactionRepository;

    /**
     * 사용자의 만족도 검색
     */
    private List<Satisfaction> findSatisfaction(List<Recipe> recipes) {
        List<Satisfaction> satisfactions = new ArrayList<>();
        for (Recipe recipe : recipes) {
            satisfactions.add(satisfactionRepository.findByRecipe(recipe));
        }
        return satisfactions;
    }

    /**
     * 사용자 정보를 Flask로 전송
     */
    public Mono<Void> sendUserInfoToFlask(CustomUserDetails userDetails) {
        log.debug("Flask API로 유저 정보 전송: {}", userDetails);
        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND));
        List<Recipe> recipes = recipeRepository.findByUser(user);
        List<Satisfaction> satisfactions = findSatisfaction(recipes);

        UserInfoDTO info = UserInfoDTO.builder()
                .user(user)
                .recipes(recipes)
                .satisfactions(satisfactions)
                .build();

        return webClient.post()
                .uri(chatEndpoint)
                .bodyValue(info)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.debug("Flask API로 유저 정보 전송 성공"))
                .doOnError(e -> log.error("Flask API로 유저 정보 전송 실패: {}", e.getMessage()));
    }

    /**
     * 이미지 분석 및 레시피 생성 요청을 Flask 서버로 전송
     */
    @Transactional
    public RecipeGenerateResponse generateRecipeFromImage(RecipeGenerateRequest request) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 전체 URL 구성
            String fullUrl = flaskBaseUrl + recipeGenerateEndpoint;
            log.info("Requesting to Flask URL: {}", fullUrl);
            log.info("사용자 정보: 사용자명={}, 요청자 ID={}", request.getUsername(), request.getUserId());

            // 이 부분에서 fullUrl을 사용하도록 수정
            HttpPost uploadFile = new HttpPost(fullUrl);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("instructions", request.getInstructions(), ContentType.TEXT_PLAIN);
            builder.addTextBody("username", request.getUsername(), ContentType.TEXT_PLAIN);

            if (request.getSessionId() != null) {
                builder.addTextBody("sessionId", request.getSessionId(), ContentType.TEXT_PLAIN);
            }

            if (request.getImage() != null && !request.getImage().isEmpty()) {
                // 파일 이름에서 비ASCII 문자 제거
                String originalFilename = request.getImage().getOriginalFilename();
                String safeFilename = originalFilename != null ?
                        originalFilename.replaceAll("[^a-zA-Z0-9.\\-]", "_") :
                        "image.jpg";

                builder.addBinaryBody(
                        "image",
                        request.getImage().getInputStream(),
                        ContentType.MULTIPART_FORM_DATA,
                        safeFilename // 안전한 파일 이름 사용
                );
            }

            HttpEntity multipart = builder.build();
            uploadFile.setEntity(multipart);

            try (CloseableHttpResponse response = httpClient.execute(uploadFile)) {
                HttpEntity responseEntity = response.getEntity();
                String responseString = EntityUtils.toString(responseEntity);
                log.info("Flask API Response for Recipe Generate: {}", responseString);

                RecipeGenerateResponse flaskResponse = objectMapper.readValue(responseString, RecipeGenerateResponse.class);

                // 현재 요청 사용자의 정보로 레시피 저장
                User user = null;
                if (request.getUserId() != null) {
                    // 사용자 ID로 사용자 조회
                    user = userRepository.findById(request.getUserId())
                            .orElseThrow(() -> new CustomException(USER_NOT_FOUND));
                    log.info("레시피 소유자 설정: {}, ID: {}", user.getUsername(), user.getId());
                } else if (request.getUsername() != null) {
                    // 사용자명으로 사용자 조회 (대체 방법)
                    user = userRepository.findByUsername(request.getUsername());
                    if (user == null) {
                        throw new CustomException(USER_NOT_FOUND);
                    }
                    log.info("레시피 소유자 설정(사용자명): {}, ID: {}", user.getUsername(), user.getId());
                } else {
                    throw new CustomException(USER_NOT_FOUND);
                }

                Recipe savedRecipe = saveRecipeFromResponse(flaskResponse, user);
                flaskResponse.setId(savedRecipe.getId());

                // 응답에 사용자 ID 설정 (클라이언트에서 확인용)
                flaskResponse.setUserId(user.getId());

                return flaskResponse;
            }
        } catch (Exception e) {
            log.error("Flask Recipe API 통신 중 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * RecipeGenerateResponse로부터 레시피 저장 (사용자 객체 직접 전달)
     */
    private Recipe saveRecipeFromResponse(RecipeGenerateResponse recipeResponse, User user) {
        // 레시피 소유자 정보 명시적 로깅
        log.info("레시피 저장 시작 - 소유자: {}, ID: {}", user.getUsername(), user.getId());

        // 레시피 엔티티 생성
        Recipe recipe = Recipe.builder()
                .name(recipeResponse.getName())
                .description(recipeResponse.getDescription())
                .user(user) // 명시적으로 사용자 설정
                .build();

        // 재료 엔티티 생성
        List<Ingredient> ingredients = new ArrayList<>();
        if (recipeResponse.getIngredients() != null) {
            for (IngredientDTO dto : recipeResponse.getIngredients()) {
                Ingredient ingredient = Ingredient.builder()
                        .name(dto.getName())
                        .recipe(recipe)
                        .build();
                ingredients.add(ingredient);
            }
        }
        recipe.setIngredients(ingredients);

        // 조리 단계 엔티티 생성
        List<Instruction> instructions = new ArrayList<>();
        if (recipeResponse.getInstructions() != null) {
            for (InstructionDTO dto : recipeResponse.getInstructions()) {
                Instruction instruction = Instruction.builder()
                        .instruction(dto.getInstruction())
                        .cookingTime(dto.getCookingTime())
                        .recipe(recipe)
                        .build();
                instructions.add(instruction);
            }
        }
        recipe.setInstructions(instructions);

        // 레시피 저장
        Recipe savedRecipe = recipeRepository.save(recipe);
        log.info("레시피 저장 완료 - ID: {}, 소유자: {}, 소유자 ID: {}",
                savedRecipe.getId(), savedRecipe.getUser().getUsername(), savedRecipe.getUser().getId());

        return savedRecipe;
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
                .map(response -> {
                    if (response != null) {
                        log.debug("대체 재료 요청 성공: {}", response.getName());

                        try {
                            // 레시피 ID로 레시피 조회
                            if (request.getRecipeId() != null) {
                                Recipe originalRecipe = recipeRepository.findById(request.getRecipeId())
                                        .orElseThrow(() -> new CustomException(RECIPE_NOT_FOUND));

                                // 원본 레시피 사용자를 사용
                                Recipe savedRecipe = saveRecipeFromResponse(response, originalRecipe.getUser());
                                response.setId(savedRecipe.getId());
                                response.setUserId(savedRecipe.getUser().getId());  // 응답에 사용자 ID 설정
                            }
                        } catch (Exception e) {
                            log.error("대체 레시피 저장 중 오류: {}", e.getMessage());
                            // 오류 발생 시에도 응답은 반환
                        }
                    }
                    return response;
                })
                .doOnError(e -> log.error("대체 재료 요청 실패: {}", e.getMessage()));
    }

    // 레시피 ID로 레시피 조회
    public Recipe getRecipeById(Long recipeId, CustomUserDetails userDetails) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new CustomException(RECIPE_NOT_FOUND));

        // 사용자 권한 검증 부분은 일단 유지
        // 필요시 이 부분을 수정하여 모든 인증된 사용자가 레시피를 볼 수 있도록 할 수 있음
        if (userDetails != null && !recipe.getUser().getId().equals(userDetails.getUserId())) {
            log.warn("레시피 접근 권한 오류 - 레시피 ID: {}, 소유자 ID: {}, 요청자 ID: {}",
                    recipeId, recipe.getUser().getId(), userDetails.getUserId());
            throw new CustomException(INVALID_USER);
        }

        return recipe;
    }

    // 어시스턴스 응답 생성
    public RecipeAssistanceResponse createAssistanceResponse(Recipe recipe) {
        List<InstructionDTO> instructions = new ArrayList<>();

        // 레시피의 지시사항을 단계별로 변환
        if (recipe.getInstructions() != null) {
            for (int i = 0; i < recipe.getInstructions().size(); i++) {
                Instruction instruction = recipe.getInstructions().get(i);

                instructions.add(InstructionDTO.builder()
                        .instruction(instruction.getInstruction())
                        .cookingTime(instruction.getCookingTime())
                        .stepNumber(i + 1)  // 단계 번호 추가
                        .build());
            }
        }

        // 레시피의 재료 정보 변환
        List<IngredientDTO> ingredients = recipe.getIngredients().stream()
                .map(ingredient -> IngredientDTO.builder()
                        .name(ingredient.getName())
                        // 필요한 추가 필드 설정
                        .build())
                .collect(Collectors.toList());

        // 어시스턴스 응답 생성
        return RecipeAssistanceResponse.builder()
                .id(recipe.getId())
                .name(recipe.getName())
                .description(recipe.getDescription())
                .instructions(instructions)
                .ingredients(ingredients)
                // 필요한 추가 정보 설정
                .build();
    }
}