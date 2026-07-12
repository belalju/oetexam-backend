package com.oet.auth.controller;

import com.oet.auth.dto.AuthResponse;
import com.oet.auth.dto.GoogleLoginRequest;
import com.oet.auth.dto.LoginRequest;
import com.oet.auth.dto.RefreshTokenRequest;
import com.oet.auth.dto.RegisterRequest;
import com.oet.auth.dto.RegisterResponse;
import com.oet.auth.dto.ResendVerificationRequest;
import com.oet.auth.dto.VerifyEmailRequest;
import com.oet.auth.service.AuthService;
import com.oet.auth.service.GoogleAuthService;
import com.oet.common.util.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Authentication endpoints")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String RESEND_MESSAGE =
            "If an account with that email exists and is unverified, a verification email has been sent.";

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;

    @Operation(summary = "Register new applicant")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.created(authService.register(request)));
    }

    @Operation(summary = "Login and receive JWT tokens")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @Operation(summary = "Refresh access token")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request)));
    }

    @Operation(summary = "Sign in or register with a Google ID token")
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> google(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(googleAuthService.loginWithGoogle(request)));
    }

    @Operation(summary = "Verify email address and receive JWT tokens")
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.verifyEmail(request)));
    }

    @Operation(summary = "Resend the email verification link")
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<String>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok(ApiResponse.success(RESEND_MESSAGE));
    }
}
