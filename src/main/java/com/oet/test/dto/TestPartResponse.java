package com.oet.test.dto;

import com.oet.test.enums.PartLabel;

import java.util.List;

public record TestPartResponse(
        Long id,
        PartLabel partLabel,
        Integer timeLimitMinutes,
        String instructions,
        Integer sortOrder,
        List<TextPassageResponse> passages,
        List<QuestionGroupResponse> questionGroups
) {}
