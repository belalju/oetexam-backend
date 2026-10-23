package com.oet.payment.dto;

public record PaymentSessionResponse(
        String transactionId,
        String sessionKey,
        String gatewayPageUrl
) {}
