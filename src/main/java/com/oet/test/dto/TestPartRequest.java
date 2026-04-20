package com.oet.test.dto;

import com.oet.test.enums.PartLabel;
import jakarta.validation.constraints.NotNull;

public record TestPartRequest(
        @NotNull(message = "Part label is required")
        PartLabel partLabel,

        String title,
        Integer timeLimitMinutes,
        String instructions,
        Integer sortOrder
) {}
