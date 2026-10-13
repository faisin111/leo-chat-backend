package com.example.chat_app_backend.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.chat_app_backend.chat.conversation.Conversation;
import com.example.chat_app_backend.chat.conversation.ConversationMember;
import com.example.chat_app_backend.chat.conversation.ConversationMemberId;
import com.example.chat_app_backend.chat.conversation.ConversationMemberRepository;
import com.example.chat_app_backend.chat.conversation.ConversationRepository;
import com.example.chat_app_backend.chat.conversation.ConversationType;
import com.example.chat_app_backend.chat.message.MessageReaction;
import com.example.chat_app_backend.chat.message.MessageReactionRepository;
import com.example.chat_app_backend.chat.message.MessageRepository;
import com.example.chat_app_backend.chat.message.MessageService;
import com.example.chat_app_backend.chat.message.dto.AddReactionRequest;
import com.example.chat_app_backend.chat.message.dto.EditMessageRequest;
import com.example.chat_app_backend.chat.message.dto.MessageResponse;
import com.example.chat_app_backend.chat.message.dto.ReadMessageRequest;
import com.example.chat_app_backend.chat.message.dto.SendMessageRequest;
import com.example.chat_app_backend.model.User;
import com.example.chat_app_backend.repository.UserRepository;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageFlowTest {
  @Autowired ObjectMapper objectMapper;
  @Autowired MessageService service;
  @Autowired ConversationRepository conversations;
  @Autowired ConversationMemberRepository members;
  @Autowired MessageRepository messages;
  @Autowired MessageReactionRepository reactions;
  @Autowired UserRepository users;
  @Autowired JwtUtils jwt;
  @Autowired MockMvc mvc;
  @LocalServerPort int port;
  @Autowired SimpUserRegistry userRegistry;
  UUID userId;
  UUID convId;
  String token;

  @BeforeEach
  void setup() {
    User user =
        users.saveAndFlush(
            new User(
                "u" + UUID.randomUUID(),
                UUID.randomUUID() + "@test.com",
                "hash",
                "ROLE_USER",
                "Test"));
    userId = user.getId();
    token = jwt.generateTokenFromUsername(user.getUsername());
    Conversation c = new Conversation();
    c.setType(ConversationType.DIRECT);
    c.setCreatedBy(userId);
    convId = conversations.saveAndFlush(c).getId();
    ConversationMember member = new ConversationMember();
    member.setId(new ConversationMemberId(convId, userId));
    members.saveAndFlush(member);
  }

  SendMessageRequest request(String key) {
    return new SendMessageRequest(convId, key, "TEXT", " hello ", null);
  }

  @Test
  void websocketSendAcknowledgesAndBroadcastsPersistedMessage() throws Exception {
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.initialize();
    client.setTaskScheduler(scheduler);
    MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
    converter.setObjectMapper(objectMapper);
    client.setMessageConverter(converter);
    StompSession session = null;
    try {
      StompHeaders connect = new StompHeaders();
      connect.set("Authorization", "Bearer " + token);
      session =
          client
              .connectAsync(
                  "ws://localhost:" + port + "/ws",
                  new WebSocketHttpHeaders(),
                  connect,
                  new StompSessionHandlerAdapter() {})
              .get(10, TimeUnit.SECONDS);
      BlockingQueue<MessageResponse> acknowledgements = new LinkedBlockingQueue<>();
      BlockingQueue<MessageResponse> broadcasts = new LinkedBlockingQueue<>();
      session.subscribe("/user/queue/acks", handler(acknowledgements));
      session.subscribe("/topic/conversations." + convId, handler(broadcasts));
      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .until(
              () ->
                  userRegistry.getUsers().stream()
                          .flatMap(u -> u.getSessions().stream())
                          .flatMap(s -> s.getSubscriptions().stream())
                          .filter(s -> s.getDestination().equals("/topic/conversations." + convId))
                          .count()
                      == 1);
      session.send("/app/chat.send", request("ws"));
      MessageResponse ack = acknowledgements.poll(10, TimeUnit.SECONDS);
      MessageResponse broadcast = broadcasts.poll(10, TimeUnit.SECONDS);
      assertThat(ack).isNotNull();
      assertThat(broadcast).isNotNull();
      assertThat(broadcast.id()).isEqualTo(ack.id());
      assertThat(messages.findById(ack.id())).isPresent();
    } finally {
      if (session != null && session.isConnected()) session.disconnect();
      client.stop();
      scheduler.shutdown();
    }
  }

  @Test
  void websocketAcceptsAuthenticatedBrowserCookieHandshake() throws Exception {
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    StompSession session = null;
    try {
      WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
      headers.set("Cookie", "leo_chat_jwt=" + token);
      headers.setOrigin("http://localhost:3000");
      session =
          client
              .connectAsync(
                  "ws://localhost:" + port + "/ws",
                  headers,
                  new StompHeaders(),
                  new StompSessionHandlerAdapter() {})
              .get(10, TimeUnit.SECONDS);
      assertThat(session.isConnected()).isTrue();
    } finally {
      if (session != null && session.isConnected()) session.disconnect();
      client.stop();
    }
  }

  @Test
  void websocketRejectsMissingTokenAndUnauthorizedSubscription() throws Exception {
    WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
    StompSession session = null;
    try {
      CompletableFuture<StompHeaders> rejected = new CompletableFuture<>();
      client.connectAsync(
          "ws://localhost:" + port + "/ws",
          new StompSessionHandlerAdapter() {
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
              rejected.complete(headers);
            }
          });
      assertThat(rejected.get(10, TimeUnit.SECONDS).getFirst("message")).isNotBlank();
      CompletableFuture<StompHeaders> error = new CompletableFuture<>();
      StompHeaders connect = new StompHeaders();
      connect.set("Authorization", "Bearer " + token);
      session =
          client
              .connectAsync(
                  "ws://localhost:" + port + "/ws",
                  new WebSocketHttpHeaders(),
                  connect,
                  new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                      error.complete(headers);
                    }
                  })
              .get(10, TimeUnit.SECONDS);
      session.subscribe(
          "/topic/conversations." + UUID.randomUUID(), new StompSessionHandlerAdapter() {});
      assertThat(error.get(10, TimeUnit.SECONDS).getFirst("message")).isNotBlank();
      StompSession rejectedSession = session;
      Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> !rejectedSession.isConnected());
    } finally {
      client.stop();
    }
  }

  private StompFrameHandler handler(BlockingQueue<MessageResponse> queue) {
    return new StompFrameHandler() {
      public Type getPayloadType(StompHeaders headers) {
        return MessageResponse.class;
      }

      public void handleFrame(StompHeaders headers, Object payload) {
        queue.add((MessageResponse) payload);
      }
    };
  }

  @Test
  void concurrentRetriesCreateOneMessage() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      List<Future<MessageResponse>> results = new ArrayList<>();
      for (int i = 0; i < 12; i++)
        results.add(pool.submit(() -> service.sendMessage(userId, convId, request("same"))));
      Set<UUID> ids = new HashSet<>();
      for (Future<MessageResponse> result : results) ids.add(result.get(10, TimeUnit.SECONDS).id());
      assertThat(ids).hasSize(1);
      assertThat(conversations.getLastSeq(convId)).contains(1L);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentSendsHaveUniqueGaplessSequences() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      List<Future<MessageResponse>> results = new ArrayList<>();
      for (int i = 0; i < 12; i++) {
        final String key = "key" + i;
        results.add(pool.submit(() -> service.sendMessage(userId, convId, request(key))));
      }
      Set<Long> seqs = new TreeSet<>();
      for (Future<MessageResponse> result : results)
        seqs.add(result.get(10, TimeUnit.SECONDS).seq());
      assertThat(seqs).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void historyReadEditDeleteAndReactionsWork() {
    MessageResponse first = service.sendMessage(userId, convId, request("one"));
    service.sendMessage(userId, convId, request("two"));
    service.sendMessage(userId, convId, request("three"));
    var page = service.getHistory(userId, convId, null, 0L, 2);
    assertThat(page.getItems()).extracting(MessageResponse::seq).containsExactly(1L, 2L);
    assertThat(page.isHasMore()).isTrue();
    assertThat(service.getHistory(userId, convId, 3L, null, 2).getItems())
        .extracting(MessageResponse::seq)
        .containsExactly(2L, 1L);
    service.readMessages(userId, convId, new ReadMessageRequest(3));
    service.readMessages(userId, convId, new ReadMessageRequest(1));
    assertThat(
            members
                .findByIdConversationIdAndIdUserId(convId, userId)
                .orElseThrow()
                .getLastReadSeq())
        .isEqualTo(3);
    service.addReaction(userId, first.id(), new AddReactionRequest("👍"));
    service.addReaction(userId, first.id(), new AddReactionRequest("👍"));
    assertThat(
            reactions.existsById(new MessageReaction.MessageReactionId(first.id(), userId, "👍")))
        .isTrue();
    service.removeReaction(userId, first.id(), "👍");
    assertThat(
            reactions.existsById(new MessageReaction.MessageReactionId(first.id(), userId, "👍")))
        .isFalse();
    assertThat(service.editMessage(userId, first.id(), new EditMessageRequest("edited")).content())
        .isEqualTo("edited");
    service.deleteMessage(userId, first.id());
    assertThat(service.getMessage(userId, first.id()).content()).isNull();
  }

  @Test
  void openApiDocumentsMessageRoutesAndValidation() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/v1/conversations/{id}/messages'].get.operationId")
                .value("messages_history"))
        .andExpect(
            jsonPath("$.paths['/api/v1/conversations/{id}/messages'].post.operationId")
                .value("messages_send"))
        .andExpect(
            jsonPath("$.paths['/api/v1/messages/{id}'].patch.operationId").value("messages_edit"))
        .andExpect(
            jsonPath("$.components.schemas.SendMessageRequest.properties.content.maxLength")
                .value(4000));
  }

  @Test
  void allMessageEndpointsReturnExpectedResponses() throws Exception {
    String path = "/api/v1/conversations/" + convId;
    String json =
        "{\"conversationId\":\""
            + convId
            + "\",\"clientMessageId\":\"rest\",\"content\":\"hello\"}";
    mvc.perform(
            post(path + "/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isOk())
        .andExpect(jsonPath("seq").value(1));
    UUID id =
        messages
            .findByConversationIdAndSenderIdAndClientMessageId(convId, userId, "rest")
            .orElseThrow()
            .getId();
    String messagePath = "/api/v1/messages/" + id;
    mvc.perform(
            get(path + "/messages?afterSeq=0&limit=1").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("items[0].id").value(id.toString()));
    mvc.perform(get(messagePath).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(
            post(path + "/read")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"upToSeq\":1}"))
        .andExpect(status().isOk());
    mvc.perform(
            patch(messagePath)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"edited\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("content").value("edited"));
    mvc.perform(
            post(messagePath + "/reactions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emoji\":\"ok\"}"))
        .andExpect(status().isOk());
    mvc.perform(delete(messagePath + "/reactions/ok").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(delete(messagePath).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/messages/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    mvc.perform(get(path + "/messages?limit=0").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(get(path + "/messages?beforeSeq=bad").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
  }

  @Test
  void endpointsRejectUnauthenticatedNonMemberAndInvalidPayloads() throws Exception {
    String path = "/api/v1/conversations/" + convId + "/messages";
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(
            post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"conversationId\":\""
                        + convId
                        + "\",\"clientMessageId\":\"x\",\"content\":\"\"}"))
        .andExpect(status().isBadRequest());
    members.deleteById(new ConversationMemberId(convId, userId));
    mvc.perform(get(path).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }
}
