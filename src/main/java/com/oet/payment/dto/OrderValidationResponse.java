package com.oet.payment.dto;

import com.oet.payment.entity.Payment;
import com.oet.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderValidationResponse(
        String transactionId,
        String validationId,
        String packageId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String bankTransactionId,
        String cardType,
        Integer riskLevel,
        String riskTitle,
        LocalDateTime validatedAt
) {
    public static OrderValidationResponse from(Payment payment) {
        return new OrderValidationResponse(
                payment.getTransactionId(),
                payment.getValidationId(),
                payment.getPricingPackage() == null ? null : payment.getPricingPackage().getCode(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getBankTransactionId(),
                payment.getCardType(),
                payment.getRiskLevel(),
                payment.getRiskTitle(),
                payment.getValidatedAt()
        );
    }
}
