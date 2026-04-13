package com.oet.attempt.dto;

import com.oet.attempt.entity.AttemptStatus;
import com.oet.test.enums.SubTestType;

import java.time.LocalDateTime;

public record AttemptHistoryResponse(
        Long attemptId,
        Long testId,
        String testTitle,
        SubTestType subTestType,
        AttemptStatus status,
        Integer totalScore,
        Integer maxScore,
        Double percentage,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {}
