package com.oet.auth.service;

import com.oet.auth.dto.AuthResponse;
import com.oet.auth.dto.LoginRequest;
import com.oet.auth.dto.RefreshTokenRequest;
import com.oet.auth.dto.RegisterRequest;
import com.oet.auth.dto.RegisterResponse;
import com.oet.auth.dto.ResendVerificationRequest;
import com.oet.auth.dto.VerifyEmailRequest;
import com.oet.auth.entity.EmailVerificationToken;
import com.oet.auth.entity.RefreshToken;
import com.oet.auth.repository.EmailVerificationTokenRepository;
import com.oet.auth.repository.RefreshTokenRepository;
import com.oet.common.exception.BusinessException;
import com.oet.common.exception.EmailNotVerifiedException;
import com.oet.common.exception.NotFoundException;
import com.oet.config.JwtTokenProvider;
import com.oet.user.entity.User;
import com.oet.user.entity.UserRole;
import com.oet.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.mail.verification-token-expiration-hours}")
    private long verificationTokenExpirationHours;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email already in use: " + request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(UserRole.APPLICANT)
                .profession(request.profession())
                .build();

        userRepository.save(user);
        log.info("New applicant registered: {}", user.getEmail());

        issueVerificationToken(user);

        return new RegisterResponse("Verification email sent", user.getEmail());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email()).orElseThrow();
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Email not verified. Please check your inbox.");
        }
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new NotFoundException("Refresh token not found"));

        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new BusinessException("Refresh token expired. Please login again.");
        }

        User user = storedToken.getUser();
        refreshTokenRepository.delete(storedToken);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        EmailVerificationToken storedToken = emailVerificationTokenRepository.findByToken(request.token())
                .orElseThrow(() -> new NotFoundException("Verification token not found"));

        if (storedToken.isExpired()) {
            emailVerificationTokenRepository.delete(storedToken);
            throw new BusinessException("Verification link expired. Please request a new one.");
        }

        User user = storedToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        emailVerificationTokenRepository.delete(storedToken);

        log.info("Email verified: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        userRepository.findByEmail(request.email())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::issueVerificationToken);
    }

    private void issueVerificationToken(User user) {
        emailVerificationTokenRepository.deleteByUserId(user.getId());

        String rawToken = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .token(rawToken)
                .expiresAt(LocalDateTime.now().plusHours(verificationTokenExpirationHours))
                .build();
        emailVerificationTokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(user, rawToken);
    }

    AuthResponse buildAuthResponse(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);

        String rawRefreshToken = UUID.randomUUID().toString();
        LocalDateTime refreshExpiry = LocalDateTime.now()
                .plusSeconds(jwtTokenProvider.getRefreshTokenExpirationMs() / 1000);

        refreshTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(rawRefreshToken)
                .expiresAt(refreshExpiry)
                .build());

        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationMs(),
                user.getRole(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName()
        );
    }
}
