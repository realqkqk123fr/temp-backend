package org.example.capstone.user.mypage.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.capstone.user.domain.User;


@Getter
@Setter
public class MypageResponse {

    private String username;
    private String email;
    private String password;
    private int age;
    private int height;
    private int weight;
    private String habit;
    private String preference;

    public MypageResponse(User user) {
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.age = user.getAge();
        this.height = user.getHeight();
        this.weight = user.getWeight();
        this.habit = user.getHabit();
        this.preference = user.getPreference();
    }
}
