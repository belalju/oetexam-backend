package com.oet.test.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TextPassageRequest(
        @NotBlank(message = "Label is required")
        @Size(max = 50)
        String label,

        String content,
        String audioFileUrl,
        Integer audioDurationSeconds,
        Integer sortOrder
) {}
