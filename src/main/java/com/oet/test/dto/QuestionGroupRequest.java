package com.oet.test.dto;

import com.oet.test.enums.QuestionType;
import jakarta.validation.constraints.NotNull;

public record QuestionGroupRequest(
        Long passageId,
        String title,
        String instructions,

        @NotNull(message = "Question type is required")
        QuestionType questionType,

        Integer sortOrder
) {}
