package com.oet.test.dto;

import java.util.List;

public record QuestionResponse(
        Long id,
        Integer questionNumber,
        String questionText,
        String prefixText,
        String suffixText,
        Integer sortOrder,
        List<QuestionOptionResponse> options,
        CorrectAnswerResponse correctAnswer  // null for applicants
) {}
