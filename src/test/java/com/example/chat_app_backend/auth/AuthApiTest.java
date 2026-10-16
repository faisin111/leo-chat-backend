package com.example.chat_app_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import com.example.chat_app_backend.service.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {
  private static final String PASSWORD = "SecurePass123!";
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired JwtUtils jwt;
  @Autowired ObjectMapper mapper;
  @MockBean EmailService email;
  User user;

  @BeforeEach
  void setup() {
    user =
        users.saveAndFlush(
            new User(
                "auth" + UUID.randomUUID().toString().substring(0, 8),
                UUID.randomUUID() + "@test.com",
                passwords.encode(PASSWORD),
                "ROLE_USER",
                "Test"));
  }

  String json(Map<String, ?> body) throws Exception {
    return mapper.writeValueAsString(body);
  }

  JsonNode login() throws Exception {
    var result =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("username", user.getUsername(), "password", PASSWORD))))
            .andExpect(status().isOk())
            .andExpect(header().exists("Set-Cookie"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("tokenType").value("Bearer"))
            .andExpect(jsonPath("expiresIn").value(900))
            .andReturn();
    assertThat(result.getRequest().getSession(false)).isNull();
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void tokenLoginDoesNotNeedCsrfBootstrap() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", user.getUsername(), "password", PASSWORD))))
        .andExpect(status().isOk());
  }

  @Test
  void loginReturnsBearerTokensAndHeaderAuthenticationWorks() throws Exception {
    var tokens = login();
    String access = tokens.get("accessToken").asText();
    assertThat(jwt.validateJwtToken(access)).isTrue();
    assertThat(jwt.validateRefreshToken(tokens.get("refreshToken").asText())).isTrue();
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/users/me").cookie(new Cookie("leo_chat_jwt", access)))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/users/me")
                .cookie(new Cookie("leo_chat_jwt", access))
                .header("Authorization", "Bearer "))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/users/me")
                .cookie(new Cookie("leo_chat_jwt", access))
                .header("Authorization", "Bearer stale-token"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/users/me")
                .header("Authorization", "Bearer " + tokens.get("refreshToken").asText()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshPreservesExpiryAndAllowsOldTokenReuse() throws Exception {
    var initial = login();
    String refresh = initial.get("refreshToken").asText();
    var result =
        mvc.perform(
                post("/api/v1/auth/refresh")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("refreshToken", refresh))))
            .andExpect(status().isOk())
            .andExpect(header().exists("Set-Cookie"))
            .andReturn();
    var next = mapper.readTree(result.getResponse().getContentAsString());
    assertThat(next.get("refreshToken").asText()).isNotEqualTo(refresh);
    assertThat(jwt.refreshExpiresAt(next.get("refreshToken").asText()))
        .isEqualTo(jwt.refreshExpiresAt(refresh));
    mvc.perform(
            post("/api/v1/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refresh))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", initial.get("accessToken").asText()))))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .with(csrf())
                .cookie(new Cookie("leo_chat_jwt_refresh", refresh)))
        .andExpect(status().isOk());
  }

  @Test
  void logoutIsClientOnlyAndProtectedRoutesRequireBearer() throws Exception {
    String access = login().get("accessToken").asText();
    mvc.perform(post("/api/v1/auth/logout").with(csrf())).andExpect(status().isNoContent());
    mvc.perform(post("/api/v1/auth/logout-all").with(csrf())).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/change-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/logout").with(csrf()).header("Authorization", "Bearer " + access))
        .andExpect(status().isNoContent())
        .andExpect(header().exists("Set-Cookie"));
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/auth/logout-all")
                .with(csrf())
                .header("Authorization", "Bearer " + access))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("code").value("BUSINESS_RULE"));
  }

  @Test
  void inactiveAccountsCannotLoginRefreshOrCallProtectedRoutes() throws Exception {
    var tokens = login();
    for (String state : List.of("DISABLED", "BANNED", "DELETED")) {
      user.setStatus(state);
      users.saveAndFlush(user);
      mvc.perform(
              post("/api/v1/auth/login")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(Map.of("username", user.getUsername(), "password", PASSWORD))))
          .andExpect(status().isUnauthorized());
      mvc.perform(
              get("/api/v1/users/me")
                  .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
          .andExpect(status().isUnauthorized());
      mvc.perform(
              post("/api/v1/auth/refresh")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(Map.of("refreshToken", tokens.get("refreshToken").asText()))))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void recoveryDoesNotExposeTokensOrAccountExistence() throws Exception {
    for (String path :
        List.of("/api/v1/auth/forgot-password", "/api/v1/auth/resend-verification")) {
      String known =
          mvc.perform(
                  post(path)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(json(Map.of("email", user.getEmail()))))
              .andExpect(status().isAccepted())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String unknown =
          mvc.perform(
                  post(path)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(json(Map.of("email", "unknown@test.com"))))
              .andExpect(status().isAccepted())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(known).isEqualTo(unknown).doesNotContain("TOKEN", "token", user.getEmail());
    }
  }

  @Test
  void passwordChangeAndResetKeepIssuedJwtValid() throws Exception {
    var tokens = login();
    String access = tokens.get("accessToken").asText();
    mvc.perform(
            post("/api/v1/auth/change-password")
                .with(csrf())
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(Map.of("currentPassword", "wrong", "newPassword", "AnotherPass123!"))))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/change-password")
                .with(csrf())
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(Map.of("currentPassword", PASSWORD, "newPassword", "AnotherPass123!"))))
        .andExpect(status().isOk());
    user = users.findById(user.getId()).orElseThrow();
    String reset = jwt.generatePasswordResetToken(user.getEmail(), user.getPasswordHash());
    mvc.perform(
            post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", reset, "newPassword", "ResetPass123!"))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/auth/reset-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", reset, "newPassword", "ResetAgain123!"))))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", tokens.get("refreshToken").asText()))))
        .andExpect(status().isOk());
  }

  @Test
  void registrationCannotGrantAdminAndVerificationWorks() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "username",
                            "new" + UUID.randomUUID().toString().substring(0, 8),
                            "email",
                            UUID.randomUUID() + "@test.com",
                            "password",
                            PASSWORD,
                            "role",
                            "ROLE_ADMIN"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("role").value("ROLE_USER"));
    mvc.perform(
            post("/api/v1/auth/verify-email")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(Map.of("token", jwt.generateEmailVerificationToken(user.getEmail())))))
        .andExpect(status().isOk());
    assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
  }

  @Test
  void concurrentRegistrationReturnsCreatedAndConflict() throws Exception {
    String body =
        json(
            Map.of(
                "username",
                "race" + UUID.randomUUID().toString().substring(0, 8),
                "email",
                UUID.randomUUID() + "@test.com",
                "password",
                PASSWORD));
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CyclicBarrier barrier = new CyclicBarrier(2);
    try {
      Callable<Integer> register =
          () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return mvc.perform(
                    post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var first = executor.submit(register);
      var second = executor.submit(register);
      assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void openApiShowsPublicAndProtectedAuthContracts() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.operationId").value("auth_login"))
        .andExpect(
            jsonPath("$.paths['/api/v1/auth/change-password'].post.security[0].bearerAuth")
                .isArray())
        .andExpect(jsonPath("$.components.schemas.TokenResponse.properties.expiresIn").exists());
  }
}
