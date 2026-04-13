package com.oet.report.dto;

import com.oet.test.enums.SubTestType;

public record TestAnalyticsResponse(
        Long testId,
        String testTitle,
        SubTestType subTestType,
        long totalAttempts,
        long completedAttempts,
        Double averageScore,
        Double averagePercentage,
        Integer maxScore
) {}
