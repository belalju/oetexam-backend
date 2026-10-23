package com.oet.payment.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oet.payment.config.SslCommerzProperties;
import com.oet.payment.dto.SslCommerzSessionQueryResponse;
import com.oet.payment.dto.SslCommerzSessionResponse;
import com.oet.payment.dto.SslCommerzValidationResponse;
import com.oet.payment.exception.PaymentGatewayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class SslCommerzClient {

    private static final String CREATE_SESSION_PATH = "/gwprocess/v4/api.php";
    private static final String QUERY_SESSION_PATH = "/validator/api/merchantTransIDvalidationAPI.php";
    private static final String VALIDATION_PATH = "/validator/api/validationserverAPI.php";

    private final RestClient restClient;
    private final SslCommerzProperties properties;
    private final ObjectMapper objectMapper;

    public SslCommerzClient(@Qualifier("sslCommerzRestClient") RestClient restClient,
                            SslCommerzProperties properties,
                            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Calls the "Create and Get Session" API. Store credentials are added here.
     */
    public SslCommerzSessionResponse createSession(MultiValueMap<String, String> sessionParams) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>(sessionParams);
        form.add("store_id", properties.storeId());
        form.add("store_passwd", properties.storePassword());

        String body = execute(() -> restClient.post()
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
        String body = execute(() -> restClient.get()
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
        String body = execute(() -> restClient.get()
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

    private String execute(GatewayCall call) {
        try {
            String body = call.run();
            if (body == null || body.isBlank()) {
                throw new PaymentGatewayException("Empty response from payment gateway");
            }
            return body;
        } catch (RestClientException ex) {
            log.error("SSLCommerz request failed: {}", ex.getMessage());
            throw new PaymentGatewayException("Payment gateway is unavailable", ex);
        }
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
