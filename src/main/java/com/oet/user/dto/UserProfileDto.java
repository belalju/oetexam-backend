package com.oet.user.dto;

import com.oet.user.entity.UserRole;

import java.time.LocalDateTime;

public record UserProfileDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        String profession,
        boolean active,
        LocalDateTime createdAt
) {}
