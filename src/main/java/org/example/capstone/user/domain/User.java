package org.example.capstone.user.domain;


import jakarta.persistence.*;
import lombok.*;
import org.example.capstone.user.mypage.dto.MypageRequest;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;        //이름

    @Column(nullable = false, unique = true)
    private String email;       //이메일

    @Column(nullable = false)
    private String password;        //비밀번호

    private int age;        //나이

    private int height;     //키

    private int weight;     //몸무게

    private String habit;    // 식습관

    private String preference;       //선호도


    public User updateUser(MypageRequest request){
        this.username = request.getUsername();
        this.password = request.getPassword();
        this.age = request.getAge();
        this.height = request.getHeight();
        this.weight = request.getWeight();
        this.habit = request.getHabit();
        this.preference = request.getPreference();
        return this;
    }

}
