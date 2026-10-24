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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Confirms payments with the SSLCommerz Order Validation API. Used by both the
 * success-redirect flow and the IPN listener; whichever arrives first marks the
 * payment paid and the other becomes a no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderValidationService {

    private final SslCommerzClient sslCommerzClient;
    private final PaymentRepository paymentRepository;
    private final TransactionTemplate transactionTemplate;

    // Not transactional: the gateway call must not hold a DB transaction open.
    public OrderValidationResponse validateOrder(OrderValidationRequest request, String customerEmail) {
        String transactionId = request.transactionId();
        Payment payment = paymentRepository.findByTransactionIdAndUserEmail(transactionId, customerEmail)
                .orElseThrow(() -> new NotFoundException("Payment not found: " + transactionId));
        return OrderValidationResponse.from(confirm(payment, request.validationId()));
    }

    /**
     * Confirms a payment without an ownership check, for the SSLCommerz success redirect.
     * Safe because the Order Validation API response, not the caller, decides the outcome.
     */
    public Payment confirmPayment(String transactionId, String validationId) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new NotFoundException("Payment not found: " + transactionId));
        return confirm(payment, validationId);
    }

    private Payment confirm(Payment payment, String validationId) {
        if (payment.getStatus().isFinal()) {
            log.info("Order validation skipped, payment already {}: tran_id={}",
                    payment.getStatus(), payment.getTransactionId());
            return payment;
        }
        SslCommerzValidationResponse validation = verifyWithGateway(validationId, payment);
        return updateIfPending(payment.getTransactionId(), pending -> markPaid(pending, validation));
    }

    /**
     * Calls the Order Validation API and checks the result against the stored payment.
     */
    public SslCommerzValidationResponse verifyWithGateway(String validationId, Payment payment) {
        if (!StringUtils.hasText(validationId)) {
            throw new PaymentValidationException("Missing val_id");
        }
        SslCommerzValidationResponse validation = sslCommerzClient.validate(validationId);

        if (!validation.isValid()) {
            log.warn("Order validation failed: tran_id={}, status={}", payment.getTransactionId(), validation.status());
            throw new PaymentValidationException("Transaction is not valid: " + validation.status());
        }
        if (!matchesPayment(validation, payment)) {
            log.error("Validated transaction does not match stored payment: tran_id={}, val_id={}",
                    payment.getTransactionId(), validationId);
            throw new PaymentValidationException("Validated transaction does not match the payment");
        }
        return validation;
    }

    public void markPaid(Payment payment, SslCommerzValidationResponse validation) {
        payment.setStatus(validation.isHighRisk() ? PaymentStatus.ON_HOLD : PaymentStatus.VALID);
        payment.setValidationId(validation.validationId());
        payment.setBankTransactionId(validation.bankTransactionId());
        payment.setCardType(validation.cardType());
        payment.setRiskLevel(validation.riskLevel());
        payment.setRiskTitle(validation.riskTitle());
        payment.setValidatedAt(LocalDateTime.now());
    }

    // Re-reads the payment under a row lock so concurrent IPN and redirect validations apply only once.
    public Payment updateIfPending(String transactionId, Consumer<Payment> update) {
        return transactionTemplate.execute(txStatus -> {
            Payment payment = paymentRepository.findByTransactionIdForUpdate(transactionId)
                    .orElseThrow(() -> new NotFoundException("Payment not found: " + transactionId));
            if (!payment.getStatus().isFinal()) {
                update.accept(payment);
                log.info("Payment updated: tran_id={}, status={}", transactionId, payment.getStatus());
            }
            return payment;
        });
    }

    // currency_type/currency_amount echo what was sent at initiation; amount may be converted to BDT.
    private boolean matchesPayment(SslCommerzValidationResponse validation, Payment payment) {
        return payment.getTransactionId().equals(validation.transactionId())
                && payment.getCurrency().equalsIgnoreCase(validation.currencyType())
                && validation.currencyAmount() != null
                && payment.getAmount().compareTo(validation.currencyAmount()) == 0;
    }
}
