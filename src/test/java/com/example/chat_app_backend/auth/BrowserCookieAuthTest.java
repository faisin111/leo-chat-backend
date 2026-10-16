package com.example.chat_app_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BrowserCookieAuthTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired ObjectMapper mapper;
  @MockBean EmailService email;

  Cookie cookie(MvcResult response, String name) {
    String header =
        response.getResponse().getHeaders("Set-Cookie").stream()
            .filter(h -> h.startsWith(name + "="))
            .findFirst()
            .orElseThrow();
    return new Cookie(name, header.substring(name.length() + 1, header.indexOf(';')));
  }

  @Test
  void browserUsesCookiesForResourcesRefreshAndLogoutWithCsrf() throws Exception {
    User user =
        users.saveAndFlush(
            new User(
                "cookie" + UUID.randomUUID().toString().substring(0, 8),
                UUID.randomUUID() + "@test.com",
                passwords.encode("SecurePass123!"),
                "ROLE_USER",
                "Test"));
    MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
    String xsrf = mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
    Cookie xsrfCookie = cookie(csrf, "XSRF-TOKEN");
    String body =
        mapper.writeValueAsString(
            Map.of("username", user.getUsername(), "password", "SecurePass123!"));
    MvcResult login =
        mvc.perform(
                post("/api/v1/auth/login")
                    .cookie(xsrfCookie)
                    .header("X-XSRF-TOKEN", xsrf)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();
    Cookie access = cookie(login, "leo_chat_jwt"), refresh = cookie(login, "leo_chat_jwt_refresh");
    assertThat(login.getResponse().getHeaders("Set-Cookie"))
        .allMatch(h -> h.contains("HttpOnly") && h.contains("SameSite=Lax"));
    mvc.perform(get("/api/v1/users/me").cookie(access)).andExpect(status().isOk());
    mvc.perform(post("/api/v1/auth/logout").cookie(access)).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/auth/logout")
                .cookie(access, xsrfCookie)
                .header("Authorization", "Bearer invalid"))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/v1/auth/refresh").cookie(refresh, xsrfCookie).header("X-XSRF-TOKEN", xsrf))
        .andExpect(status().isOk())
        .andExpect(header().exists("Set-Cookie"));
    MvcResult logout =
        mvc.perform(
                post("/api/v1/auth/logout").cookie(access, xsrfCookie).header("X-XSRF-TOKEN", xsrf))
            .andExpect(status().isNoContent())
            .andReturn();
    assertThat(
            logout.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("leo_chat_jwt"))
                .toList())
        .hasSize(3)
        .allMatch(h -> h.contains("Max-Age=0") && h.contains("HttpOnly"));
    assertThat(logout.getResponse().getHeaders("Set-Cookie"))
        .anyMatch(h -> h.contains("Path=/api/v1/auth/refresh"));
    mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutCanClearExpiredCookiesWithoutAnAuthenticatedAccount() throws Exception {
    MvcResult csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
    String token = mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
    mvc.perform(
            post("/api/v1/auth/logout")
                .cookie(cookie(csrf, "XSRF-TOKEN"), new Cookie("leo_chat_jwt", "expired"))
                .header("X-XSRF-TOKEN", token))
        .andExpect(status().isNoContent())
        .andExpect(header().exists("Set-Cookie"));
  }

  @Test
  void corsAllowsConfiguredBrowserCredentials() throws Exception {
    mvc.perform(get("/api/v1/auth/csrf").header("Origin", "http://localhost:3000"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    mvc.perform(get("/api/v1/auth/csrf").header("Origin", "https://untrusted.example"))
        .andExpect(status().isForbidden());
  }
}
