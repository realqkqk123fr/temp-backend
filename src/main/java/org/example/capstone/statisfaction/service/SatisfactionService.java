package org.example.capstone.statisfaction.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.capstone.global.exception.CustomException;
import org.example.capstone.global.exception.ErrorCode;
import org.example.capstone.recipe.domain.Recipe;
import org.example.capstone.recipe.repository.RecipeRepository;
import org.example.capstone.statisfaction.domain.Satisfaction;
import org.example.capstone.statisfaction.dto.SatisfactionRequest;
import org.example.capstone.statisfaction.repository.SatisfactionRepository;
import org.example.capstone.user.domain.User;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.example.capstone.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static org.example.capstone.global.exception.ErrorCode.USER_NOT_FOUND;


@Service
@Slf4j
@RequiredArgsConstructor
public class SatisfactionService {

    private final SatisfactionRepository satisfactionRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;


    public void saveSatisafction(Long recipeId, CustomUserDetails userDetails, SatisfactionRequest satisfactionRequest){
        log.debug("만족도 평가 저장: {}", satisfactionRequest);

        // 레시피 조회
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPE_NOT_FOUND));

        //유저 조회
        Long userId = userDetails.getUserId();
        Optional<User> user = userRepository.findById(userId);
        if(user.isEmpty()){
            throw new CustomException(USER_NOT_FOUND);
        }

        // 만족도 평가 저장
        Satisfaction satisfaction = Satisfaction.builder()
                .user(user.get())
                .recipe(recipe)
                .rate(satisfactionRequest.getRate())
                .comment(satisfactionRequest.getComment())
                .build();
         satisfactionRepository.save(satisfaction);

    }
}
