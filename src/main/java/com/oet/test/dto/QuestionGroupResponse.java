package com.oet.test.dto;

import com.oet.test.enums.QuestionType;

import java.util.List;

public record QuestionGroupResponse(
        Long id,
        Long passageId,
        String title,
        String instructions,
        QuestionType questionType,
        Integer sortOrder,
        List<QuestionResponse> questions
) {}
