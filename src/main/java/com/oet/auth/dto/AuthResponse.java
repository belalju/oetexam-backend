package com.oet.auth.dto;

import com.oet.user.entity.UserRole;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserRole role,
        String email,
        String firstName,
        String lastName
) {}
