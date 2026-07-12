package com.oet.auth.service;

import com.oet.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Async
    public void sendVerificationEmail(User user, String token) {
        String link = frontendBaseUrl + "/auth/verify-email?token=" + token;
        log.debug("Verification link for {}: {}", user.getEmail(), link);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(user.getEmail());
        message.setSubject("Verify your OET Practice email address");
        message.setText("""
                Hi %s,

                Please verify your email address by clicking the link below:
                %s

                This link will expire in 24 hours.

                If you did not create an account, you can safely ignore this email.
                """.formatted(user.getFirstName(), link));

        mailSender.send(message);
    }
}
