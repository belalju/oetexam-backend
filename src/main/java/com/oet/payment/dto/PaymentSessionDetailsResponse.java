package com.oet.payment.dto;

import java.math.BigDecimal;

public record PaymentSessionDetailsResponse(
        String sessionKey,
        String status,
        String transactionId,
        String validationId,
        String transactionDate,
        BigDecimal amount,
        String currency,
        String bankTransactionId,
        String cardType,
        String cardBrand,
        Integer riskLevel,
        String riskTitle
) {}
