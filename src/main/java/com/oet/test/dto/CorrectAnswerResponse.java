package com.oet.test.dto;

import java.util.List;

public record CorrectAnswerResponse(
        Long correctOptionId,
        Character optionLabel,
        String correctText,
        List<String> alternativeAnswers
) {}
