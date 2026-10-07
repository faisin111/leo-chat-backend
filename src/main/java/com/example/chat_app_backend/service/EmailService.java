package com.example.chat_app_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

  @Autowired private JavaMailSender mailSender;

  @Value("${app.public.url:http://localhost:8080}")
  private String publicUrl;

  @Value("${spring.mail.username:noreply@example.com}")
  private String fromAddress;

  @Async
  public void sendVerificationEmail(String toEmail, String token) {
    String verificationUrl = publicUrl + "/verify-email?token=" + token;
        System.out.println("\n=== DEV MODE: VERIFICATION TOKEN ===\n" + token + "\n====================================\n");

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
      System.out.println("Email successfully sent to " + toEmail);
    } catch (Exception e) {
      System.err.println("Failed to send email to " + toEmail + ": " + e.getMessage());
      // We log but don't crash, so the token is still saved and can be retried
    }
  }

  @Async
  public void sendPasswordResetEmail(String toEmail, String token) {
    String resetUrl = publicUrl + "/reset-password?token=" + token;
    System.out.println("\n=== DEV MODE: PASSWORD RESET TOKEN ===\n" + token + "\n======================================\n");

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
    } catch (Exception e) {
      System.err.println("Failed to send password reset email to " + toEmail);
    }
  }
}
