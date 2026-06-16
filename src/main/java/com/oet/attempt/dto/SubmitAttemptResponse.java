package com.oet.attempt.dto;

import com.oet.attempt.entity.AttemptStatus;

import java.time.LocalDateTime;

public record SubmitAttemptResponse(
        Long attemptId,
        AttemptStatus status,
        Integer totalScore,
        Integer maxScore,
        String grade,
        Integer timeSpentSeconds,
        LocalDateTime completedAt
) {}
