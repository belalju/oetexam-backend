package com.oet.attempt.dto;

import com.oet.attempt.entity.AttemptStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AttemptResultResponse(
        Long attemptId,
        String testTitle,
        AttemptStatus status,
        Integer totalScore,
        Integer maxScore,
        Double percentage,
        Integer timeSpentSeconds,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        List<AttemptAnswerResult> answers
) {}
