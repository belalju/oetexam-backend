package com.oet.payment.controller;

import com.oet.common.util.ApiResponse;
import com.oet.payment.dto.CreatePaymentSessionRequest;
import com.oet.payment.dto.OrderValidationRequest;
import com.oet.payment.dto.OrderValidationResponse;
import com.oet.payment.dto.PaymentIpnResponse;
import com.oet.payment.dto.PaymentSessionDetailsResponse;
import com.oet.payment.dto.PaymentSessionResponse;
import com.oet.payment.entity.GatewayEventType;
import com.oet.payment.service.GatewayResponseRecorder;
import com.oet.payment.service.OrderValidationService;
import com.oet.payment.service.PaymentCallbackService;
import com.oet.payment.service.PaymentIpnService;
import com.oet.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentIpnService paymentIpnService;
    private final OrderValidationService orderValidationService;
    private final PaymentCallbackService paymentCallbackService;
    private final GatewayResponseRecorder gatewayResponseRecorder;

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<PaymentSessionResponse>> createSession(
            @Valid @RequestBody CreatePaymentSessionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        PaymentSessionResponse response = paymentService.createSession(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @GetMapping("/sessions/{sessionKey}")
    public ResponseEntity<ApiResponse<PaymentSessionDetailsResponse>> getSession(
            @PathVariable @NotBlank @Size(max = 50) String sessionKey,
            @AuthenticationPrincipal UserDetails userDetails) {
        boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getSession(sessionKey, userDetails.getUsername(), isAdmin)));
    }

    /**
     * Confirms a payment with the SSLCommerz Order Validation API using the tran_id and val_id
     * SSLCommerz posted to the success URL. Safe to call more than once.
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<OrderValidationResponse>> validateOrder(
            @Valid @RequestBody OrderValidationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                orderValidationService.validateOrder(request, userDetails.getUsername())));
    }

    /**
     * Public IPN listener called server-to-server by SSLCommerz (form-urlencoded POST).
     */
    @PostMapping(value = "/ipn", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<ApiResponse<PaymentIpnResponse>> handleIpn(@RequestParam Map<String, String> ipnParams) {
        logGatewayCall(GatewayEventType.IPN, ipnParams);
        return ResponseEntity.ok(ApiResponse.success(paymentIpnService.handleIpn(ipnParams)));
    }

    /**
     * Public SSLCommerz redirect targets. The gateway form-POSTs the customer's browser here;
     * each one answers 303 to the matching frontend result page.
     */
    @PostMapping(value = "/callback/success", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> successCallback(@RequestParam Map<String, String> params) {
        logGatewayCall(GatewayEventType.SUCCESS_CALLBACK, params);
        return seeOther(paymentCallbackService.handleSuccess(params));
    }

    @PostMapping(value = "/callback/fail", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> failCallback(@RequestParam Map<String, String> params) {
        logGatewayCall(GatewayEventType.FAIL_CALLBACK, params);
        return seeOther(paymentCallbackService.handleFail(params));
    }

    @PostMapping(value = "/callback/cancel", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> cancelCallback(@RequestParam Map<String, String> params) {
        logGatewayCall(GatewayEventType.CANCEL_CALLBACK, params);
        return seeOther(paymentCallbackService.handleCancel(params));
    }

    /**
     * Public diagnostic endpoint: returns the server's current date-time so the domain
     * routing to this backend can be checked from a browser.
     */
    @GetMapping("/callback/redirect-test")
    public ResponseEntity<ApiResponse<OffsetDateTime>> redirectTest() {
        return ResponseEntity.ok(ApiResponse.success(OffsetDateTime.now()));
    }

    /**
     * Stores the full SSLCommerz payload as JSON, then logs only non-sensitive identifiers;
     * card and customer fields are deliberately left out of the log.
     */
    private void logGatewayCall(GatewayEventType endpoint, Map<String, String> params) {
        gatewayResponseRecorder.recordPayload(endpoint, params);
        log.info("SSLCommerz {} received: tran_id={}, val_id={}, status={}, amount={}, currency={}",
                endpoint,
                params.get("tran_id"),
                params.get("val_id"),
                params.get("status"),
                params.get("amount"),
                params.get("currency"));
    }

    private ResponseEntity<Void> seeOther(URI location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }
}
