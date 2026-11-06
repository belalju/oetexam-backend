package com.oet.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oet.payment.entity.GatewayEventType;
import com.oet.payment.entity.PaymentGatewayResponse;
import com.oet.payment.repository.PaymentGatewayResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayResponseRecorderTest {

    @Mock
    private PaymentGatewayResponseRepository repository;

    private GatewayResponseRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new GatewayResponseRecorder(repository, new ObjectMapper());
    }

    @Test
    void recordPayload_storesFormParamsAsJsonWithTransactionId() {
        recorder.recordPayload(GatewayEventType.IPN, Map.of("tran_id", "OETABC123", "status", "VALID"));

        PaymentGatewayResponse saved = captureSaved();
        assertThat(saved.getEventType()).isEqualTo(GatewayEventType.IPN);
        assertThat(saved.getTransactionId()).isEqualTo("OETABC123");
        assertThat(saved.getPayload().get("status").asText()).isEqualTo("VALID");
    }

    @Test
    void recordPayload_hasNoRequestPayload() {
        recorder.recordPayload(GatewayEventType.SUCCESS_CALLBACK, Map.of("tran_id", "OETABC123"));

        assertThat(captureSaved().getRequestPayload()).isNull();
    }

    @Test
    void recordExchange_prefersTransactionIdFromResponse() {
        recorder.recordExchange(GatewayEventType.ORDER_VALIDATION, Map.of("val_id", "VAL1"),
                "{\"status\":\"VALID\",\"tran_id\":\"OETABC123\"}");

        PaymentGatewayResponse saved = captureSaved();
        assertThat(saved.getTransactionId()).isEqualTo("OETABC123");
        assertThat(saved.getRequestPayload().get("val_id").asText()).isEqualTo("VAL1");
    }

    @Test
    void recordExchange_usesRequestTransactionIdWhenResponseHasNone() {
        recorder.recordExchange(GatewayEventType.SESSION_CREATE, Map.of("tran_id", "OETABC123"),
                "{\"status\":\"SUCCESS\",\"sessionkey\":\"KEY\"}");

        PaymentGatewayResponse saved = captureSaved();
        assertThat(saved.getTransactionId()).isEqualTo("OETABC123");
        assertThat(saved.getPayload().get("sessionkey").asText()).isEqualTo("KEY");
    }

    @Test
    void recordExchange_stripsStoreCredentialsFromRequest() {
        recorder.recordExchange(GatewayEventType.SESSION_CREATE,
                Map.of("tran_id", "OETABC123", "store_id", "store", "store_passwd", "secret"), "{}");

        JsonNode request = captureSaved().getRequestPayload();
        assertThat(request.has("store_id")).isFalse();
        assertThat(request.has("store_passwd")).isFalse();
        assertThat(request.get("tran_id").asText()).isEqualTo("OETABC123");
    }

    @Test
    void recordExchange_keepsRequestWhenNoResponse() {
        recorder.recordExchange(GatewayEventType.SESSION_CREATE, Map.of("tran_id", "OETABC123"), null);

        PaymentGatewayResponse saved = captureSaved();
        assertThat(saved.getPayload()).isNull();
        assertThat(saved.getRequestPayload().get("tran_id").asText()).isEqualTo("OETABC123");
    }

    @Test
    void recordExchange_wrapsNonJsonResponse() {
        recorder.recordExchange(GatewayEventType.SESSION_QUERY, Map.of("sessionkey", "KEY"), "<html>error</html>");

        assertThat(captureSaved().getPayload().get("raw").asText()).isEqualTo("<html>error</html>");
    }

    @Test
    void recordPayload_truncatesOverlongTransactionId() {
        recorder.recordPayload(GatewayEventType.FAIL_CALLBACK, Map.of("tran_id", "X".repeat(40)));

        assertThat(captureSaved().getTransactionId()).hasSize(30);
    }

    @Test
    void storageFailure_doesNotPropagate() {
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatCode(() -> recorder.recordPayload(GatewayEventType.IPN, Map.of("tran_id", "OETABC123")))
                .doesNotThrowAnyException();
    }

    private PaymentGatewayResponse captureSaved() {
        ArgumentCaptor<PaymentGatewayResponse> captor = ArgumentCaptor.forClass(PaymentGatewayResponse.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
