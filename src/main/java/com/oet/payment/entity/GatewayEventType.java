package com.oet.payment.entity;

/**
 * Which SSLCommerz interaction produced a stored gateway response.
 */
public enum GatewayEventType {
    SESSION_CREATE,
    SESSION_QUERY,
    ORDER_VALIDATION,
    IPN,
    SUCCESS_CALLBACK,
    FAIL_CALLBACK,
    CANCEL_CALLBACK
}
