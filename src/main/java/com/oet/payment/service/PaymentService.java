package com.oet.payment.service;

import com.oet.common.exception.NotFoundException;
import com.oet.payment.client.SslCommerzClient;
import com.oet.payment.config.SslCommerzProperties;
import com.oet.payment.dto.CreatePaymentSessionRequest;
import com.oet.payment.dto.PaymentSessionDetailsResponse;
import com.oet.payment.dto.PaymentSessionResponse;
import com.oet.payment.dto.SslCommerzSessionQueryResponse;
import com.oet.payment.dto.SslCommerzSessionResponse;
import com.oet.payment.entity.Payment;
import com.oet.payment.entity.PaymentStatus;
import com.oet.payment.entity.PricingPackage;
import com.oet.payment.exception.PaymentGatewayException;
import com.oet.payment.exception.PaymentValidationException;
import com.oet.payment.repository.PaymentRepository;
import com.oet.user.entity.User;
import com.oet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String TRANSACTION_ID_PREFIX = "OET";

    private final SslCommerzClient sslCommerzClient;
    private final SslCommerzProperties properties;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    // Not transactional: the gateway call must not hold a DB transaction open.
    public PaymentSessionResponse createSession(CreatePaymentSessionRequest request, String customerEmail) {
        PricingPackage pricingPackage = resolvePackage(request);
        Payment payment = paymentRepository.save(newPendingPayment(pricingPackage, customerEmail));
        String transactionId = payment.getTransactionId();

        SslCommerzSessionResponse response;
        try {
            response = sslCommerzClient.createSession(buildSessionParams(request, pricingPackage, payment, customerEmail));
        } catch (PaymentGatewayException ex) {
            markFailed(payment);
            throw ex;
        }

        if (!response.isSuccess() || !StringUtils.hasText(response.gatewayPageUrl())) {
            log.warn("SSLCommerz session creation failed for tran_id={}: {}", transactionId, response.failedReason());
            markFailed(payment);
            throw new PaymentGatewayException("Unable to create payment session: " + response.failedReason());
        }

        payment.setSessionKey(response.sessionKey());
        paymentRepository.save(payment);
        log.info("Payment session created: tran_id={}", transactionId);
        return new PaymentSessionResponse(transactionId, response.sessionKey(), response.gatewayPageUrl());
    }

    public PaymentSessionDetailsResponse getSession(String sessionKey, String requesterEmail, boolean isAdmin) {
        SslCommerzSessionQueryResponse response = sslCommerzClient.querySession(sessionKey);

        if (!response.isConnected()) {
            log.warn("SSLCommerz session query failed: APIConnect={}", response.apiConnect());
            throw new PaymentGatewayException("Unable to query payment session: " + response.apiConnect());
        }
        if (!StringUtils.hasText(response.transactionId())) {
            throw new NotFoundException("Payment session not found: " + sessionKey);
        }
        // value_a carries the payer's email, set when the session was created
        if (!isAdmin && !requesterEmail.equalsIgnoreCase(response.valueA())) {
            throw new AccessDeniedException("Payment session belongs to another user");
        }
        return toDetailsResponse(response);
    }

    // The client's amount is only echoed back for comparison; the charge always uses the server price.
    private PricingPackage resolvePackage(CreatePaymentSessionRequest request) {
        PricingPackage pricingPackage = PricingPackage.fromCode(request.packageId())
                .orElseThrow(() -> new NotFoundException("Package not found: " + request.packageId()));
        String currency = StringUtils.hasText(request.currency()) ? request.currency() : pricingPackage.getCurrency();
        if (!pricingPackage.matches(request.amount(), currency)) {
            log.warn("Checkout price mismatch for package={}: amount={} {}",
                    pricingPackage.getCode(), request.amount(), currency);
            throw new PaymentValidationException("Package price has changed. Please refresh and try again.");
        }
        return pricingPackage;
    }

    private Payment newPendingPayment(PricingPackage pricingPackage, String customerEmail) {
        User user = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new NotFoundException("User not found: " + customerEmail));
        return Payment.builder()
                .user(user)
                .pricingPackage(pricingPackage)
                .transactionId(generateTransactionId())
                .amount(pricingPackage.getPrice())
                .currency(pricingPackage.getCurrency())
                .status(PaymentStatus.PENDING)
                .build();
    }

    private void markFailed(Payment payment) {
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);
    }

    private MultiValueMap<String, String> buildSessionParams(CreatePaymentSessionRequest request,
                                                             PricingPackage pricingPackage,
                                                             Payment payment,
                                                             String customerEmail) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("total_amount", payment.getAmount().toPlainString());
        params.add("currency", payment.getCurrency());
        params.add("tran_id", payment.getTransactionId());
        params.add("success_url", properties.successUrl());
        params.add("fail_url", properties.failUrl());
        params.add("cancel_url", properties.cancelUrl());
        if (StringUtils.hasText(properties.ipnUrl())) {
            params.add("ipn_url", properties.ipnUrl());
        }
        params.add("cus_name", request.customerName());
        params.add("cus_email", customerEmail);
        params.add("cus_add1", request.customerAddress());
        params.add("cus_city", request.customerCity());
        params.add("cus_postcode", request.customerPostcode());
        params.add("cus_country", request.customerCountry());
        params.add("cus_phone", request.customerPhone());
        params.add("shipping_method", "NO");
        params.add("product_name", pricingPackage.getDisplayName());
        params.add("product_category", PricingPackage.PRODUCT_CATEGORY);
        params.add("product_profile", "non-physical-goods");
        params.add("value_a", customerEmail);
        return params;
    }

    private PaymentSessionDetailsResponse toDetailsResponse(SslCommerzSessionQueryResponse response) {
        return new PaymentSessionDetailsResponse(
                response.sessionKey(),
                response.status(),
                response.transactionId(),
                response.validationId(),
                response.transactionDate(),
                response.amount(),
                response.currency(),
                response.bankTransactionId(),
                response.cardType(),
                response.cardBrand(),
                response.riskLevel(),
                response.riskTitle()
        );
    }

    // SSLCommerz limits tran_id to 30 characters
    private String generateTransactionId() {
        return TRANSACTION_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
    }
}
