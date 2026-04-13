package com.oet.report.controller;

import com.oet.common.util.ApiResponse;
import com.oet.report.dto.TestAnalyticsResponse;
import com.oet.report.dto.UserScoreReport;
import com.oet.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/test/{testId}")
//    @PreAuthorize("hasRole('ADMIN')") // temporarily disabled
    public ResponseEntity<ApiResponse<TestAnalyticsResponse>> getTestAnalytics(@PathVariable Long testId) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getTestAnalytics(testId)));
    }

    @GetMapping("/user/{userId}")
//    @PreAuthorize("hasRole('ADMIN')") // temporarily disabled
    public ResponseEntity<ApiResponse<UserScoreReport>> getUserReport(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getUserReport(userId)));
    }
}
