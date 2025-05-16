package org.example.capstone.recipe.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.global.exception.CustomException;
import org.example.capstone.global.exception.ErrorCode;
import org.example.capstone.recipe.domain.Ingredient;
import org.example.capstone.recipe.domain.Instruction;
import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.recipe.dto.IngredientDTO;
import org.example.capstone.recipe.dto.InstructionDTO;
import org.example.capstone.recipe.dto.RecipeResponse;
import org.example.capstone.recipe.dto.UserInfoDTO;
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

import java.util.ArrayList;
import java.util.List;

import static org.example.capstone.global.exception.ErrorCode.USER_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeService {

    @Value("${flask.api.endpoints.recipe}")
    private String recipeEndpoint;

    @Value("${flask.api.endpoints.chat}")
    private String chatEndpoint;

    private final WebClient webClient;

    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final InstructionRepository instructionRepository;
    private final SatisfactionRepository satisfactionRepository;


    private Mono<RecipeResponse> getRecipeFromFlask(Long recipeId) {
        log.debug("Flask API에서 레시피 정보 요청: ID = {}", recipeId);
        return webClient.get()
                .uri(recipeEndpoint + "/{id}", recipeId)
                .retrieve()
                .bodyToMono(RecipeResponse.class)
                .doOnSuccess(recipe -> log.debug("Flask API에서 레시피 정보 응답 성공"))
                .doOnError(e -> log.error("Flask API에서 레시피 정보 요청 실패: {}", e.getMessage()));
    }

    private List<Instruction> saveEachInstruction(List<InstructionDTO> instructionDTOs, Recipe recipe) {
        List<Instruction> instructions = new ArrayList<>();
        for (InstructionDTO dto : instructionDTOs) {
            Instruction instruction = Instruction.builder()
                    .instruction(dto.getInstruction())
                    .cookingTime(dto.getCookingTime())
                    .recipe(recipe)
                    .build();
            instructionRepository.save(instruction);
            instructions.add(instruction);
        }
        return instructions;
    }

    private List<Ingredient> saveEachIngridient(List<IngredientDTO> ingredientDTOS, Recipe recipe) {
        List<Ingredient> ingredients = new ArrayList<>();
        for (IngredientDTO dto : ingredientDTOS) {
            Ingredient ingredient = Ingredient.builder()
                    .name(dto.getName())
                    .recipe(recipe)
                    .build();
            ingredientRepository.save(ingredient);
            ingredients.add(ingredient);
        }
        return ingredients;
    }

    @Transactional
    public RecipeResponse fetchAndSaveRecipeFromFlask(Long recipeId, CustomUserDetails userDetails) {
        log.debug("Flask에서 레시피 정보 가져오기 시작: ID = {}", recipeId);

        // Flask API에서 레시피 정보 가져오기
        RecipeResponse recipeResponse = getRecipeFromFlask(recipeId).block();
        if (recipeResponse == null) {
            throw new CustomException(ErrorCode.RECIPE_NOT_FOUND);
        }
        //유저 확인
        User user = userRepository.findById(userDetails.getUserId()).orElseThrow(() -> new CustomException(USER_NOT_FOUND));

        Recipe recipe = Recipe.builder()
                .name(recipeResponse.getName())
                .description(recipeResponse.getDescription())
                .user(user)
                .build();

        // 어시스턴스 저장
        List<InstructionDTO> instructions = recipeResponse.getInstructions();
        List<Instruction> savedInstructions = saveEachInstruction(instructions, recipe);

        //재료 저장
        List<IngredientDTO> ingredients = recipeResponse.getIngredients();
        List<Ingredient> savedIngredients = saveEachIngridient(ingredients, recipe);

        recipe.setIngredients(savedIngredients);
        recipe.setInstructions(savedInstructions);

        Recipe savedRecipe = recipeRepository.save(recipe);

        log.info("Flask에서 가져온 레시피 저장 완료: ID = {}", savedRecipe.getId());
        return recipeResponse;
    }

    //유저의 만족도 검색
    private List<Satisfaction> findSatisfaction(List<Recipe> recipes){
        List<Satisfaction> satisfactions = new ArrayList<>();
        for (Recipe recipe : recipes) {
           satisfactions.add(satisfactionRepository.findByRecipe(recipe));
        }
        return satisfactions;
    }

    //플라스크로 유저정보 전송
    public Mono<Void> sendUserInfoToFlask(CustomUserDetails userDetails) {
        log.debug("Flask API로 유저 정보 전송: {}", userDetails);
        User user = userRepository.findById(userDetails.getUserId()).orElseThrow(()-> new CustomException(USER_NOT_FOUND));
        List<Recipe> recipes= recipeRepository.findByUser(user);
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
}
