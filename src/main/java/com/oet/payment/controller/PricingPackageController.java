package com.oet.payment.controller;

import com.oet.common.util.ApiResponse;
import com.oet.payment.dto.PricingPackageResponse;
import com.oet.payment.service.PricingPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public price list for the pricing page, so the frontend no longer hardcodes packages.
 */
@RestController
@RequestMapping("/api/packages")
@RequiredArgsConstructor
public class PricingPackageController {

    private final PricingPackageService pricingPackageService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PricingPackageResponse>>> getPackages() {
        return ResponseEntity.ok(ApiResponse.success(pricingPackageService.getActivePackages()));
    }
}
