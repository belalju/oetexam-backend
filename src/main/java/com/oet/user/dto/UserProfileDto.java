package com.oet.user.dto;

import com.oet.user.entity.UserRole;

import java.time.LocalDateTime;

/**
 * The package fields describe the user's latest VALID payment and are null until the user has paid.
 */
public record UserProfileDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        String profession,
        boolean active,
        LocalDateTime createdAt,
        String packageCode,
        Integer noOfAllowedTest,
        LocalDateTime packageExpirationDate
) {}
