package com.oet.payment.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oet.payment.config.SslCommerzProperties;
import com.oet.payment.dto.SslCommerzSessionQueryResponse;
import com.oet.payment.dto.SslCommerzSessionResponse;
import com.oet.payment.dto.SslCommerzValidationResponse;
import com.oet.payment.entity.GatewayEventType;
import com.oet.payment.exception.PaymentGatewayException;
import com.oet.payment.service.GatewayResponseRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Slf4j
@Component
public class SslCommerzClient {

    private static final String CREATE_SESSION_PATH = "/gwprocess/v4/api.php";
    private static final String QUERY_SESSION_PATH = "/validator/api/merchantTransIDvalidationAPI.php";
    private static final String VALIDATION_PATH = "/validator/api/validationserverAPI.php";

    private final RestClient restClient;
    private final SslCommerzProperties properties;
    private final ObjectMapper objectMapper;
    private final GatewayResponseRecorder responseRecorder;

    public SslCommerzClient(@Qualifier("sslCommerzRestClient") RestClient restClient,
                            SslCommerzProperties properties,
                            ObjectMapper objectMapper,
                            GatewayResponseRecorder responseRecorder) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.responseRecorder = responseRecorder;
    }

    /**
     * Calls the "Create and Get Session" API. Store credentials are added here.
     */
    public SslCommerzSessionResponse createSession(MultiValueMap<String, String> sessionParams) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>(sessionParams);
        form.add("store_id", properties.storeId());
        form.add("store_passwd", properties.storePassword());

        String body = execute(GatewayEventType.SESSION_CREATE, sessionParams.toSingleValueMap(), () -> restClient.post()
                .uri(CREATE_SESSION_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class));
        return parse(body, SslCommerzSessionResponse.class);
    }

    /**
     * Calls the "Transaction Query by Session ID" API.
     */
    public SslCommerzSessionQueryResponse querySession(String sessionKey) {
        Map<String, String> request = Map.of("sessionkey", sessionKey, "format", "json");
        String body = execute(GatewayEventType.SESSION_QUERY, request, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(QUERY_SESSION_PATH)
                        .queryParam("sessionkey", sessionKey)
                        .queryParam("store_id", properties.storeId())
                        .queryParam("store_passwd", properties.storePassword())
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .body(String.class));
        return parse(body, SslCommerzSessionQueryResponse.class);
    }

    /**
     * Calls the "Order Validation" API for the val_id received in an IPN.
     */
    public SslCommerzValidationResponse validate(String validationId) {
        Map<String, String> request = Map.of("val_id", validationId, "format", "json");
        String body = execute(GatewayEventType.ORDER_VALIDATION, request, () -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(VALIDATION_PATH)
                        .queryParam("val_id", validationId)
                        .queryParam("store_id", properties.storeId())
                        .queryParam("store_passwd", properties.storePassword())
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .body(String.class));
        return parse(body, SslCommerzValidationResponse.class);
    }

    /**
     * Runs the call and records the request with whatever came back — including error bodies,
     * and with no response at all when the gateway could not be reached. The request map must
     * not contain store credentials; the recorder strips them as a second safeguard.
     */
    private String execute(GatewayEventType eventType, Map<String, String> request, GatewayCall call) {
        String body = null;
        try {
            body = call.run();
        } catch (RestClientResponseException ex) {
            log.error("SSLCommerz request failed with HTTP {}", ex.getStatusCode().value());
            body = ex.getResponseBodyAsString();
            throw new PaymentGatewayException("Payment gateway is unavailable", ex);
        } catch (RestClientException ex) {
            log.error("SSLCommerz request failed: {}", ex.getMessage());
            throw new PaymentGatewayException("Payment gateway is unavailable", ex);
        } finally {
            responseRecorder.recordExchange(eventType, request, body);
        }
        if (body == null || body.isBlank()) {
            throw new PaymentGatewayException("Empty response from payment gateway");
        }
        return body;
    }

    // SSLCommerz may answer with a text/html content type, so the JSON body is parsed manually.
    private <T> T parse(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (JsonProcessingException ex) {
            log.error("Unparseable SSLCommerz response for {}", type.getSimpleName());
            throw new PaymentGatewayException("Invalid response from payment gateway", ex);
        }
    }

    @FunctionalInterface
    private interface GatewayCall {
        String run();
    }
}
