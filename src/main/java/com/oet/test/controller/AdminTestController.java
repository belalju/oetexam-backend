package com.oet.test.controller;

import com.oet.common.util.ApiResponse;
import com.oet.test.dto.*;
import com.oet.test.service.QuestionService;
import com.oet.test.service.TestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Tests", description = "Test management for admins")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminTestController {

    private final TestService testService;
    private final QuestionService questionService;

    @Operation(summary = "Create test")
    @PostMapping("/tests")
    public ResponseEntity<ApiResponse<TestSummaryResponse>> createTest(
            @Valid @RequestBody TestCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(201)
                .body(ApiResponse.created(testService.createTest(request, userDetails.getUsername())));
    }

    @Operation(summary = "List all tests")
    @GetMapping("/tests")
    public ResponseEntity<ApiResponse<Page<TestSummaryResponse>>> listTests(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(testService.adminListTests(pageable)));
    }

    @Operation(summary = "Get full test detail with answers")
    @GetMapping("/tests/{testId}")
    public ResponseEntity<ApiResponse<TestDetailResponse>> getTestDetail(@PathVariable Long testId) {
        return ResponseEntity.ok(ApiResponse.success(testService.adminGetTestDetail(testId)));
    }

    @Operation(summary = "Update test metadata")
    @PutMapping("/tests/{testId}")
    public ResponseEntity<ApiResponse<TestSummaryResponse>> updateTest(
            @PathVariable Long testId,
            @Valid @RequestBody TestCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(testService.updateTest(testId, request)));
    }

    @Operation(summary = "Delete test")
    @DeleteMapping("/tests/{testId}")
    public ResponseEntity<Void> deleteTest(@PathVariable Long testId) {
        testService.deleteTest(testId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Publish test")
    @PutMapping("/tests/{testId}/publish")
    public ResponseEntity<ApiResponse<TestSummaryResponse>> publishTest(@PathVariable Long testId) {
        return ResponseEntity.ok(ApiResponse.success(testService.publishTest(testId)));
    }

    @Operation(summary = "Unpublish test")
    @PutMapping("/tests/{testId}/unpublish")
    public ResponseEntity<ApiResponse<TestSummaryResponse>> unpublishTest(@PathVariable Long testId) {
        return ResponseEntity.ok(ApiResponse.success(testService.unpublishTest(testId)));
    }

    @Operation(summary = "Add part to test")
    @PostMapping("/tests/{testId}/parts")
    public ResponseEntity<ApiResponse<TestPartResponse>> addPart(
            @PathVariable Long testId,
            @Valid @RequestBody TestPartRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(testService.addPart(testId, request)));
    }

    @Operation(summary = "Update part")
    @PutMapping("/parts/{partId}")
    public ResponseEntity<ApiResponse<TestPartResponse>> updatePart(
            @PathVariable Long partId,
            @Valid @RequestBody TestPartRequest request) {
        return ResponseEntity.ok(ApiResponse.success(testService.updatePart(partId, request)));
    }

    @Operation(summary = "Delete part")
    @DeleteMapping("/parts/{partId}")
    public ResponseEntity<Void> deletePart(@PathVariable Long partId) {
        testService.deletePart(partId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add passage to part")
    @PostMapping("/parts/{partId}/passages")
    public ResponseEntity<ApiResponse<TextPassageResponse>> addPassage(
            @PathVariable Long partId,
            @Valid @RequestBody TextPassageRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(testService.addPassage(partId, request)));
    }

    @Operation(summary = "Update passage")
    @PutMapping("/passages/{passageId}")
    public ResponseEntity<ApiResponse<TextPassageResponse>> updatePassage(
            @PathVariable Long passageId,
            @Valid @RequestBody TextPassageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(testService.updatePassage(passageId, request)));
    }

    @Operation(summary = "Delete passage")
    @DeleteMapping("/passages/{passageId}")
    public ResponseEntity<Void> deletePassage(@PathVariable Long passageId) {
        testService.deletePassage(passageId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add question group to part")
    @PostMapping("/parts/{partId}/question-groups")
    public ResponseEntity<ApiResponse<QuestionGroupResponse>> addQuestionGroup(
            @PathVariable Long partId,
            @Valid @RequestBody QuestionGroupRequest request) {
        return ResponseEntity.status(201)
                .body(ApiResponse.created(testService.addQuestionGroup(partId, request)));
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

    @Operation(summary = "Add question to group (with options and correct answer)")
    @PostMapping("/question-groups/{groupId}/questions")
    public ResponseEntity<ApiResponse<QuestionResponse>> addQuestion(
            @PathVariable Long groupId,
            @Valid @RequestBody QuestionCreateRequest request) {
        return ResponseEntity.status(201)
                .body(ApiResponse.created(questionService.createQuestion(groupId, request)));
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
