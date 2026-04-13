package com.oet.attempt.dto;

public record AttemptAnswerResult(
        Long questionId,
        Integer questionNumber,
        String questionText,
        Long selectedOptionId,
        String answerText,
        Long correctOptionId,
        String correctText,
        boolean correct
) {}
