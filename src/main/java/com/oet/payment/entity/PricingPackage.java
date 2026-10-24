package com.oet.payment.entity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

/**
 * Server-side price list. Must mirror PACKAGES in the frontend
 * (src/app/main/models/package.ts); checkout rejects any amount that differs.
 */
public enum PricingPackage {
    BRONZE("bronze", "Bronze Package", new BigDecimal("24.99"), "USD"),
    SILVER("silver", "Silver Package", new BigDecimal("39.99"), "USD"),
    GOLD("gold", "Gold Package", new BigDecimal("54.99"), "USD");

    public static final String PRODUCT_CATEGORY = "OET Practice";

    private final String code;
    private final String displayName;
    private final BigDecimal price;
    private final String currency;

    PricingPackage(String code, String displayName, BigDecimal price, String currency) {
        this.code = code;
        this.displayName = displayName;
        this.price = price;
        this.currency = currency;
    }

    public static Optional<PricingPackage> fromCode(String code) {
        return Arrays.stream(values())
                .filter(pricingPackage -> pricingPackage.code.equalsIgnoreCase(code))
                .findFirst();
    }

    /** Compares by value so 24.99 and 24.990 are treated as equal. */
    public boolean matches(BigDecimal amount, String currencyCode) {
        return amount != null
                && price.compareTo(amount) == 0
                && currency.equalsIgnoreCase(currencyCode);
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }
}
