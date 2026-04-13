package com.oet.test.dto;

import com.oet.test.enums.SubTestType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TestCreateRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255)
        String title,

        String description,

        @NotNull(message = "Sub-test type is required")
        SubTestType subTestType,

        @NotNull(message = "Time limit is required")
        @Min(value = 1, message = "Time limit must be at least 1 minute")
        Integer totalTimeLimitMinutes
) {}
