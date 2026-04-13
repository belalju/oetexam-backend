package com.oet.test.dto;

public record TextPassageResponse(
        Long id,
        String label,
        String content,
        String audioFileUrl,
        Integer audioDurationSeconds,
        Integer sortOrder
) {}
