package org.example.capstone.user.login.dto;

import lombok.RequiredArgsConstructor;
import org.example.capstone.user.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final User user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    public Long getUserId() {
        // ID가 null인지 명시적으로 검사하고 로깅
        Long id = user.getId();
        if (id == null) {
            System.out.println("WARNING: User ID is null for user: " + user.getUsername());
        }
        return id;
    }

    public String getUserEmail() {
        return user.getEmail();
    }

    // UserDetails의 나머지 기본 메소드들도 구현해야 함
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String toString() {
        return "CustomUserDetails{" +
                "userId=" + (user != null ? user.getId() : "null") +
                ", username=" + (user != null ? user.getUsername() : "null") +
                ", email=" + (user != null ? user.getEmail() : "null") +
                '}';
    }
}
