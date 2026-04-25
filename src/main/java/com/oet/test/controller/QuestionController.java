package com.oet.test.controller;

import com.oet.common.util.ApiResponse;
import com.oet.test.dto.QuestionCreateRequest;
import com.oet.test.dto.QuestionResponse;
import com.oet.test.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin — Questions", description = "Question management for admins")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class QuestionController {

    private final QuestionService questionService;

    @Operation(summary = "Add question to group")
    @PostMapping("/question-groups/{groupId}/questions")
    public ResponseEntity<ApiResponse<QuestionResponse>> addQuestion(
            @PathVariable Long groupId,
            @Valid @RequestBody QuestionCreateRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(questionService.createQuestion(groupId, request)));
    }

    @Operation(summary = "List questions by group")
    @GetMapping("/question-groups/{groupId}/questions")
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> listQuestions(@PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.success(questionService.getQuestionsByGroupId(groupId)));
    }

    @Operation(summary = "Get question by ID")
    @GetMapping("/questions/{questionId}")
    public ResponseEntity<ApiResponse<QuestionResponse>> getQuestion(@PathVariable Long questionId) {
        return ResponseEntity.ok(ApiResponse.success(questionService.getQuestionById(questionId)));
    }

    @Operation(summary = "Update question")
    @PutMapping("/questions/{questionId}")
    public ResponseEntity<ApiResponse<QuestionResponse>> updateQuestion(
            @PathVariable Long questionId,
            @Valid @RequestBody QuestionCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(questionService.updateQuestion(questionId, request)));
    }

    @Operation(summary = "Delete question")
    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long questionId) {
        questionService.deleteQuestion(questionId);
        return ResponseEntity.noContent().build();
    }
}
