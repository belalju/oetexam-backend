package com.oet.payment.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.payment.sslcommerz")
public record SslCommerzProperties(
        @NotBlank String baseUrl,
        @NotBlank String storeId,
        @NotBlank String storePassword,
        @NotBlank String successUrl,
        @NotBlank String failUrl,
        @NotBlank String cancelUrl,
        String ipnUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout
) {
}
