package com.oet.test.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QuestionOptionRequest(
        @NotNull(message = "Option label is required")
        Character optionLabel,

        @NotBlank(message = "Option text is required")
        String optionText,

        Integer sortOrder
) {}
