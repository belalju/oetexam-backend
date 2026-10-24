package com.oet.payment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePaymentSessionRequest(
        @NotBlank(message = "Package is required")
        @Size(max = 20, message = "Package must not exceed 20 characters")
        String packageId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "10.00", message = "Amount must be at least 10.00")
        @DecimalMax(value = "500000.00", message = "Amount must not exceed 500000.00")
        @Digits(integer = 8, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount,

        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
        String currency,

        @NotBlank(message = "Customer name is required")
        @Size(max = 50, message = "Customer name must not exceed 50 characters")
        String customerName,

        @NotBlank(message = "Customer phone is required")
        @Size(max = 20, message = "Customer phone must not exceed 20 characters")
        String customerPhone,

        @NotBlank(message = "Customer address is required")
        @Size(max = 50, message = "Customer address must not exceed 50 characters")
        String customerAddress,

        @NotBlank(message = "Customer city is required")
        @Size(max = 50, message = "Customer city must not exceed 50 characters")
        String customerCity,

        @NotBlank(message = "Customer postcode is required")
        @Size(max = 30, message = "Customer postcode must not exceed 30 characters")
        String customerPostcode,

        @NotBlank(message = "Customer country is required")
        @Size(max = 50, message = "Customer country must not exceed 50 characters")
        String customerCountry
) {}
