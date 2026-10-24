package com.oet.payment.service;

import com.oet.common.exception.NotFoundException;
import com.oet.payment.client.SslCommerzClient;
import com.oet.payment.dto.OrderValidationRequest;
import com.oet.payment.dto.OrderValidationResponse;
import com.oet.payment.dto.SslCommerzValidationResponse;
import com.oet.payment.entity.Payment;
import com.oet.payment.entity.PaymentStatus;
import com.oet.payment.exception.PaymentValidationException;
import com.oet.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderValidationServiceTest {

    private static final String TRAN_ID = "OETABC123";
    private static final String VAL_ID = "2410231234567abcdef";
    private static final String EMAIL = "applicant@example.com";

    @Mock
    private SslCommerzClient sslCommerzClient;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TransactionTemplate transactionTemplate;

    private OrderValidationService service;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new OrderValidationService(sslCommerzClient, paymentRepository, transactionTemplate);
        payment = Payment.builder()
                .transactionId(TRAN_ID)
                .amount(new BigDecimal("1500.00"))
                .currency("BDT")
                .status(PaymentStatus.PENDING)
                .build();
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        lenient().when(paymentRepository.findByTransactionIdForUpdate(TRAN_ID)).thenReturn(Optional.of(payment));
    }

    @Test
    void validateOrder_validTransaction_marksPaymentValid() {
        givenOwnedPayment();
        when(sslCommerzClient.validate(VAL_ID)).thenReturn(gatewayResponse("VALID", "1500", 0));

        OrderValidationResponse response = service.validateOrder(request(), EMAIL);

        assertThat(response.status()).isEqualTo(PaymentStatus.VALID);
        assertThat(response.validationId()).isEqualTo(VAL_ID);
        assertThat(response.bankTransactionId()).isEqualTo("BANK1");
        assertThat(response.validatedAt()).isNotNull();
    }

    @Test
    void validateOrder_alreadyValidatedStatus_isAccepted() {
        givenOwnedPayment();
        when(sslCommerzClient.validate(VAL_ID)).thenReturn(gatewayResponse("VALIDATED", "1500.00", 0));

        assertThat(service.validateOrder(request(), EMAIL).status()).isEqualTo(PaymentStatus.VALID);
    }

    @Test
    void validateOrder_highRisk_putsPaymentOnHold() {
        givenOwnedPayment();
        when(sslCommerzClient.validate(VAL_ID)).thenReturn(gatewayResponse("VALID", "1500.00", 1));

        assertThat(service.validateOrder(request(), EMAIL).status()).isEqualTo(PaymentStatus.ON_HOLD);
    }

    @Test
    void validateOrder_invalidTransaction_throwsAndLeavesPending() {
        givenOwnedPayment();
        when(sslCommerzClient.validate(VAL_ID)).thenReturn(gatewayResponse("INVALID_TRANSACTION", null, null));

        assertThatThrownBy(() -> service.validateOrder(request(), EMAIL))
                .isInstanceOf(PaymentValidationException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void validateOrder_amountMismatch_throws() {
        givenOwnedPayment();
        when(sslCommerzClient.validate(VAL_ID)).thenReturn(gatewayResponse("VALID", "10.00", 0));

        assertThatThrownBy(() -> service.validateOrder(request(), EMAIL))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("does not match");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void validateOrder_valIdForAnotherTransaction_throws() {
        givenOwnedPayment();
        when(sslCommerzClient.validate(VAL_ID)).thenReturn(new SslCommerzValidationResponse(
                "VALID", null, "OETOTHER", VAL_ID, new BigDecimal("1500.00"), null, "BDT", "BDT",
                new BigDecimal("1500.00"), "BANK1", "VISA-Dutch Bangla", "VISA", 0, "Safe"));

        assertThatThrownBy(() -> service.validateOrder(request(), EMAIL))
                .isInstanceOf(PaymentValidationException.class);
    }

    @Test
    void validateOrder_paymentAlreadyFinal_skipsGateway() {
        payment.setStatus(PaymentStatus.VALID);
        givenOwnedPayment();

        assertThat(service.validateOrder(request(), EMAIL).status()).isEqualTo(PaymentStatus.VALID);
        verify(sslCommerzClient, never()).validate(anyString());
    }

    @Test
    void validateOrder_paymentOfAnotherUser_throwsNotFound() {
        when(paymentRepository.findByTransactionIdAndUserEmail(TRAN_ID, EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateOrder(request(), EMAIL))
                .isInstanceOf(NotFoundException.class);
        verify(sslCommerzClient, never()).validate(anyString());
    }

    @Test
    void verifyWithGateway_missingValId_throws() {
        assertThatThrownBy(() -> service.verifyWithGateway(" ", payment))
                .isInstanceOf(PaymentValidationException.class);
    }

    private void givenOwnedPayment() {
        when(paymentRepository.findByTransactionIdAndUserEmail(TRAN_ID, EMAIL)).thenReturn(Optional.of(payment));
    }

    private OrderValidationRequest request() {
        return new OrderValidationRequest(TRAN_ID, VAL_ID);
    }

    private SslCommerzValidationResponse gatewayResponse(String status, String currencyAmount, Integer riskLevel) {
        BigDecimal amount = currencyAmount == null ? null : new BigDecimal(currencyAmount);
        return new SslCommerzValidationResponse(
                status, "2026-10-23 22:00:00", TRAN_ID, VAL_ID, amount, amount, "BDT", "BDT",
                amount, "BANK1", "VISA-Dutch Bangla", "VISA", riskLevel, "Safe");
    }
}
