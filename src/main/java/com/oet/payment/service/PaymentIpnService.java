package com.oet.payment.service;

import com.oet.common.exception.NotFoundException;
import com.oet.payment.client.SslCommerzClient;
import com.oet.payment.client.SslCommerzSignatureVerifier;
import com.oet.payment.dto.PaymentIpnResponse;
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
import java.util.Map;
import java.util.function.Consumer;

/**
 * Handles SSLCommerz IPN callbacks. The IPN payload is never trusted on its own:
 * its signature is checked, then the transaction is confirmed with the Order
 * Validation API and matched against the stored payment before it is marked paid.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIpnService {

    private static final Map<String, PaymentStatus> UNPAID_IPN_STATUSES = Map.of(
            "FAILED", PaymentStatus.FAILED,
            "CANCELLED", PaymentStatus.CANCELLED,
            "EXPIRED", PaymentStatus.EXPIRED,
            "UNATTEMPTED", PaymentStatus.UNATTEMPTED);

    private final SslCommerzClient sslCommerzClient;
    private final SslCommerzSignatureVerifier signatureVerifier;
    private final PaymentRepository paymentRepository;
    private final TransactionTemplate transactionTemplate;

    public PaymentIpnResponse handleIpn(Map<String, String> ipnParams) {
        String transactionId = ipnParams.get("tran_id");
        String ipnStatus = ipnParams.getOrDefault("status", "");

        if (!signatureVerifier.isValid(ipnParams)) {
            log.warn("Rejected IPN with invalid signature: tran_id={}", transactionId);
            throw new PaymentValidationException("Invalid IPN signature");
        }

        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new NotFoundException("Payment not found: " + transactionId));
        if (payment.getStatus().isFinal()) {
            log.info("IPN ignored, payment already {}: tran_id={}", payment.getStatus(), transactionId);
            return new PaymentIpnResponse(transactionId, payment.getStatus());
        }

        if (!"VALID".equalsIgnoreCase(ipnStatus)) {
            PaymentStatus status = UNPAID_IPN_STATUSES.getOrDefault(ipnStatus.toUpperCase(), PaymentStatus.FAILED);
            return updatePending(transactionId, pending -> pending.setStatus(status));
        }

        SslCommerzValidationResponse validation = validateWithGateway(ipnParams.get("val_id"), payment);
        return updatePending(transactionId, pending -> markPaid(pending, validation));
    }

    private SslCommerzValidationResponse validateWithGateway(String validationId, Payment payment) {
        if (!StringUtils.hasText(validationId)) {
            throw new PaymentValidationException("Missing val_id in IPN");
        }
        SslCommerzValidationResponse validation = sslCommerzClient.validate(validationId);

        if (!validation.isValid()) {
            log.warn("Order validation failed: tran_id={}, status={}", payment.getTransactionId(), validation.status());
            throw new PaymentValidationException("Transaction is not valid: " + validation.status());
        }
        if (!payment.getTransactionId().equals(validation.transactionId())
                || !payment.getCurrency().equalsIgnoreCase(validation.currencyType())
                || validation.currencyAmount() == null
                || payment.getAmount().compareTo(validation.currencyAmount()) != 0) {
            log.error("Validated transaction does not match stored payment: tran_id={}, val_id={}",
                    payment.getTransactionId(), validationId);
            throw new PaymentValidationException("Validated transaction does not match the payment");
        }
        return validation;
    }

    private void markPaid(Payment payment, SslCommerzValidationResponse validation) {
        payment.setStatus(validation.isHighRisk() ? PaymentStatus.ON_HOLD : PaymentStatus.VALID);
        payment.setValidationId(validation.validationId());
        payment.setBankTransactionId(validation.bankTransactionId());
        payment.setCardType(validation.cardType());
        payment.setRiskLevel(validation.riskLevel());
        payment.setRiskTitle(validation.riskTitle());
        payment.setValidatedAt(LocalDateTime.now());
    }

    // Re-reads the payment under a row lock so concurrent IPN retries apply only once.
    private PaymentIpnResponse updatePending(String transactionId, Consumer<Payment> update) {
        return transactionTemplate.execute(txStatus -> {
            Payment payment = paymentRepository.findByTransactionIdForUpdate(transactionId)
                    .orElseThrow(() -> new NotFoundException("Payment not found: " + transactionId));
            if (!payment.getStatus().isFinal()) {
                update.accept(payment);
                log.info("Payment updated from IPN: tran_id={}, status={}", transactionId, payment.getStatus());
            }
            return new PaymentIpnResponse(transactionId, payment.getStatus());
        });
    }
}
