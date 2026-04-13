package com.oet.test.dto;

public record QuestionOptionResponse(
        Long id,
        Character optionLabel,
        String optionText,
        Integer sortOrder
) {}
