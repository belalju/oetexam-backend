package com.oet.auth.service;

import com.oet.auth.dto.AuthResponse;
import com.oet.auth.dto.GoogleLoginRequest;
import com.oet.user.entity.AuthProvider;
import com.oet.user.entity.User;
import com.oet.user.entity.UserRole;
import com.oet.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
public class GoogleAuthService {

    private final JwtDecoder googleIdTokenDecoder;
    private final UserRepository userRepository;
    private final AuthService authService;

    public GoogleAuthService(@Qualifier("googleIdTokenDecoder") JwtDecoder googleIdTokenDecoder,
                              UserRepository userRepository,
                              AuthService authService) {
        this.googleIdTokenDecoder = googleIdTokenDecoder;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        Jwt jwt;
        try {
            jwt = googleIdTokenDecoder.decode(request.idToken());
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid Google token");
        }
        if (!Boolean.TRUE.equals(jwt.getClaim("email_verified"))) {
            throw new BadCredentialsException("Google email is not verified");
        }
        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email").toLowerCase();

        User user = userRepository.findByGoogleSub(sub)
                .or(() -> userRepository.findByEmail(email).map(existing -> {
                    existing.setGoogleSub(sub);
                    existing.setEmailVerified(true);
                    return existing;
                }))
                .orElseGet(() -> createGoogleUser(jwt, sub, email));

        if (!user.isActive()) {
            throw new DisabledException("Account is disabled");
        }
        return authService.buildAuthResponse(user);
    }

    private User createGoogleUser(Jwt jwt, String sub, String email) {
        String firstName = firstNonBlank(jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("name"), email.substring(0, email.indexOf('@')));
        String lastName = Objects.requireNonNullElse(jwt.getClaimAsString("family_name"), "");
        log.info("New Google user: {}", email);
        return userRepository.save(User.builder()
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .role(UserRole.APPLICANT)
                .authProvider(AuthProvider.GOOGLE)
                .googleSub(sub)
                .emailVerified(true)
                .build());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
