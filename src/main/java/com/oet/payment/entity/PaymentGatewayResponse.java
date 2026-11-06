package com.oet.payment.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

/**
 * Audit record of an SSLCommerz exchange, stored as JSON.
 */
@Entity
@Table(name = "payment_gateway_responses", indexes = {
        @Index(name = "idx_gateway_responses_transaction", columnList = "transaction_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PaymentGatewayResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", length = 30)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private GatewayEventType eventType;

    /** Request we sent to SSLCommerz, without store credentials; null for IPN and callbacks. */
    @Type(JsonType.class)
    @Column(name = "request_payload", columnDefinition = "json")
    private JsonNode requestPayload;

    /** Response or posted payload from SSLCommerz; null when a request got no response. */
    @Type(JsonType.class)
    @Column(name = "payload", columnDefinition = "json")
    private JsonNode payload;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
