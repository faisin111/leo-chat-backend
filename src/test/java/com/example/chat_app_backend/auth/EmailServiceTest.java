package com.example.chat_app_backend.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

class EmailServiceTest {

  private HttpServer server;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void sendsVerificationMessageToConfiguredFormspreeEndpoint() throws Exception {
    CountDownLatch received = new CountDownLatch(1);
    AtomicReference<String> body = new AtomicReference<>();
    server.createContext(
        "/form",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(200, 0);
          exchange.getResponseBody().close();
          received.countDown();
        });
    server.start();

    EmailProperties properties =
        new EmailProperties(
            "http://localhost:" + server.getAddress().getPort() + "/form",
            "https://leo-chat.example",
            "noreply@example.com");
    EmailService service =
        new EmailService(mock(JavaMailSender.class), RestClient.builder(), properties);

    service.sendVerificationEmail("alice@example.com", "verification-token");

    assertTrue(received.await(5, TimeUnit.SECONDS));
    assertTrue(body.get().contains("alice@example.com"));
    assertTrue(
        body.get().contains("https://leo-chat.example/verify-email?token=verification-token"));
    assertTrue(body.get().contains("Verify your Leo Chat account"));
  }
}
