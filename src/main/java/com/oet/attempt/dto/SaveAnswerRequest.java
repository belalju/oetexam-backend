package com.oet.attempt.dto;

import jakarta.validation.constraints.NotNull;

public record SaveAnswerRequest(
        @NotNull(message = "Question ID is required")
        Long questionId,

        Long selectedOptionId,
        String answerText
) {}
