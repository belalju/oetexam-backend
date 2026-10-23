package com.oet.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Raw response of the SSLCommerz "Order Validation" API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SslCommerzValidationResponse(
        String status,
        @JsonProperty("tran_id") String transactionId,
        @JsonProperty("val_id") String validationId,
        BigDecimal amount,
        String currency,
        @JsonProperty("currency_type") String currencyType,
        @JsonProperty("currency_amount") BigDecimal currencyAmount,
        @JsonProperty("bank_tran_id") String bankTransactionId,
        @JsonProperty("card_type") String cardType,
        @JsonProperty("risk_level") Integer riskLevel,
        @JsonProperty("risk_title") String riskTitle
) {
    /** VALIDATED means the transaction is valid but was already validated by an earlier call. */
    public boolean isValid() {
        return "VALID".equalsIgnoreCase(status) || "VALIDATED".equalsIgnoreCase(status);
    }

    public boolean isHighRisk() {
        return riskLevel != null && riskLevel == 1;
    }
}
