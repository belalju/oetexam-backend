package com.oet.report.dto;

import com.oet.attempt.entity.AttemptStatus;
import com.oet.test.enums.SubTestType;

import java.time.LocalDateTime;
import java.util.List;

public record UserScoreReport(
        Long userId,
        String fullName,
        String email,
        List<AttemptSummary> attempts
) {
    public record AttemptSummary(
            Long attemptId,
            Long testId,
            String testTitle,
            SubTestType subTestType,
            AttemptStatus status,
            Integer totalScore,
            Integer maxScore,
            Double percentage,
            Integer timeSpentSeconds,
            LocalDateTime startedAt,
            LocalDateTime completedAt
    ) {}
}
