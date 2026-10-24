package com.oet.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sent by the frontend after SSLCommerz redirects the customer to the success URL
 * with {@code tran_id} and {@code val_id}.
 */
public record OrderValidationRequest(
        @NotBlank(message = "Transaction ID is required")
        @Size(max = 30, message = "Transaction ID must be at most 30 characters")
        String transactionId,

        @NotBlank(message = "Validation ID is required")
        @Size(max = 50, message = "Validation ID must be at most 50 characters")
        String validationId
) {}
