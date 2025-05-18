package org.example.capstone.nutrition.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.nutrition.domain.Nutrition;
import org.example.capstone.nutrition.dto.NutritionDTO;
import org.example.capstone.nutrition.repository.NutritionRepository;
import org.example.capstone.recipe.domain.Ingredient;
import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.recipe.repository.RecipeRepository;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 영양 정보 처리 서비스의 단순 구현
 * 별도의 트랜잭션 관리 없이 필요한 작업만 수행
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NutritionService {

    @Value("${flask.api.endpoints.nutrition}")
    private String nutritionEndpoint;

    private final WebClient webClient;
    private final NutritionRepository nutritionRepository;
    private final RecipeRepository recipeRepository;

    @Autowired
    private NutritionPersistenceService persistenceService;

    /**
     * 영양 정보 조회 - 트랜잭션 없음
     */
    public NutritionDTO getNutritionByRecipeId(Long recipeId, CustomUserDetails userDetails) {
        try {
            log.info("영양 정보 조회 시작 - 레시피 ID: {}", recipeId);

            // 1. 레시피 존재 확인
            Optional<Recipe> recipeOpt = recipeRepository.findById(recipeId);
            if (recipeOpt.isEmpty()) {
                log.warn("레시피가 존재하지 않음: {}", recipeId);
                return createDefaultNutrition();
            }

            Recipe recipe = recipeOpt.get();

            // 2. 기존 영양 정보 조회
            Optional<Nutrition> existingNutrition = nutritionRepository.findByRecipeId(recipeId);

            // 3. 이미 있으면 반환
            if (existingNutrition.isPresent()) {
                log.info("기존 영양 정보 사용: {}", recipeId);
                return convertToDTO(existingNutrition.get());
            }

            // 4. 없으면 Flask API 호출
            log.info("Flask에 영양 정보 요청 - 레시피: {}", recipe.getName());
            NutritionDTO nutritionDto = callFlaskAPI(recipe);

            // 5. 결과 저장 (별도 서비스 사용)
            try {
                persistenceService.saveNutrition(recipeId, nutritionDto);
            } catch (Exception e) {
                log.error("영양 정보 저장 실패 (무시됨): {}", e.getMessage());
                // 저장 실패해도 계속 진행
            }

            // 6. 결과 반환
            return nutritionDto;

        } catch (Exception e) {
            log.error("영양 정보 처리 중 오류: {}", e.getMessage());
            return createDefaultNutrition();
        }
    }

    /**
     * Flask API 호출 - 트랜잭션 없음
     */
    private NutritionDTO callFlaskAPI(Recipe recipe) {
        try {
            // 재료 목록 및 레시피 이름 추출
            StringBuilder ingredients = new StringBuilder(recipe.getName());

            if (recipe.getIngredients() != null && !recipe.getIngredients().isEmpty()) {
                ingredients.append(", ");
                recipe.getIngredients().forEach(i -> ingredients.append(i.getName()).append(", "));
            }

            String ingredientsStr = ingredients.toString().replaceAll(", $", "");
            log.info("영양 정보 요청 재료: {}", ingredientsStr);

            // API 요청 구성
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("ingredients", ingredientsStr);

            // API 호출
            return webClient.post()
                    .uri(nutritionEndpoint)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(NutritionDTO.class)
                    .doOnSuccess(n -> log.info("영양 정보 응답 성공"))
                    .doOnError(e -> log.error("영양 정보 요청 실패: {}", e.getMessage()))
                    .onErrorReturn(createDefaultNutrition())
                    .block();

        } catch (Exception e) {
            log.error("Flask API 호출 오류: {}", e.getMessage());
            return createDefaultNutrition();
        }
    }

    /**
     * 영양 정보 엔티티를 DTO로 변환
     */
    private NutritionDTO convertToDTO(Nutrition nutrition) {
        return NutritionDTO.builder()
                .calories(nutrition.getCalories())
                .carbohydrate(nutrition.getCarbohydrate())
                .protein(nutrition.getProtein())
                .fat(nutrition.getFat())
                .sugar(nutrition.getSugar())
                .sodium(nutrition.getSodium())
                .saturatedFat(nutrition.getSaturatedFat())
                .transFat(nutrition.getTransFat())
                .cholesterol(nutrition.getCholesterol())
                .build();
    }

    /**
     * 기본 영양 정보 생성
     */
    private NutritionDTO createDefaultNutrition() {
        return NutritionDTO.builder()
                .calories(500.0)
                .carbohydrate(30.0)
                .protein(25.0)
                .fat(15.0)
                .sugar(5.0)
                .sodium(400.0)
                .saturatedFat(3.0)
                .transFat(0.0)
                .cholesterol(50.0)
                .build();
    }

    /**
     * 영양 정보 저장을 담당하는 내부 서비스
     */
    @Service
    @Slf4j
    @RequiredArgsConstructor
    public static class NutritionPersistenceService {

        private final NutritionRepository nutritionRepository;
        private final RecipeRepository recipeRepository;

        /**
         * 영양 정보 저장 - 별도의 독립 트랜잭션으로 실행
         */
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void saveNutrition(Long recipeId, NutritionDTO dto) {
            log.info("독립 트랜잭션으로 영양 정보 저장 시작: {}", recipeId);

            try {
                // 1. 이미 데이터가 있는지 다시 확인
                if (nutritionRepository.findByRecipeId(recipeId).isPresent()) {
                    log.info("이미 영양 정보가 있어 저장 취소: {}", recipeId);
                    return;
                }

                // 2. 레시피 엔티티 조회
                Recipe recipe = recipeRepository.findById(recipeId).orElse(null);
                if (recipe == null) {
                    log.warn("영양 정보 저장 취소 - 레시피 없음: {}", recipeId);
                    return;
                }

                // 3. 영양 정보 엔티티 생성 및 저장
                Nutrition nutrition = Nutrition.builder()
                        .recipe(recipe)
                        .calories(dto.getCalories())
                        .carbohydrate(dto.getCarbohydrate())
                        .protein(dto.getProtein())
                        .fat(dto.getFat())
                        .sugar(dto.getSugar())
                        .sodium(dto.getSodium())
                        .saturatedFat(dto.getSaturatedFat())
                        .transFat(dto.getTransFat())
                        .cholesterol(dto.getCholesterol())
                        .build();

                nutritionRepository.save(nutrition);
                log.info("영양 정보 저장 성공: {}", recipeId);

            } catch (Exception e) {
                log.error("영양 정보 저장 중 오류 발생: {}", e.getMessage());
                // 예외가 전파되어 트랜잭션이 롤백되더라도 호출자에게는 영향 없음
                throw e;
            }
        }
    }
}