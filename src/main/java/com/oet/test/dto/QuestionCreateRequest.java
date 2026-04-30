package com.oet.test.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QuestionCreateRequest(
        @NotNull(message = "Question number is required")
        @Min(1)
        Integer questionNumber,

        String questionText,

        String prefixText,
        String suffixText,
        Integer sortOrder,

        @Valid
        List<QuestionOptionRequest> options,

        Character correctOptionLabel,
        String correctText,
        List<String> alternativeAnswers
) {}
