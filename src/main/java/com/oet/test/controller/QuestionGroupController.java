package com.oet.test.controller;

import com.oet.common.util.ApiResponse;
import com.oet.test.dto.QuestionGroupRequest;
import com.oet.test.dto.QuestionGroupResponse;
import com.oet.test.service.TestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin — Question Groups", description = "Question group management for admins")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class QuestionGroupController {

    private final TestService testService;

    @Operation(summary = "Add question group to part")
    @PostMapping("/parts/{partId}/question-groups")
    public ResponseEntity<ApiResponse<QuestionGroupResponse>> addQuestionGroup(
            @PathVariable Long partId,
            @Valid @RequestBody QuestionGroupRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(testService.addQuestionGroup(partId, request)));
    }

    @Operation(summary = "List question groups by test")
    @GetMapping("/tests/{testId}/question-groups")
    public ResponseEntity<ApiResponse<List<QuestionGroupResponse>>> listQuestionGroups(@PathVariable Long testId) {
        return ResponseEntity.ok(ApiResponse.success(testService.getQuestionGroupsByTestId(testId)));
    }

    @Operation(summary = "Get question group by ID")
    @GetMapping("/question-groups/{groupId}")
    public ResponseEntity<ApiResponse<QuestionGroupResponse>> getQuestionGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(testService.getQuestionGroupById(groupId)));
    }

    @Operation(summary = "Update question group")
    @PutMapping("/question-groups/{groupId}")
    public ResponseEntity<ApiResponse<QuestionGroupResponse>> updateQuestionGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody QuestionGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success(testService.updateQuestionGroup(groupId, request)));
    }

    @Operation(summary = "Delete question group")
    @DeleteMapping("/question-groups/{groupId}")
    public ResponseEntity<Void> deleteQuestionGroup(@PathVariable Long groupId) {
        testService.deleteQuestionGroup(groupId);
        return ResponseEntity.noContent().build();
    }
}
