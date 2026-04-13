package com.oet.test.controller;

import com.oet.common.util.ApiResponse;
import com.oet.test.dto.TestDetailResponse;
import com.oet.test.dto.TestSummaryResponse;
import com.oet.test.service.TestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tests — Applicant", description = "Test browsing for applicants")
@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TestController {

    private final TestService testService;

    @Operation(summary = "List all published tests")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<TestSummaryResponse>>> listPublishedTests(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(testService.listPublishedTests(pageable)));
    }

    @Operation(summary = "Get test structure (no answers)")
    @GetMapping("/{testId}/preview")
    public ResponseEntity<ApiResponse<TestDetailResponse>> previewTest(@PathVariable Long testId) {
        return ResponseEntity.ok(ApiResponse.success(testService.getTestForApplicant(testId)));
    }
}
