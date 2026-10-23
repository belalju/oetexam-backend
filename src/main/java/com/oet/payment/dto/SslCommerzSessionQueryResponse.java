package com.oet.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Raw response of the SSLCommerz "Transaction Query by Session ID" API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SslCommerzSessionQueryResponse(
        @JsonProperty("APIConnect") String apiConnect,
        String status,
        @JsonProperty("sessionkey") String sessionKey,
        @JsonProperty("tran_date") String transactionDate,
        @JsonProperty("tran_id") String transactionId,
        @JsonProperty("val_id") String validationId,
        BigDecimal amount,
        String currency,
        @JsonProperty("bank_tran_id") String bankTransactionId,
        @JsonProperty("card_type") String cardType,
        @JsonProperty("card_brand") String cardBrand,
        @JsonProperty("value_a") String valueA,
        @JsonProperty("risk_level") Integer riskLevel,
        @JsonProperty("risk_title") String riskTitle
) {
    public boolean isConnected() {
        return "DONE".equalsIgnoreCase(apiConnect);
    }
}
