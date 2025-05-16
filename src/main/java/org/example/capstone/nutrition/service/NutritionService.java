package org.example.capstone.nutrition.service;




import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.global.exception.CustomException;
import org.example.capstone.nutrition.domain.Nutrition;
import org.example.capstone.nutrition.dto.NutritionDTO;
import org.example.capstone.nutrition.repository.NutritionRepository;
import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.recipe.repository.RecipeRepository;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.example.capstone.global.exception.ErrorCode.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class NutritionService {

    @Value("${flask.api.endpoints.nutrition}")
    private String nutritionEndpoint;

    private final WebClient webClient;

    private final NutritionRepository nutritionRepository;
    private final RecipeRepository recipeRepository;

    //플라스크에서 영양 정보를 받음
    private Mono<NutritionDTO> getNutritionFromFlask(Long recipeId){
        return webClient.get()
                .uri(nutritionEndpoint + "/{id}", recipeId)
                .retrieve()
                .bodyToMono(NutritionDTO.class)
                .doOnSuccess(nutrition -> log.debug("Flask API에서 영양 성분 정보 응답 성공"))
                .doOnError(e -> log.error("Flask API에서 영양 성분 정보 요청 실패: {}", e.getMessage()));
    }

    @Transactional
    public NutritionDTO fetchAndSaveNutritionFromFlask(Long recipeId, CustomUserDetails userDetails) {
        log.debug("Flask에서 영양 성분 정보 가져오기 시작: Recipe ID = {}", recipeId);

        // 레시피 조회
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new CustomException(RECIPE_NOT_FOUND));

        //레시피 유저 비교
        Long userId = userDetails.getUserId();
        if(!recipe.getUser().getId().equals(userId)){
            throw new CustomException(INVALID_USER);
        }

        // Flask API에서 영양 성분 정보 가져오기
        NutritionDTO nutritionDto = getNutritionFromFlask(recipeId).block();
        if (nutritionDto == null) {
            throw new CustomException(NUTRITION_NOT_FOUND);
        }


        Nutrition nutrition = Nutrition.builder()
                .recipe(recipe)
                .calories(nutritionDto.getCalories())
                .carbohydrate(nutritionDto.getCarbohydrate())
                .protein(nutritionDto.getProtein())
                .fat(nutritionDto.getFat())
                .sugar(nutritionDto.getSugar())
                .sodium(nutritionDto.getSodium())
                .saturatedFat(nutritionDto.getSaturatedFat())
                .transFat(nutritionDto.getTransFat())
                .cholesterol(nutritionDto.getCholesterol())
                .build();

        Nutrition savedNutritionInfo = nutritionRepository.save(nutrition);


        recipeRepository.save(recipe);

        log.info("Flask에서 가져온 영양 성분 정보 저장 완료: ID = {}", savedNutritionInfo.getId());
        return nutritionDto;
    }



}
