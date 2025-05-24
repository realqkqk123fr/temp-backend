package org.example.capstone.recipe.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

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

    @PostConstruct
    public void init() {
        // UTF-8 인코딩 설정
        objectMapper.configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);
        // 추가 설정
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

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

            // 요청에 실을 데이터 로깅
            log.info("지시사항: {}", request.getInstructions());

            // 이 부분에서 fullUrl을 사용하도록 수정
            HttpPost uploadFile = new HttpPost(fullUrl);

            // UTF-8 인코딩 명시적 설정
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.setCharset(StandardCharsets.UTF_8);

            // 지시사항 UTF-8로 인코딩하여 추가
            ContentType textContentType = ContentType.create("text/plain", StandardCharsets.UTF_8);
            builder.addTextBody("instructions", request.getInstructions(), textContentType);
            builder.addTextBody("username", request.getUsername(), textContentType);

            if (request.getSessionId() != null) {
                builder.addTextBody("sessionId", request.getSessionId(), textContentType);
            }

            if (request.getImage() != null && !request.getImage().isEmpty()) {
                // 파일 이름에서 비ASCII 문자 제거
                String originalFilename = request.getImage().getOriginalFilename();
                String safeFilename = originalFilename != null ?
                        originalFilename.replaceAll("[^a-zA-Z0-9.\\-]", "_") :
                        "image.jpg";

                // 확장자 처리 개선
                String fileExt = "";
                int lastDotIndex = safeFilename.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    fileExt = safeFilename.substring(lastDotIndex).toLowerCase();
                }
                if (fileExt.isEmpty()) {
                    fileExt = ".jpg";
                    safeFilename += fileExt;
                }

                log.info("이미지 파일명: {}, 확장자: {}", safeFilename, fileExt);

                builder.addBinaryBody(
                        "image",
                        request.getImage().getInputStream(),
                        ContentType.MULTIPART_FORM_DATA,
                        safeFilename // 안전한 파일 이름 사용
                );
            }

            HttpEntity multipart = builder.build();
            uploadFile.setEntity(multipart);

            // 인코딩 헤더 추가
            uploadFile.setHeader("Accept-Charset", "UTF-8");

            try (CloseableHttpResponse response = httpClient.execute(uploadFile)) {
                HttpEntity responseEntity = response.getEntity();
                String responseString = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
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
                        .amount(dto.getAmount()) // amount 필드 저장
                        .recipe(recipe)
                        .build();
                ingredients.add(ingredient);
            }
        }
        recipe.setIngredients(ingredients);

        // 조리 단계 엔티티 생성 - cookingTimeSeconds 필드 처리 추가
        List<Instruction> instructions = new ArrayList<>();
        if (recipeResponse.getInstructions() != null) {
            for (InstructionDTO dto : recipeResponse.getInstructions()) {
                // 초 단위 시간 처리
                Integer cookingTimeSeconds = dto.getCookingTimeSeconds();
                if (cookingTimeSeconds == null) {
                    // 초 단위 값이 없는 경우 분 단위에서 변환
                    cookingTimeSeconds = dto.getCookingTime() * 60;
                }

                Instruction instruction = Instruction.builder()
                        .instruction(dto.getInstruction())
                        .cookingTime(dto.getCookingTime())
                        .cookingTimeSeconds(cookingTimeSeconds) // 초 단위 저장
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
     * 대체 재료 요청 처리 - LLM 기반 판단 적용
     */
    public Mono<RecipeGenerateResponse> substituteIngredient(SubstituteIngredientRequest request) {
        log.debug("대체 재료 요청: 원재료={}, 대체재료={}, 레시피={}, 레시피ID={}",
                request.getOriginalIngredient(), request.getSubstituteIngredient(),
                request.getRecipeName(), request.getRecipeId());

        // 기존 레시피 데이터 조회
        Recipe originalRecipe = null;
        if (request.getRecipeId() != null) {
            try {
                originalRecipe = recipeRepository.findById(request.getRecipeId())
                        .orElse(null);

                if (originalRecipe != null) {
                    log.info("기존 레시피 발견 - ID: {}, 이름: {}", originalRecipe.getId(), originalRecipe.getName());
                }
            } catch (Exception e) {
                log.warn("레시피 조회 중 오류: {}", e.getMessage());
            }
        }

        // 요청 본문 구성
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("ori", request.getOriginalIngredient());
        requestBody.put("sub", request.getSubstituteIngredient());
        requestBody.put("recipe", request.getRecipeName());

        // 기존 레시피 데이터 추가 (LLM이 대체 가능성을 더 정확히 판단할 수 있도록)
        if (originalRecipe != null) {
            Map<String, Object> originalRecipeData = new HashMap<>();

            // 기존 재료 정보
            List<Map<String, String>> ingredientsList = new ArrayList<>();
            if (originalRecipe.getIngredients() != null) {
                for (Ingredient ingredient : originalRecipe.getIngredients()) {
                    Map<String, String> ingredientMap = new HashMap<>();
                    ingredientMap.put("name", ingredient.getName());
                    ingredientMap.put("amount", ingredient.getAmount() != null ? ingredient.getAmount() : "적당량");
                    ingredientsList.add(ingredientMap);
                }
            }
            originalRecipeData.put("ingredients", ingredientsList);

            // 기존 조리법 정보
            List<Map<String, Object>> instructionsList = new ArrayList<>();
            if (originalRecipe.getInstructions() != null) {
                for (Instruction instruction : originalRecipe.getInstructions()) {
                    Map<String, Object> instructionMap = new HashMap<>();
                    instructionMap.put("instruction", instruction.getInstruction());
                    instructionMap.put("cookingTime", instruction.getCookingTime());
                    instructionMap.put("cookingTimeSeconds", instruction.getCookingTimeSeconds());
                    instructionMap.put("stepNumber", instructionsList.size() + 1);
                    instructionsList.add(instructionMap);
                }
            }
            originalRecipeData.put("instructions", instructionsList);

            requestBody.put("originalRecipe", originalRecipeData);
            log.debug("기존 레시피 데이터 포함됨 - 재료: {}개, 조리법: {}개",
                    ingredientsList.size(), instructionsList.size());
        }

        // 자동 수량 조정 옵션 포함
        requestBody.put("autoAdjustAmount", request.isAutoAdjustAmount());

        // 원본 레시피 포함 옵션 포함
        requestBody.put("includeOriginalRecipe", request.isIncludeOriginalRecipe());

        // RecipeUpdateService 사용 (final 변수로 전달)
        final Recipe finalOriginalRecipe = originalRecipe;

        return webClient.post()
                .uri(substituteEndpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .acceptCharset(StandardCharsets.UTF_8)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(RecipeGenerateResponse.class)
                .map(response -> {
                    if (response != null) {
                        log.debug("대체 재료 요청 응답: {}", response.getName());

                        // 대체 불가능 여부는 Flask 응답 그대로 사용 (LLM 판단 결과)
                        if (response.isSubstituteFailure()) {
                            log.info("대체 재료 사용 불가(LLM 판단): {} -> {}, 사유: {}",
                                    request.getOriginalIngredient(),
                                    request.getSubstituteIngredient(),
                                    response.getDescription());

                            return response;
                        }

                        try {
                            // 성공한 경우 기존 레시피 업데이트
                            if (finalOriginalRecipe != null) {
                                Recipe updatedRecipe = recipeUpdateService.updateExistingRecipe(
                                        finalOriginalRecipe,
                                        response,
                                        request.getOriginalIngredient(),
                                        request.getSubstituteIngredient()
                                );
                                response.setId(updatedRecipe.getId());
                                response.setUserId(updatedRecipe.getUser().getId());

                                log.info("기존 레시피 업데이트 완료 - ID: {}, 새 이름: {}",
                                        updatedRecipe.getId(), updatedRecipe.getName());
                            } else {
                                // 새 레시피로 저장
                                log.warn("기존 레시피를 찾을 수 없어 새 레시피로 저장");
                                // 여기에 새 레시피 저장 로직을 추가할 수 있음
                            }
                        } catch (Exception e) {
                            log.error("대체 레시피 저장 중 오류: {}", e.getMessage());
                        }
                    }
                    return response;
                })
                .doOnError(e -> log.error("대체 재료 요청 실패: {}", e.getMessage()));
    }

    // 별도 서비스 클래스로 분리 - 재료명 교체 로직 추가
    // RecipeUpdateService 클래스
    @Service
    @RequiredArgsConstructor
    @Slf4j
    public static class RecipeUpdateService {

        private final RecipeRepository recipeRepository;
        private final IngredientRepository ingredientRepository;
        private final InstructionRepository instructionRepository;

        /**
         * 기존 레시피를 대체 재료 응답으로 업데이트 (LLM 판단 결과 활용)
         */
        @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
        public Recipe updateExistingRecipe(Recipe originalRecipe, RecipeGenerateResponse response,
                                           String originalIngredient, String substituteIngredient) {
            try {
                log.info("레시피 업데이트 트랜잭션 시작 - 레시피 ID: {}, 재료 교체: {} -> {}",
                        originalRecipe.getId(), originalIngredient, substituteIngredient);

                // 레시피 기본 정보 업데이트
                originalRecipe.setName(response.getName());
                originalRecipe.setDescription(response.getDescription());

                // 기존 재료 삭제
                if (originalRecipe.getIngredients() != null && !originalRecipe.getIngredients().isEmpty()) {
                    List<Ingredient> ingredientsToDelete = new ArrayList<>(originalRecipe.getIngredients());
                    originalRecipe.getIngredients().clear();
                    ingredientRepository.deleteAll(ingredientsToDelete);
                    log.debug("기존 재료 {}개 삭제 완료", ingredientsToDelete.size());
                }

                // 새 재료 추가
                List<Ingredient> newIngredients = new ArrayList<>();
                if (response.getIngredients() != null) {
                    for (IngredientDTO dto : response.getIngredients()) {
                        Ingredient ingredient = Ingredient.builder()
                                .name(dto.getName())
                                .amount(dto.getAmount() != null ? dto.getAmount() : "적당량")
                                .recipe(originalRecipe)
                                .build();
                        newIngredients.add(ingredient);
                    }
                }
                originalRecipe.setIngredients(newIngredients);
                log.debug("새 재료 {}개 추가 완료", newIngredients.size());

                // 기존 조리법 삭제
                if (originalRecipe.getInstructions() != null && !originalRecipe.getInstructions().isEmpty()) {
                    List<Instruction> instructionsToDelete = new ArrayList<>(originalRecipe.getInstructions());
                    originalRecipe.getInstructions().clear();
                    instructionRepository.deleteAll(instructionsToDelete);
                    log.debug("기존 조리법 {}개 삭제 완료", instructionsToDelete.size());
                }

                // 새 조리법 추가 (LLM이 생성한 조리법 사용)
                List<Instruction> newInstructions = new ArrayList<>();
                if (response.getInstructions() != null) {
                    for (InstructionDTO dto : response.getInstructions()) {
                        Integer cookingTimeSeconds = dto.getCookingTimeSeconds();
                        if (cookingTimeSeconds == null) {
                            cookingTimeSeconds = dto.getCookingTime() * 60;
                        }

                        // LLM이 이미 재료명을 업데이트했으므로 추가적인 교체는 필요 없음
                        String instructionText = dto.getInstruction();

                        Instruction instruction = Instruction.builder()
                                .instruction(instructionText)
                                .cookingTime(dto.getCookingTime())
                                .cookingTimeSeconds(cookingTimeSeconds)
                                .recipe(originalRecipe)
                                .build();
                        newInstructions.add(instruction);
                    }
                }
                originalRecipe.setInstructions(newInstructions);
                log.debug("새 조리법 {}개 추가 완료 (LLM 생성)", newInstructions.size());

                // 업데이트된 레시피 저장
                Recipe savedRecipe = recipeRepository.save(originalRecipe);
                log.info("레시피 업데이트 완료 - ID: {}, 이름: {}", savedRecipe.getId(), savedRecipe.getName());

                return savedRecipe;

            } catch (Exception e) {
                log.error("레시피 업데이트 중 오류 발생: {}", e.getMessage(), e);
                throw new RuntimeException("레시피 업데이트 실패", e);
            }
        }

        /**
         * 조리법 텍스트에서 재료명 교체 (강화된 로직)
         */
        private String replaceIngredientInText(String instructionText, String originalIngredient, String substituteIngredient) {
            if (instructionText == null || instructionText.trim().isEmpty() ||
                    originalIngredient == null || substituteIngredient == null) {
                return instructionText;
            }

            String updatedText = instructionText;

            try {
                // 1. 정확히 일치하는 경우 (단어 경계 사용)
                String exactPattern = "(?i)\\b" + Pattern.quote(originalIngredient) + "\\b";
                updatedText = updatedText.replaceAll(exactPattern, substituteIngredient);

                // 2. 부분 일치 고려 (여러 변형 처리)
                // 예: "무염버터" -> "무염마가린"
                if (!originalIngredient.equals(substituteIngredient)) {
                    // 원재료가 다른 단어의 일부인 경우 처리
                    String partialPattern = "(?i)" + Pattern.quote(originalIngredient);

                    // 원재료가 포함되지만 대체재료는 포함되지 않은 경우에만 교체
                    if (updatedText.toLowerCase().contains(originalIngredient.toLowerCase()) &&
                            !updatedText.toLowerCase().contains(substituteIngredient.toLowerCase())) {
                        updatedText = Pattern.compile(partialPattern, Pattern.CASE_INSENSITIVE)
                                .matcher(updatedText)
                                .replaceAll(substituteIngredient);
                    }

                    // 다른 형태의 변형도 고려 (공백, 하이픈, 언더스코어 등이 제거된 형태)
                    String originalNoSpace = originalIngredient.replace(" ", "");
                    String originalNoHyphen = originalIngredient.replace("-", "");
                    String originalNoUnderscore = originalIngredient.replace("_", "");

                    String[] originalVariants = {
                            originalNoSpace,
                            originalNoHyphen,
                            originalNoUnderscore
                    };

                    for (String variant : originalVariants) {
                        if (!variant.equals(originalIngredient) &&
                                updatedText.toLowerCase().contains(variant.toLowerCase())) {
                            String variantPattern = "(?i)\\b" + Pattern.quote(variant) + "\\b";
                            updatedText = updatedText.replaceAll(variantPattern, substituteIngredient);
                        }
                    }

                    // 3. 복합어 처리 (예: "쇠고기국물" -> "닭고기국물")
                    // 원재료가 다른 단어의 접두사나 접미사로 사용되는 경우
                    if (originalIngredient.length() >= 2) {
                        // 2글자 이상인 경우에만 적용 (너무 짧은 단어는 위험함)
                        String compoundPattern = "(?i)([가-힣a-z]+)?" + Pattern.quote(originalIngredient) + "([가-힣a-z]+)?";

                        Pattern pattern = Pattern.compile(compoundPattern, Pattern.CASE_INSENSITIVE);
                        Matcher matcher = pattern.matcher(updatedText);

                        StringBuffer sb = new StringBuffer();
                        while (matcher.find()) {
                            String prefix = matcher.group(1) != null ? matcher.group(1) : "";
                            String suffix = matcher.group(2) != null ? matcher.group(2) : "";

                            // 접두사나 접미사가 있는 경우 (복합어)
                            if (!prefix.isEmpty() || !suffix.isEmpty()) {
                                matcher.appendReplacement(sb, prefix + substituteIngredient + suffix);
                            } else {
                                // 정확히 일치하는 경우는 이미 처리했으므로 여기서는 건너뜀
                                matcher.appendReplacement(sb, matcher.group(0));
                            }
                        }
                        matcher.appendTail(sb);
                        updatedText = sb.toString();
                    }
                }

                // 변경 로깅
                if (!updatedText.equals(instructionText)) {
                    log.debug("조리법 텍스트 교체됨: '{}' -> '{}'",
                            instructionText.substring(0, Math.min(50, instructionText.length())),
                            updatedText.substring(0, Math.min(50, updatedText.length())));
                }

            } catch (Exception e) {
                log.warn("조리법 텍스트 교체 중 오류 (원본 유지): {}", e.getMessage());
                return instructionText; // 오류 시 원본 반환
            }

            return updatedText;
        }
    }

    // RecipeUpdateService 주입
    private final RecipeUpdateService recipeUpdateService;

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
        int totalCookingTime = 0;
        int totalCookingTimeSeconds = 0;

        // 레시피의 지시사항을 단계별로 변환
        if (recipe.getInstructions() != null) {
            for (int i = 0; i < recipe.getInstructions().size(); i++) {
                Instruction instruction = recipe.getInstructions().get(i);

                // 초 단위 조리 시간 처리
                Integer cookingTimeSeconds = instruction.getCookingTimeSeconds();
                if (cookingTimeSeconds == null) {
                    // 초 단위 값이 없는 경우 분 단위에서 변환
                    cookingTimeSeconds = instruction.getCookingTime() * 60;
                }

                // 총 조리 시간 누적
                totalCookingTime += instruction.getCookingTime();
                totalCookingTimeSeconds += cookingTimeSeconds;

                instructions.add(InstructionDTO.builder()
                        .instruction(instruction.getInstruction())
                        .cookingTime(instruction.getCookingTime())
                        .cookingTimeSeconds(cookingTimeSeconds)
                        .stepNumber(i + 1)  // 단계 번호 추가
                        .build());
            }
        }

        // 레시피의 재료 정보 변환
        List<IngredientDTO> ingredients = recipe.getIngredients().stream()
                .map(ingredient -> IngredientDTO.builder()
                        .name(ingredient.getName())
                        .amount(ingredient.getAmount()) // 양 추가
                        .build())
                .collect(Collectors.toList());

        // 어시스턴스 응답 생성
        return RecipeAssistanceResponse.builder()
                .id(recipe.getId())
                .name(recipe.getName())
                .description(recipe.getDescription())
                .instructions(instructions)
                .ingredients(ingredients)
                .totalCookingTime(totalCookingTime)
                .totalCookingTimeSeconds(totalCookingTimeSeconds)
                .difficulty(estimateDifficulty(recipe)) // 난이도 추정
                .servings(estimateServings(recipe))     // 인분 수 추정
                .build();
    }

    // 레시피 난이도 추정
    private String estimateDifficulty(Recipe recipe) {
        if (recipe.getInstructions() == null) {
            return "보통";
        }

        int instructionCount = recipe.getInstructions().size();
        int ingredientCount = recipe.getIngredients() != null ? recipe.getIngredients().size() : 0;

        // 간단한 난이도 추정 로직
        if (instructionCount <= 3 && ingredientCount <= 5) {
            return "쉬움";
        } else if (instructionCount >= 7 || ingredientCount >= 10) {
            return "어려움";
        } else {
            return "보통";
        }
    }

    // 레시피 인분 수 추정
    private String estimateServings(Recipe recipe) {
        // 기본값으로 2인분 설정
        return "2인분";
    }
}