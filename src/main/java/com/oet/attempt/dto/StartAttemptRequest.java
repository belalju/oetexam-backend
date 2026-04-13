package com.oet.attempt.dto;

import jakarta.validation.constraints.NotNull;

public record StartAttemptRequest(
        @NotNull(message = "Test ID is required")
        Long testId
) {}
