package com.oet.test.dto;

import com.oet.test.enums.SubTestType;

import java.time.LocalDateTime;
import java.util.List;

public record TestDetailResponse(
        Long id,
        String title,
        String description,
        SubTestType subTestType,
        Integer totalTimeLimitMinutes,
        boolean published,
        String createdByName,
        LocalDateTime createdAt,
        List<TestPartResponse> parts
) {}
