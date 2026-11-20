package com.example.chat_app_backend.auth;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@EnableConfigurationProperties(EmailProperties.class)
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);
  private final JavaMailSender mailSender;
  private final RestClient restClient;
  private final EmailProperties properties;

  public EmailService(
      JavaMailSender mailSender, RestClient.Builder restClientBuilder, EmailProperties properties) {
    this.mailSender = mailSender;
    this.restClient = restClientBuilder.build();
    this.properties = properties;
  }

  @Async
  public void sendVerificationEmail(String toEmail, String token) {
    String verificationUrl = properties.publicUrl() + "/verify-email?token=" + token;
    sendEmail(
        toEmail,
        "Verify your Leo Chat account",
        "Welcome to Leo Chat!\n\n"
            + "Please click the link below to verify your email address:\n"
            + verificationUrl
            + "\n\nThis link will expire in 24 hours.",
        "verification_email_delivery_failed");
  }

  @Async
  public void sendPasswordResetEmail(String toEmail, String token) {
    String resetUrl = properties.publicUrl() + "/reset-password?token=" + token;
    sendEmail(
        toEmail,
        "Password Reset Request",
        "You requested a password reset.\n\n"
            + "Click here to reset your password:\n"
            + resetUrl
            + "\n\nIf you did not request this, please ignore this email.",
        "password_reset_email_delivery_failed");
  }

  private void sendEmail(String toEmail, String subject, String body, String failureEvent) {
    if (properties.formspreeEndpoint() != null && !properties.formspreeEndpoint().isBlank()) {
      try {
        restClient
            .post()
            .uri(properties.formspreeEndpoint())
            .body(
                Map.of(
                    "email", toEmail,
                    "_replyto", toEmail,
                    "_subject", subject,
                    "_autoresponse", body,
                    "message", body))
            .retrieve()
            .toBodilessEntity();
      } catch (RestClientException e) {
        log.warn(failureEvent);
      }
      return;
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(properties.fromAddress());
    message.setTo(toEmail);
    message.setSubject(subject);
    message.setText(body);
    try {
      mailSender.send(message);
    } catch (MailException e) {
      log.warn(failureEvent);
    }
  }
}
