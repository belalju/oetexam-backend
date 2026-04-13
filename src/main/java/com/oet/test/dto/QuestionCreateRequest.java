package com.oet.test.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record QuestionCreateRequest(
        @NotNull(message = "Question number is required")
        @Min(1)
        Integer questionNumber,

        @NotBlank(message = "Question text is required")
        String questionText,

        String prefixText,
        String suffixText,
        Integer sortOrder,

        @Valid
        List<QuestionOptionRequest> options,

        Long correctOptionId,
        String correctText,
        List<String> alternativeAnswers
) {}
