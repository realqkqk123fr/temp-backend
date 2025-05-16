package org.example.capstone.user.mypage.controller;


import lombok.RequiredArgsConstructor;
import org.example.capstone.user.login.dto.CustomUserDetails;
import org.example.capstone.user.mypage.dto.MypageRequest;
import org.example.capstone.user.mypage.dto.MypageResponse;
import org.example.capstone.user.mypage.service.MypageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MypageController {

    private final MypageService mypageService;

    @GetMapping("/api/mypage")
    public ResponseEntity<MypageResponse> getMyPage(@AuthenticationPrincipal CustomUserDetails userDetails){
        return ResponseEntity.ok(mypageService.getMypage(userDetails));
    }

    @PostMapping("/api/mypage")
    public ResponseEntity<MypageResponse> updateMyPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                       @RequestBody MypageRequest request){
        return ResponseEntity.ok(mypageService.updateMypage(userDetails, request));
    }
}
