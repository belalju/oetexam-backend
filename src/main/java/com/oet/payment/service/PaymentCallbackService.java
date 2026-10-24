package com.oet.payment.service;

import com.oet.common.exception.NotFoundException;
import com.oet.payment.client.SslCommerzSignatureVerifier;
import com.oet.payment.config.SslCommerzProperties;
import com.oet.payment.entity.Payment;
import com.oet.payment.entity.PaymentStatus;
import com.oet.payment.exception.PaymentGatewayException;
import com.oet.payment.exception.PaymentValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Handles the browser form POSTs SSLCommerz sends to success_url, fail_url and cancel_url,
 * and decides which frontend result page the customer is redirected to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCallbackService {

    private static final String SUCCESS_PAGE = "success";
    private static final String FAIL_PAGE = "fail";
    private static final String CANCEL_PAGE = "cancel";

    private final OrderValidationService orderValidationService;
    private final SslCommerzSignatureVerifier signatureVerifier;
    private final SslCommerzProperties properties;

    public URI handleSuccess(Map<String, String> params) {
        String transactionId = params.get("tran_id");
        String validationId = params.get("val_id");
        if (!StringUtils.hasText(transactionId)) {
            return resultPage(FAIL_PAGE, null, null);
        }
        try {
            Payment payment = orderValidationService.confirmPayment(transactionId, validationId);
            String page = payment.getStatus().isPaid() ? SUCCESS_PAGE : FAIL_PAGE;
            return resultPage(page, transactionId, validationId);
        } catch (PaymentValidationException | NotFoundException ex) {
            log.warn("Success callback rejected: tran_id={}, reason={}", transactionId, ex.getMessage());
            return resultPage(FAIL_PAGE, transactionId, null);
        } catch (PaymentGatewayException ex) {
            // Gateway unreachable: the success page retries validation, and the IPN confirms it later.
            log.warn("Success callback could not reach SSLCommerz: tran_id={}", transactionId);
            return resultPage(SUCCESS_PAGE, transactionId, validationId);
        }
    }

    public URI handleFail(Map<String, String> params) {
        recordUnpaid(params, PaymentStatus.FAILED);
        return resultPage(FAIL_PAGE, params.get("tran_id"), null);
    }

    public URI handleCancel(Map<String, String> params) {
        recordUnpaid(params, PaymentStatus.CANCELLED);
        return resultPage(CANCEL_PAGE, params.get("tran_id"), null);
    }

    // The endpoint is public, so the status only changes when SSLCommerz signed the request.
    private void recordUnpaid(Map<String, String> params, PaymentStatus status) {
        String transactionId = params.get("tran_id");
        if (!StringUtils.hasText(transactionId) || !signatureVerifier.isValid(params)) {
            log.warn("Unsigned {} callback ignored: tran_id={}", status, transactionId);
            return;
        }
        try {
            orderValidationService.updateIfPending(transactionId, pending -> pending.setStatus(status));
        } catch (NotFoundException ex) {
            log.warn("{} callback for unknown payment: tran_id={}", status, transactionId);
        }
    }

    private URI resultPage(String page, String transactionId, String validationId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.resultPageUrl())
                .pathSegment(page);
        if (StringUtils.hasText(transactionId)) {
            builder.queryParam("tran_id", transactionId);
        }
        if (StringUtils.hasText(validationId)) {
            builder.queryParam("val_id", validationId);
        }
        return builder.encode().build().toUri();
    }
}
