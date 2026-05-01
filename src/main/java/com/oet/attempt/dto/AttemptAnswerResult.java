package com.oet.attempt.dto;

public record AttemptAnswerResult(
        Long questionId,
        Integer questionNumber,
        String questionText,
        Long selectedOptionId,
        Character selectedOptionLabel,
        String answerText,
        Long correctOptionId,
        Character optionLabel,
        String correctText,
        boolean correct
) {}
