package com.oet.attempt.controller;

import com.oet.attempt.dto.*;
import com.oet.attempt.service.AttemptService;
import com.oet.common.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attempts")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<StartAttemptResponse>> startAttempt(
            @Valid @RequestBody StartAttemptRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        StartAttemptResponse response = attemptService.startAttempt(request.testId(), userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{attemptId}")
    public ResponseEntity<ApiResponse<StartAttemptResponse>> resumeAttempt(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal UserDetails userDetails) {
        StartAttemptResponse response = attemptService.resumeAttempt(attemptId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{attemptId}/answer")
    public ResponseEntity<ApiResponse<Void>> saveAnswer(
            @PathVariable Long attemptId,
            @Valid @RequestBody SaveAnswerRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        attemptService.saveAnswer(attemptId, request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{attemptId}/submit")
    public ResponseEntity<ApiResponse<SubmitAttemptResponse>> submitAttempt(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal UserDetails userDetails) {
        SubmitAttemptResponse response = attemptService.submitAttempt(attemptId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{attemptId}/results")
    public ResponseEntity<ApiResponse<AttemptResultResponse>> getResults(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal UserDetails userDetails) {
        AttemptResultResponse response = attemptService.getResults(attemptId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/my-history")
    public ResponseEntity<ApiResponse<Page<AttemptHistoryResponse>>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AttemptHistoryResponse> history = attemptService.getHistory(userDetails.getUsername(), pageable);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
