package com.oet.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oet.payment.entity.GatewayEventType;
import com.oet.payment.entity.PaymentGatewayResponse;
import com.oet.payment.repository.PaymentGatewayResponseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Stores every SSLCommerz exchange as JSON for auditing and dispute handling: our requests
 * (without store credentials) with their responses, and the payloads the gateway posts to us.
 * Recording is best-effort: a storage failure is logged and never breaks the payment flow.
 * Callers run outside a DB transaction, so each record commits on its own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayResponseRecorder {

    private static final String TRANSACTION_ID_FIELD = "tran_id";
    private static final String RAW_BODY_FIELD = "raw";
    private static final int TRANSACTION_ID_MAX_LENGTH = 30;
    private static final Set<String> CREDENTIAL_FIELDS = Set.of("store_id", "store_passwd");

    private final PaymentGatewayResponseRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Records a form payload the gateway posted to us (IPN or redirect callback).
     */
    public void recordPayload(GatewayEventType eventType, Map<String, String> payload) {
        save(eventType, payload.get(TRANSACTION_ID_FIELD), null, objectMapper.valueToTree(payload));
    }

    /**
     * Records a request we sent and its raw response body (null or blank when none came back).
     * A body that is not a JSON object is wrapped as {"raw": body}. The tran_id in the response
     * wins over the one in the request, since the session-create response carries none.
     */
    public void recordExchange(GatewayEventType eventType, Map<String, String> request, String responseBody) {
        JsonNode response = StringUtils.hasText(responseBody) ? toJson(responseBody) : null;
        String transactionId = response == null ? "" : response.path(TRANSACTION_ID_FIELD).asText("");
        if (!StringUtils.hasText(transactionId)) {
            transactionId = request.get(TRANSACTION_ID_FIELD);
        }
        save(eventType, transactionId, objectMapper.valueToTree(withoutCredentials(request)), response);
    }

    private void save(GatewayEventType eventType, String transactionId, JsonNode request, JsonNode response) {
        try {
            repository.save(PaymentGatewayResponse.builder()
                    .eventType(eventType)
                    .transactionId(truncate(transactionId))
                    .requestPayload(request)
                    .payload(response)
                    .build());
        } catch (DataAccessException ex) {
            log.error("Failed to store SSLCommerz {} response: tran_id={}", eventType, transactionId, ex);
        }
    }

    private Map<String, String> withoutCredentials(Map<String, String> request) {
        Map<String, String> sanitized = new HashMap<>(request);
        sanitized.keySet().removeAll(CREDENTIAL_FIELDS);
        return sanitized;
    }

    private JsonNode toJson(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            if (json != null && json.isObject()) {
                return json;
            }
        } catch (JsonProcessingException ex) {
            log.debug("SSLCommerz response is not JSON, storing it as raw text");
        }
        return objectMapper.createObjectNode().put(RAW_BODY_FIELD, body);
    }

    // tran_id on public callbacks is caller-controlled; keep it within the column size.
    private String truncate(String transactionId) {
        if (transactionId == null || transactionId.length() <= TRANSACTION_ID_MAX_LENGTH) {
            return transactionId;
        }
        return transactionId.substring(0, TRANSACTION_ID_MAX_LENGTH);
    }
}
