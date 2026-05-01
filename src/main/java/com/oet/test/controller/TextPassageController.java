package com.oet.test.controller;

import com.oet.common.util.ApiResponse;
import com.oet.test.dto.TextPassageRequest;
import com.oet.test.dto.TextPassageResponse;
import com.oet.test.service.TestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin — Text Passages", description = "Text passage management for admins")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class TextPassageController {

    private final TestService testService;

    @Operation(summary = "Add passage to part")
    @PostMapping("/parts/{partId}/passages")
    public ResponseEntity<ApiResponse<TextPassageResponse>> addPassage(
            @PathVariable Long partId,
            @Valid @RequestBody TextPassageRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(testService.addPassage(partId, request)));
    }

    @Operation(summary = "List passages by test")
    @GetMapping("/tests/{testId}/passages")
    public ResponseEntity<ApiResponse<List<TextPassageResponse>>> listPassages(@PathVariable Long testId) {
        return ResponseEntity.ok(ApiResponse.success(testService.getPassagesByTestId(testId)));
    }

    @Operation(summary = "Get passage by ID")
    @GetMapping("/passages/{passageId}")
    public ResponseEntity<ApiResponse<TextPassageResponse>> getPassage(@PathVariable Long passageId) {
        return ResponseEntity.ok(ApiResponse.success(testService.getPassageById(passageId)));
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
}
