package com.oet.payment.dto;

import com.oet.payment.entity.PricingPackage;

import java.math.BigDecimal;

/**
 * Public view of a package. {@code id} is the package code (e.g. "gold"), which checkout
 * expects as {@code packageId}; it matches the shape of the frontend PricingPackage model.
 */
public record PricingPackageResponse(
        String id,
        String name,
        String description,
        BigDecimal price,
        BigDecimal originalPrice,
        String currency,
        Integer noOfAllowedTest,
        Integer accessDays,
        boolean popular
) {
    public static PricingPackageResponse from(PricingPackage pricingPackage) {
        return new PricingPackageResponse(
                pricingPackage.getCode(),
                pricingPackage.getName(),
                pricingPackage.getDescription(),
                pricingPackage.getPrice(),
                pricingPackage.getOriginalPrice(),
                pricingPackage.getCurrency(),
                pricingPackage.getNoOfAllowedTest(),
                pricingPackage.getAccessDays(),
                pricingPackage.isPopular()
        );
    }
}
