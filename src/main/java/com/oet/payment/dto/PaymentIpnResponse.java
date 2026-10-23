package com.oet.payment.dto;

import com.oet.payment.entity.PaymentStatus;

public record PaymentIpnResponse(
        String transactionId,
        PaymentStatus status
) {}
