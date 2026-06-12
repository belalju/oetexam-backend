package com.oet.attempt.dto;

public record AttemptAnswerResult(
        Long questionId,
        Integer questionNumber,
        String questionText,
        String prefixText,
        String suffixText,
        Long selectedOptionId,
        Character selectedOptionLabel,
        String answerText,
        Long correctOptionId,
        Character optionLabel,
        String correctText,
        boolean correct
) {}
