package com.oet.test.controller;

import com.oet.common.util.ApiResponse;
import com.oet.test.dto.TestPartRequest;
import com.oet.test.dto.TestPartResponse;
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

@Tag(name = "Admin — Test Parts", description = "Test part management for admins")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class TestPartController {

    private final TestService testService;

    @Operation(summary = "Add part to test")
    @PostMapping("/tests/{testId}/parts")
    public ResponseEntity<ApiResponse<TestPartResponse>> addPart(
            @PathVariable Long testId,
            @Valid @RequestBody TestPartRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(testService.addPart(testId, request)));
    }

    @Operation(summary = "List parts by test")
    @GetMapping("/tests/{testId}/parts")
    public ResponseEntity<ApiResponse<List<TestPartResponse>>> listParts(@PathVariable Long testId) {
        return ResponseEntity.ok(ApiResponse.success(testService.getTestPartList(testId)));
    }

    @Operation(summary = "Get part by ID")
    @GetMapping("/parts/{partId}")
    public ResponseEntity<ApiResponse<TestPartResponse>> getPart(@PathVariable Long partId) {
        return ResponseEntity.ok(ApiResponse.success(testService.getPartById(partId)));
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
}
