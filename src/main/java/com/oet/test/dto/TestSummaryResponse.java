package com.oet.test.dto;

import com.oet.test.enums.SubTestType;

import java.time.LocalDateTime;

public record TestSummaryResponse(
        Long id,
        String title,
        String description,
        SubTestType subTestType,
        Integer totalTimeLimitMinutes,
        boolean published,
        String createdByName,
        LocalDateTime createdAt
) {}
