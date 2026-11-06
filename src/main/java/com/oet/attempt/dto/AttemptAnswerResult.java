package com.oet.attempt.dto;

import com.oet.test.enums.PartLabel;

public record AttemptAnswerResult(
        Long questionId,
        PartLabel partLabel,
        Integer questionNumber,
        String questionText,
        String prefixText,
        String suffixText,
        Long selectedOptionId,
        Character selectedOptionLabel,
        String answerText,
        Long correctOptionId,
        Character optionLabel,
        String optionText,
        String correctText,
        boolean correct
) {}
