package com.oet.auth.service;

import com.oet.auth.dto.AuthResponse;
import com.oet.auth.dto.GoogleLoginRequest;
import com.oet.user.entity.AuthProvider;
import com.oet.user.entity.User;
import com.oet.user.entity.UserRole;
import com.oet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private JwtDecoder googleIdTokenDecoder;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthService authService;

    private GoogleAuthService googleAuthService;

    private static final String ID_TOKEN = "google-id-token";

    private Jwt buildJwt(Map<String, Object> extraClaims) {
        Jwt.Builder builder = Jwt.withTokenValue(ID_TOKEN)
                .header("alg", "RS256")
                .subject("google-sub-123")
                .claim("email", "user@example.com")
                .claim("email_verified", true)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        extraClaims.forEach(builder::claim);
        return builder.build();
    }

    private void init() {
        googleAuthService = new GoogleAuthService(googleIdTokenDecoder, userRepository, authService);
    }

    @Test
    void loginWithGoogle_newUser_createsApplicantWithNullPassword() {
        init();
        Jwt jwt = buildJwt(Map.of("given_name", "Jamie", "family_name", "Doe"));
        when(googleIdTokenDecoder.decode(ID_TOKEN)).thenReturn(jwt);
        when(userRepository.findByGoogleSub("google-sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(savedUser.capture())).thenAnswer(inv -> savedUser.getValue());
        when(authService.buildAuthResponse(any(User.class))).thenReturn(mock(AuthResponse.class));

        googleAuthService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        User created = savedUser.getValue();
        assertThat(created.getRole()).isEqualTo(UserRole.APPLICANT);
        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(created.isEmailVerified()).isTrue();
        assertThat(created.getGoogleSub()).isEqualTo("google-sub-123");
        assertThat(created.getPasswordHash()).isNull();
        assertThat(created.getFirstName()).isEqualTo("Jamie");
        assertThat(created.getLastName()).isEqualTo("Doe");
    }

    @Test
    void loginWithGoogle_newUser_fallsBackToNameOrEmailLocalPart_whenGivenFamilyNameMissing() {
        init();
        Jwt jwt = buildJwt(Map.of("name", "Full Name"));
        when(googleIdTokenDecoder.decode(ID_TOKEN)).thenReturn(jwt);
        when(userRepository.findByGoogleSub("google-sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(savedUser.capture())).thenAnswer(inv -> savedUser.getValue());
        when(authService.buildAuthResponse(any(User.class))).thenReturn(mock(AuthResponse.class));

        googleAuthService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        User created = savedUser.getValue();
        assertThat(created.getFirstName()).isEqualTo("Full Name");
        assertThat(created.getLastName()).isEqualTo("");
    }

    @Test
    void loginWithGoogle_autoLinksExistingLocalAccountByEmail() {
        init();
        Jwt jwt = buildJwt(Map.of());
        User existing = User.builder()
                .id(5L)
                .email("user@example.com")
                .role(UserRole.APPLICANT)
                .authProvider(AuthProvider.LOCAL)
                .active(true)
                .emailVerified(false)
                .build();
        when(googleIdTokenDecoder.decode(ID_TOKEN)).thenReturn(jwt);
        when(userRepository.findByGoogleSub("google-sub-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));
        when(authService.buildAuthResponse(existing)).thenReturn(mock(AuthResponse.class));

        googleAuthService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        assertThat(existing.getGoogleSub()).isEqualTo("google-sub-123");
        assertThat(existing.isEmailVerified()).isTrue();
        assertThat(existing.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginWithGoogle_lookupBySub_shortCircuitsEmailLookup() {
        init();
        Jwt jwt = buildJwt(Map.of());
        User existing = User.builder()
                .id(7L)
                .email("user@example.com")
                .role(UserRole.APPLICANT)
                .authProvider(AuthProvider.GOOGLE)
                .googleSub("google-sub-123")
                .active(true)
                .emailVerified(true)
                .build();
        when(googleIdTokenDecoder.decode(ID_TOKEN)).thenReturn(jwt);
        when(userRepository.findByGoogleSub("google-sub-123")).thenReturn(Optional.of(existing));
        when(authService.buildAuthResponse(existing)).thenReturn(mock(AuthResponse.class));

        googleAuthService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN));

        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void loginWithGoogle_emailNotVerifiedClaim_throwsBadCredentials() {
        init();
        Jwt jwt = Jwt.withTokenValue(ID_TOKEN)
                .header("alg", "RS256")
                .subject("google-sub-123")
                .claim("email", "user@example.com")
                .claim("email_verified", false)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(googleIdTokenDecoder.decode(ID_TOKEN)).thenReturn(jwt);

        assertThatThrownBy(() -> googleAuthService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginWithGoogle_decoderThrowsJwtException_throwsBadCredentials() {
        init();
        when(googleIdTokenDecoder.decode(ID_TOKEN)).thenThrow(new JwtException("bad token"));

        assertThatThrownBy(() -> googleAuthService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginWithGoogle_inactiveUser_throwsDisabled() {
        init();
        Jwt jwt = buildJwt(Map.of());
        User existing = User.builder()
                .id(9L)
                .email("user@example.com")
                .role(UserRole.APPLICANT)
                .authProvider(AuthProvider.GOOGLE)
                .googleSub("google-sub-123")
                .active(false)
                .emailVerified(true)
                .build();
        when(googleIdTokenDecoder.decode(ID_TOKEN)).thenReturn(jwt);
        when(userRepository.findByGoogleSub("google-sub-123")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> googleAuthService.loginWithGoogle(new GoogleLoginRequest(ID_TOKEN)))
                .isInstanceOf(DisabledException.class);
    }
}
