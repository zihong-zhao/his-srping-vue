package com.his.auth;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record LoginResponse(
            String token,
            Long userId,
            String username,
            String displayName,
            String role,
            LocalDateTime expiresAt) {
    }
}
