package com.oet.payment.service;

import com.oet.common.exception.NotFoundException;
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

import java.util.Map;

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

    private final SslCommerzSignatureVerifier signatureVerifier;
    private final PaymentRepository paymentRepository;
    private final OrderValidationService orderValidationService;

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
            return toResponse(orderValidationService.updateIfPending(transactionId, pending -> pending.setStatus(status)));
        }

        SslCommerzValidationResponse validation =
                orderValidationService.verifyWithGateway(ipnParams.get("val_id"), payment);
        return toResponse(orderValidationService.updateIfPending(transactionId,
                pending -> orderValidationService.markPaid(pending, validation)));
    }

    private PaymentIpnResponse toResponse(Payment payment) {
        return new PaymentIpnResponse(payment.getTransactionId(), payment.getStatus());
    }
}
