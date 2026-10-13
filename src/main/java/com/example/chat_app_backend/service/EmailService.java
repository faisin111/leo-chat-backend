package com.example.chat_app_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);
  private final JavaMailSender mailSender;

  public EmailService(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  @Value("${app.public.url:http://localhost:3000}")
  private String publicUrl;

  @Value("${app.mail.from-address:noreply@example.com}")
  private String fromAddress;

  @Async
  public void sendVerificationEmail(String toEmail, String token) {
    String verificationUrl = publicUrl + "/verify-email?token=" + token;

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(toEmail);
    message.setSubject("Verify your Leo Chat account");
    message.setText(
        "Welcome to Leo Chat!\n\n"
            + "Please click the link below to verify your email address:\n"
            + verificationUrl
            + "\n\n"
            + "This link will expire in 24 hours.");

    try {
      mailSender.send(message);
    } catch (MailException e) {
      log.warn("verification_email_delivery_failed");
      // We log but don't crash, so the token is still saved and can be retried
    }
  }

  @Async
  public void sendPasswordResetEmail(String toEmail, String token) {
    String resetUrl = publicUrl + "/reset-password?token=" + token;

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(toEmail);
    message.setSubject("Password Reset Request");
    message.setText(
        "You requested a password reset.\n\n"
            + "Click here to reset your password:\n"
            + resetUrl
            + "\n\n"
            + "If you did not request this, please ignore this email.");

    try {
      mailSender.send(message);
    } catch (MailException e) {
      log.warn("password_reset_email_delivery_failed");
    }
  }
}
