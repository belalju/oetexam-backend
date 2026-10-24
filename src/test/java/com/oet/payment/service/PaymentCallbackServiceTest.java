package com.oet.payment.service;

import com.oet.common.exception.NotFoundException;
import com.oet.payment.client.SslCommerzSignatureVerifier;
import com.oet.payment.config.SslCommerzProperties;
import com.oet.payment.entity.Payment;
import com.oet.payment.entity.PaymentStatus;
import com.oet.payment.exception.PaymentGatewayException;
import com.oet.payment.exception.PaymentValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCallbackServiceTest {

    private static final String TRAN_ID = "OETABC123";
    private static final String VAL_ID = "VAL123";
    private static final Map<String, String> SUCCESS_PARAMS = Map.of("tran_id", TRAN_ID, "val_id", VAL_ID);

    @Mock
    private OrderValidationService orderValidationService;
    @Mock
    private SslCommerzSignatureVerifier signatureVerifier;

    private PaymentCallbackService service;

    @BeforeEach
    void setUp() {
        SslCommerzProperties properties = new SslCommerzProperties(
                "https://sandbox.sslcommerz.com", "store", "secret",
                "https://api/success", "https://api/fail", "https://api/cancel",
                "http://localhost:4200/payment", null, Duration.ofSeconds(10), Duration.ofSeconds(30));
        service = new PaymentCallbackService(orderValidationService, signatureVerifier, properties);
    }

    @Test
    void handleSuccess_validatedPayment_redirectsToSuccessPage() {
        when(orderValidationService.confirmPayment(TRAN_ID, VAL_ID)).thenReturn(payment(PaymentStatus.VALID));

        assertThat(service.handleSuccess(SUCCESS_PARAMS)).hasToString(
                "http://localhost:4200/payment/success?tran_id=OETABC123&val_id=VAL123");
    }

    @Test
    void handleSuccess_highRiskPayment_stillRedirectsToSuccessPage() {
        when(orderValidationService.confirmPayment(TRAN_ID, VAL_ID)).thenReturn(payment(PaymentStatus.ON_HOLD));

        assertThat(service.handleSuccess(SUCCESS_PARAMS).getPath()).isEqualTo("/payment/success");
    }

    @Test
    void handleSuccess_invalidTransaction_redirectsToFailPage() {
        when(orderValidationService.confirmPayment(TRAN_ID, VAL_ID))
                .thenThrow(new PaymentValidationException("Transaction is not valid"));

        assertThat(service.handleSuccess(SUCCESS_PARAMS)).hasToString(
                "http://localhost:4200/payment/fail?tran_id=OETABC123");
    }

    @Test
    void handleSuccess_paymentAlreadyFailed_redirectsToFailPage() {
        when(orderValidationService.confirmPayment(TRAN_ID, VAL_ID)).thenReturn(payment(PaymentStatus.FAILED));

        assertThat(service.handleSuccess(SUCCESS_PARAMS).getPath()).isEqualTo("/payment/fail");
    }

    @Test
    void handleSuccess_gatewayDown_redirectsToSuccessPageForRetry() {
        when(orderValidationService.confirmPayment(TRAN_ID, VAL_ID))
                .thenThrow(new PaymentGatewayException("Payment gateway is unavailable"));

        assertThat(service.handleSuccess(SUCCESS_PARAMS)).hasToString(
                "http://localhost:4200/payment/success?tran_id=OETABC123&val_id=VAL123");
    }

    @Test
    void handleFail_signedCallback_marksPaymentFailed() {
        Map<String, String> params = Map.of("tran_id", TRAN_ID, "status", "FAILED");
        when(signatureVerifier.isValid(params)).thenReturn(true);

        assertThat(service.handleFail(params)).hasToString("http://localhost:4200/payment/fail?tran_id=OETABC123");
        verify(orderValidationService).updateIfPending(eq(TRAN_ID), any());
    }

    @Test
    void handleCancel_unsignedCallback_redirectsWithoutChangingPayment() {
        Map<String, String> params = Map.of("tran_id", TRAN_ID);
        when(signatureVerifier.isValid(params)).thenReturn(false);

        assertThat(service.handleCancel(params)).hasToString("http://localhost:4200/payment/cancel?tran_id=OETABC123");
        verify(orderValidationService, never()).updateIfPending(anyString(), any());
    }

    @Test
    void handleCancel_unknownPayment_stillRedirects() {
        Map<String, String> params = Map.of("tran_id", TRAN_ID);
        when(signatureVerifier.isValid(params)).thenReturn(true);
        when(orderValidationService.updateIfPending(eq(TRAN_ID), any())).thenThrow(new NotFoundException("missing"));

        assertThat(service.handleCancel(params).getPath()).isEqualTo("/payment/cancel");
    }

    private Payment payment(PaymentStatus status) {
        return Payment.builder().transactionId(TRAN_ID).status(status).build();
    }
}
