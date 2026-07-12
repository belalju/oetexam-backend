package com.oet.auth.service;

import com.oet.auth.dto.AuthResponse;
import com.oet.auth.dto.LoginRequest;
import com.oet.auth.dto.RegisterRequest;
import com.oet.auth.dto.RegisterResponse;
import com.oet.auth.dto.ResendVerificationRequest;
import com.oet.auth.dto.VerifyEmailRequest;
import com.oet.auth.entity.EmailVerificationToken;
import com.oet.auth.repository.EmailVerificationTokenRepository;
import com.oet.auth.repository.RefreshTokenRepository;
import com.oet.common.exception.BusinessException;
import com.oet.common.exception.EmailNotVerifiedException;
import com.oet.common.exception.NotFoundException;
import com.oet.config.JwtTokenProvider;
import com.oet.user.entity.User;
import com.oet.user.entity.UserRole;
import com.oet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "verificationTokenExpirationHours", 24L);
    }

    private User sampleUser() {
        return User.builder()
                .id(1L)
                .email("applicant@example.com")
                .firstName("Ann")
                .lastName("Lee")
                .role(UserRole.APPLICANT)
                .active(true)
                .emailVerified(false)
                .build();
    }

    @Test
    void register_createsUnverifiedUser_sendsEmail_andReturnsNoTokens() {
        RegisterRequest request = new RegisterRequest("Ann", "Lee", "applicant@example.com", "password123", null);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed");

        RegisterResponse response = authService.register(request);

        assertThat(response.email()).isEqualTo("applicant@example.com");
        verify(emailVerificationTokenRepository).deleteByUserId(any());
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).sendVerificationEmail(any(User.class), anyString());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void register_throwsBusinessException_whenEmailAlreadyInUse() {
        RegisterRequest request = new RegisterRequest("Ann", "Lee", "applicant@example.com", "password123", null);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void login_throwsEmailNotVerified_whenUserUnverified() {
        LoginRequest request = new LoginRequest("applicant@example.com", "password123");
        User user = sampleUser();
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessage("Email not verified. Please check your inbox.");
    }

    @Test
    void login_succeeds_whenUserVerified() {
        LoginRequest request = new LoginRequest("applicant@example.com", "password123");
        User user = sampleUser();
        user.setEmailVerified(true);
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        mockBuildAuthResponse(user);

        AuthResponse response = authService.login(request);

        assertThat(response.email()).isEqualTo(user.getEmail());
    }

    @Test
    void verifyEmail_validToken_setsVerifiedAndReturnsTokens() {
        User user = sampleUser();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(10L)
                .user(user)
                .token("valid-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(emailVerificationTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        mockBuildAuthResponse(user);

        AuthResponse response = authService.verifyEmail(new VerifyEmailRequest("valid-token"));

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(response.email()).isEqualTo(user.getEmail());
        verify(emailVerificationTokenRepository).delete(token);
    }

    @Test
    void verifyEmail_expiredToken_deletesTokenAndThrows() {
        User user = sampleUser();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(11L)
                .user(user)
                .token("expired-token")
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();
        when(emailVerificationTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest("expired-token")))
                .isInstanceOf(BusinessException.class);
        verify(emailVerificationTokenRepository).delete(token);
    }

    @Test
    void verifyEmail_unknownToken_throwsNotFound() {
        when(emailVerificationTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail(new VerifyEmailRequest("missing")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resendVerification_unknownEmail_doesNothingButDoesNotThrow() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        authService.resendVerification(new ResendVerificationRequest("ghost@example.com"));

        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void resendVerification_alreadyVerifiedEmail_doesNothing() {
        User user = sampleUser();
        user.setEmailVerified(true);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        authService.resendVerification(new ResendVerificationRequest(user.getEmail()));

        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void resendVerification_unverifiedEmail_rotatesTokenAndResends() {
        User user = sampleUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        authService.resendVerification(new ResendVerificationRequest(user.getEmail()));

        verify(emailVerificationTokenRepository).deleteByUserId(user.getId());
        verify(emailService).sendVerificationEmail(eq(user), anyString());
    }

    private void mockBuildAuthResponse(User user) {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetailsService.loadUserByUsername(user.getEmail())).thenReturn(userDetails);
        when(jwtTokenProvider.generateAccessToken(userDetails)).thenReturn("access-token");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(3600000L);
    }
}
