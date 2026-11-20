package com.example.chat_app_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.chat_app_backend.admin.Report;
import com.example.chat_app_backend.admin.ReportRepository;
import com.example.chat_app_backend.auth.EmailService;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import com.example.chat_app_backend.user.User;
import com.example.chat_app_backend.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Calls every REST endpoint once with a realistic request and checks the status code. A failure
 * lists every endpoint that answered differently, so one run shows the whole picture.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndpointCoverageTest {
  static final String PASSWORD = "SecurePass123!";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired ReportRepository reports;
  @Autowired PasswordEncoder passwords;
  @Autowired JwtUtils jwt;
  @MockBean EmailService email;

  final List<String> failures = new ArrayList<>();
  int checked;

  User user(String role) {
    String name = "e" + UUID.randomUUID().toString().substring(0, 10);
    return users.saveAndFlush(
        new User(name, name + "@test.com", passwords.encode(PASSWORD), role, "Name " + name));
  }

  String token(User u) {
    return jwt.generateTokenFromUsername(u.getUsername());
  }

  String json(Object body) throws Exception {
    return mapper.writeValueAsString(body);
  }

  MvcResult call(String label, MockHttpServletRequestBuilder req, String token, int... expected)
      throws Exception {
    if (token != null) req.header("Authorization", "Bearer " + token);
    MvcResult result = mvc.perform(req).andReturn();
    int status = result.getResponse().getStatus();
    checked++;
    boolean ok = false;
    for (int e : expected) ok |= e == status;
    if (!ok) {
      failures.add(
          label
              + " -> "
              + status
              + " (expected "
              + java.util.Arrays.toString(expected)
              + ") "
              + result.getResponse().getContentAsString());
    }
    return result;
  }

  MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder req, Object body)
      throws Exception {
    return req.contentType(MediaType.APPLICATION_JSON).content(json(body));
  }

  JsonNode read(MvcResult r) throws Exception {
    String s = r.getResponse().getContentAsString();
    return s.isBlank() ? mapper.createObjectNode() : mapper.readTree(s);
  }

  @Test
  void everyEndpointAnswersAsDocumented() throws Exception {
    User alice = user("ROLE_USER");
    User bob = user("ROLE_USER");
    User carol = user("ROLE_USER");
    User admin = user("ROLE_ADMIN");
    String a = token(alice), b = token(bob), adm = token(admin);

    // ---- auth
    call("GET csrf", get("/api/v1/auth/csrf"), null, 200);
    String regName = "r" + UUID.randomUUID().toString().substring(0, 10);
    call(
        "POST register",
        body(
            post("/api/v1/auth/register"),
            Map.of("username", regName, "email", regName + "@test.com", "password", PASSWORD)),
        null,
        201);
    MvcResult login =
        call(
            "POST login",
            body(
                post("/api/v1/auth/login"),
                Map.of("username", alice.getUsername(), "password", PASSWORD)),
            null,
            200);
    String refresh = read(login).path("refreshToken").asText();
    call(
        "POST refresh",
        body(post("/api/v1/auth/refresh"), Map.of("refreshToken", refresh)),
        null,
        200);
    call(
        "POST forgot-password",
        body(post("/api/v1/auth/forgot-password"), Map.of("email", "nobody@test.com")),
        null,
        200,
        202,
        204);
    call(
        "POST reset-password bad token",
        body(
            post("/api/v1/auth/reset-password"),
            Map.of("token", "invalid", "newPassword", "NewPass123!")),
        null,
        401);
    call(
        "POST verify-email bad token",
        body(post("/api/v1/auth/verify-email"), Map.of("token", "invalid")),
        null,
        400,
        401);
    call(
        "POST resend-verification",
        body(post("/api/v1/auth/resend-verification"), Map.of("email", alice.getEmail())),
        null,
        200,
        202,
        204);
    call(
        "POST change-password wrong current",
        body(
            post("/api/v1/auth/change-password"),
            Map.of("currentPassword", "wrong", "newPassword", "NewPass123!")),
        a,
        401);

    // ---- users
    call("GET users/me", get("/api/v1/users/me"), a, 200);
    call(
        "PATCH users/me/profile",
        body(patch("/api/v1/users/me/profile"), Map.of("bio", "hello", "displayName", "Alice")),
        a,
        200);
    call("GET users/me/sessions", get("/api/v1/users/me/sessions"), a, 200);
    call(
        "DELETE users/me/sessions/{id} unknown",
        delete("/api/v1/users/me/sessions/" + UUID.randomUUID()),
        a,
        404,
        403,
        204,
        200);
    call(
        "GET users/presence",
        get("/api/v1/users/presence").param("ids", bob.getId().toString()),
        a,
        200);
    call("GET users/search", get("/api/v1/users/search").param("q", alice.getUsername()), a, 200);
    call("GET users/search missing q", get("/api/v1/users/search"), a, 400);
    MvcResult other = call("GET users/{id}", get("/api/v1/users/" + bob.getId()), a, 200);
    assertThat(read(other).path("email").isNull()).as("other user's email is hidden").isTrue();
    call("GET users/{id} unknown", get("/api/v1/users/" + UUID.randomUUID()), a, 404);

    // ---- conversations
    MvcResult dm =
        call(
            "POST conversations/direct",
            body(post("/api/v1/conversations/direct"), Map.of("targetUserId", bob.getId())),
            a,
            200,
            201);
    String dmId = read(dm).path("id").asText();
    MvcResult group =
        call(
            "POST conversations/group",
            body(
                post("/api/v1/conversations/group"),
                Map.of("title", "Team", "memberIds", List.of(bob.getId()))),
            a,
            200,
            201);
    String gid = read(group).path("id").asText();
    call("GET conversations", get("/api/v1/conversations"), a, 200);
    call("GET conversations?q=", get("/api/v1/conversations").param("q", "team"), a, 200);
    call("GET conversations/unread-count", get("/api/v1/conversations/unread-count"), a, 200);
    call("GET conversations/{id}", get("/api/v1/conversations/" + gid), a, 200);
    call(
        "GET conversations/{id} non-member",
        get("/api/v1/conversations/" + gid),
        token(carol),
        403);
    call(
        "PATCH conversations/{id}",
        body(patch("/api/v1/conversations/" + gid), Map.of("title", "Team 2")),
        a,
        200);
    call("GET members", get("/api/v1/conversations/" + gid + "/members"), a, 200);
    call(
        "POST members",
        body(
            post("/api/v1/conversations/" + gid + "/members"),
            Map.of("memberIds", List.of(carol.getId()))),
        a,
        200,
        204);
    call(
        "PATCH member role",
        body(
            patch("/api/v1/conversations/" + gid + "/members/" + bob.getId() + "/role"),
            Map.of("role", "ADMIN")),
        a,
        200,
        204);
    call(
        "DELETE member by admin cannot remove owner",
        delete("/api/v1/conversations/" + gid + "/members/" + alice.getId()),
        b,
        403);
    call(
        "DELETE member",
        delete("/api/v1/conversations/" + gid + "/members/" + carol.getId()),
        a,
        200,
        204);
    call(
        "PUT mute",
        body(
            put("/api/v1/conversations/" + gid + "/mute"),
            Map.of("mutedUntil", Instant.now().plusSeconds(3600).toString())),
        a,
        200,
        204);
    call("DELETE mute", delete("/api/v1/conversations/" + gid + "/mute"), a, 200, 204);
    call("PUT pin", put("/api/v1/conversations/" + gid + "/pin"), a, 200, 204);
    call("DELETE pin", delete("/api/v1/conversations/" + gid + "/pin"), a, 200, 204);
    call("PUT archive", put("/api/v1/conversations/" + gid + "/archive"), a, 200, 204);
    call("DELETE archive", delete("/api/v1/conversations/" + gid + "/archive"), a, 200, 204);

    // ---- messages
    MvcResult sent =
        call(
            "POST messages",
            body(
                post("/api/v1/conversations/" + gid + "/messages"),
                Map.of(
                    "conversationId",
                    gid,
                    "clientMessageId",
                    "m-1",
                    "type",
                    "TEXT",
                    "content",
                    "hello team")),
            a,
            200,
            201);
    String mid = read(sent).path("id").asText();
    long seq = read(sent).path("seq").asLong();
    call("GET messages", get("/api/v1/conversations/" + gid + "/messages"), a, 200);
    call(
        "GET messages search",
        get("/api/v1/conversations/" + gid + "/messages").param("q", "hello"),
        a,
        200);
    call(
        "POST read",
        body(post("/api/v1/conversations/" + gid + "/read"), Map.of("upToSeq", seq)),
        b,
        200,
        204);
    call("GET message", get("/api/v1/messages/" + mid), a, 200);
    call(
        "PATCH message",
        body(patch("/api/v1/messages/" + mid), Map.of("content", "hello edited")),
        a,
        200);
    call(
        "PATCH message by non-sender",
        body(patch("/api/v1/messages/" + mid), Map.of("content", "hijack")),
        b,
        403);
    call(
        "POST reaction",
        body(post("/api/v1/messages/" + mid + "/reactions"), Map.of("emoji", "👍")),
        b,
        200,
        201,
        204);
    call("DELETE reaction", delete("/api/v1/messages/" + mid + "/reactions/👍"), b, 200, 204);
    MvcResult second =
        call(
            "POST messages 2",
            body(
                post("/api/v1/conversations/" + gid + "/messages"),
                Map.of("conversationId", gid, "clientMessageId", "m-2", "content", "bye")),
            a,
            200,
            201);
    call("DELETE message", delete("/api/v1/messages/" + mid), a, 200, 204);

    // ---- media (storage is not configured in tests: 422 BUSINESS_RULE, never 500)
    call(
        "POST media/presign",
        body(post("/api/v1/media/presign"), Map.of("mimeType", "image/png", "sizeInBytes", 1024)),
        a,
        200,
        422);
    call(
        "POST media/confirm",
        body(post("/api/v1/media/confirm"), Map.of("storageKey", "users/" + alice.getId() + "/x")),
        a,
        200,
        404,
        422);
    call(
        "GET media/download unknown",
        get("/api/v1/media/download").param("storageKey", "users/x/y"),
        a,
        404);

    // ---- AI (Gemini is disabled in tests: 422 "AI assistant is not configured")
    call("GET ai/conversations", get("/api/v1/ai/conversations"), a, 200);
    call("POST ai/conversation", post("/api/v1/ai/conversation"), a, 200);
    call(
        "POST ai/chat",
        body(post("/api/v1/ai/chat"), Map.of("conversationId", UUID.randomUUID(), "prompt", "hi")),
        a,
        422);
    call(
        "POST ai/chat/regenerate",
        body(
            post("/api/v1/ai/chat/regenerate"),
            Map.of("conversationId", gid, "messageId", read(second).path("id").asText())),
        a,
        422);
    call(
        "POST ai/actions",
        body(post("/api/v1/ai/actions"), Map.of("action", "LIST_CONVERSATIONS")),
        a,
        200);
    call(
        "POST ai/actions get my profile",
        body(post("/api/v1/ai/actions"), Map.of("action", "GET_MY_PROFILE")),
        a,
        200);
    call(
        "DELETE ai/conversations/{id} normal chat",
        delete("/api/v1/ai/conversations/" + gid),
        a,
        422);
    call("DELETE ai/conversation", delete("/api/v1/ai/conversation"), a, 204);

    // ---- admin
    Report report = new Report();
    report.setReporterId(bob.getId());
    report.setTargetType("USER");
    report.setTargetId(carol.getId());
    report.setReason("SPAM");
    report = reports.saveAndFlush(report);
    call("GET admin as user", get("/api/v1/admin/stats/overview"), a, 403);
    call("GET admin/stats/overview", get("/api/v1/admin/stats/overview"), adm, 200);
    call("GET admin/users", get("/api/v1/admin/users"), adm, 200);
    call("GET admin/users/{id}", get("/api/v1/admin/users/" + carol.getId()), adm, 200);
    call(
        "PATCH admin/users/{id}/status",
        body(
            patch("/api/v1/admin/users/" + carol.getId() + "/status"),
            Map.of("status", "DISABLED", "reason", "test")),
        adm,
        200);
    call(
        "POST admin/users/{id}/force-logout",
        post("/api/v1/admin/users/" + carol.getId() + "/force-logout"),
        adm,
        200);
    call("GET admin/conversations", get("/api/v1/admin/conversations"), adm, 200);
    call(
        "PATCH admin/conversations/{id}/status",
        body(
            patch("/api/v1/admin/conversations/" + dmId + "/status"),
            Map.of("status", "ARCHIVED", "reason", "test")),
        adm,
        200);
    call(
        "POST message to archived conversation",
        body(
            post("/api/v1/conversations/" + dmId + "/messages"),
            Map.of("conversationId", dmId, "clientMessageId", "m-3", "content", "x")),
        a,
        422);
    call("GET admin/reports", get("/api/v1/admin/reports"), adm, 200);
    call("GET admin/reports/{id}", get("/api/v1/admin/reports/" + report.getId()), adm, 200);
    call(
        "PATCH admin/reports/{id}",
        body(
            patch("/api/v1/admin/reports/" + report.getId()),
            Map.of("resolution", "handled", "action", "WARN")),
        adm,
        200);
    call(
        "DELETE admin/messages/{id}",
        delete("/api/v1/admin/messages/" + read(second).path("id").asText())
            .param("reason", "spam"),
        adm,
        200);
    call(
        "DELETE admin/messages/{id} missing reason",
        delete("/api/v1/admin/messages/" + UUID.randomUUID()),
        adm,
        400);
    MvcResult audit = call("GET admin/audit-logs", get("/api/v1/admin/audit-logs"), adm, 200);
    assertThat(read(audit).path("items").size()).as("admin actions are audited").isPositive();
    call(
        "POST admin/transfer-ownership wrong password",
        body(
            post("/api/v1/admin/transfer-ownership"),
            Map.of("targetUserId", bob.getId(), "password", "wrong")),
        adm,
        401,
        403);
    call("DELETE admin/users/{id}", delete("/api/v1/admin/users/" + carol.getId()), adm, 200);

    // ---- leaving and deactivation last (they end the session)
    call("DELETE conversations/{id} leave", delete("/api/v1/conversations/" + gid), b, 204);
    call("POST logout-all", post("/api/v1/auth/logout-all"), a, 200, 204, 401);
    call("POST logout", post("/api/v1/auth/logout"), a, 204);
    call("DELETE users/me", delete("/api/v1/users/me"), b, 200);
    call("DELETE users/me as admin", delete("/api/v1/users/me"), adm, 422);

    // ---- protocol errors are client errors
    call("PUT users/me method not allowed", put("/api/v1/users/me"), a, 405);
    call(
        "POST login wrong media type",
        post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"),
        null,
        415);

    assertThat(failures).as("endpoints answering unexpectedly (" + checked + " checked)").isEmpty();
  }
}
