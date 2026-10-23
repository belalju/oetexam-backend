package com.oet.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Raw response of the SSLCommerz "Create and Get Session" API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SslCommerzSessionResponse(
        String status,
        @JsonProperty("failedreason") String failedReason,
        @JsonProperty("sessionkey") String sessionKey,
        @JsonProperty("GatewayPageURL") String gatewayPageUrl
) {
    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }
}
