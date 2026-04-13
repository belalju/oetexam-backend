package com.oet.attempt.dto;

import com.oet.test.dto.TestDetailResponse;

import java.time.LocalDateTime;

public record StartAttemptResponse(
        Long attemptId,
        LocalDateTime startedAt,
        Integer timeLimitMinutes,
        TestDetailResponse test
) {}
