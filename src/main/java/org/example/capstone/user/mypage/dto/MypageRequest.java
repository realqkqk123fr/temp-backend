package org.example.capstone.user.mypage.dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MypageRequest {

    private String username;
    private String password;
    private int age;
    private int height;
    private int weight;
    private String habit;
    private String preference;


}
