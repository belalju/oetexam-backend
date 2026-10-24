package com.oet.payment.controller;

import com.oet.common.util.ApiResponse;
import com.oet.payment.dto.CreatePaymentSessionRequest;
import com.oet.payment.dto.OrderValidationRequest;
import com.oet.payment.dto.OrderValidationResponse;
import com.oet.payment.dto.PaymentIpnResponse;
import com.oet.payment.dto.PaymentSessionDetailsResponse;
import com.oet.payment.dto.PaymentSessionResponse;
import com.oet.payment.service.OrderValidationService;
import com.oet.payment.service.PaymentCallbackService;
import com.oet.payment.service.PaymentIpnService;
import com.oet.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentIpnService paymentIpnService;
    private final OrderValidationService orderValidationService;
    private final PaymentCallbackService paymentCallbackService;

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
        return ResponseEntity.ok(ApiResponse.success(paymentIpnService.handleIpn(ipnParams)));
    }

    /**
     * Public SSLCommerz redirect targets. The gateway form-POSTs the customer's browser here;
     * each one answers 303 to the matching frontend result page.
     */
    @PostMapping(value = "/callback/success", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> successCallback(@RequestParam Map<String, String> params) {
        return seeOther(paymentCallbackService.handleSuccess(params));
    }

    @PostMapping(value = "/callback/fail", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> failCallback(@RequestParam Map<String, String> params) {
        return seeOther(paymentCallbackService.handleFail(params));
    }

    @PostMapping(value = "/callback/cancel", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> cancelCallback(@RequestParam Map<String, String> params) {
        return seeOther(paymentCallbackService.handleCancel(params));
    }

    private ResponseEntity<Void> seeOther(URI location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }
}
