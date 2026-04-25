package com.oet.test.controller;

import com.oet.common.util.ApiResponse;
import com.oet.test.dto.TestCreateRequest;
import com.oet.test.dto.TestDetailResponse;
import com.oet.test.dto.TestSummaryResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin — Tests", description = "Test management for admins")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTestController {

    private final TestService testService;

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

    @Operation(summary = "Get test summary")
    @GetMapping("/tests/{testId}/summary")
    public ResponseEntity<ApiResponse<TestSummaryResponse>> getTestInformation(@PathVariable Long testId) {
        return ResponseEntity.ok(ApiResponse.success(testService.getTestInformation(testId)));
    }

    @Operation(summary = "Get full test detail with parts, passages and questions")
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
}
