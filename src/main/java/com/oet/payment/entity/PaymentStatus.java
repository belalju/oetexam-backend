package com.oet.payment.entity;

public enum PaymentStatus {
    PENDING,
    VALID,
    /** Paid and validated, but flagged high risk by SSLCommerz — service must be held. */
    ON_HOLD,
    FAILED,
    CANCELLED,
    EXPIRED,
    UNATTEMPTED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
