# SKILL.md: Building a Perfect Java Chat Backend

This file trains an agent to write **production-quality Spring Boot backend code** for this project. It contains principles, reusable patterns, code templates, and review checklists. Use these templates as the default shape of your code.

Stack: Java 21, Spring Boot 3.x, Spring Security, Spring Data JPA, Flyway, PostgreSQL, Redis, WebSocket/STOMP, JUnit 5, Testcontainers.

---

## 1. Core Principles (think like this)

1. **Correctness before features.** A chat that loses or duplicates messages is broken, however many features it has.
2. **The database is the source of truth.** Redis and WebSocket are delivery mechanisms only.
3. **Make invalid states impossible.** Use DB constraints plus validation, not code checks alone.
4. **Idempotency everywhere a client may retry.**
5. **Authorize at the resource level.** Authentication only says who you are; membership checks say what you may touch.
6. **Small, testable units.** Business rules live in services and are testable without Spring.
7. **Fail loudly and consistently.** Domain exceptions plus one global handler.
8. **Design for two nodes from day one.** No in-memory state that would break with a second instance (except in Phase 1's simple broker, which is documented and replaceable).

---

## 2. Pattern: Layered Feature Slice

```
chat/message/
  MessageController.java     REST endpoints, validation, OpenAPI
  MessageService.java        business rules, transactions
  MessageRepository.java     Spring Data queries
  Message.java               JPA entity
  MessageMapper.java         MapStruct entity ↔ DTO
  dto/SendMessageRequest.java, MessageResponse.java
```

### DTOs are records with validation

```java
public record SendMessageRequest(
    @NotBlank @Size(max = 64) String clientMessageId,
    @NotBlank @Size(max = 4000) String content,
    UUID replyToId
) {}

public record MessageResponse(
    UUID id, UUID conversationId, UUID senderId, long seq,
    String clientMessageId, String type, String content,
    UUID replyToId, Instant createdAt, Instant editedAt, boolean deleted
) {}
```

### Thin controller

```java
@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/messages")
@RequiredArgsConstructor
@Tag(name = "Messages")
class MessageController {

  private final MessageService messageService;

  @PostMapping
  @Operation(summary = "Send a message (REST fallback)")
  ResponseEntity<MessageResponse> send(
      @PathVariable UUID conversationId,
      @Valid @RequestBody SendMessageRequest request,
      @AuthenticationPrincipal AuthUser user) {
    MessageResponse created = messageService.send(user.id(), conversationId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping
  CursorPage<MessageResponse> history(
      @PathVariable UUID conversationId,
      @RequestParam(required = false) Long beforeSeq,
      @RequestParam(required = false) Long afterSeq,
      @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit,
      @AuthenticationPrincipal AuthUser user) {
    return messageService.history(user.id(), conversationId, beforeSeq, afterSeq, limit);
  }
}
```

---

## 3. Pattern: Idempotent, Ordered Send (the heart of the app)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

  private final ConversationService conversationService;
  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;
  private final MessageMapper mapper;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  @Transactional
  public MessageResponse send(UUID senderId, UUID conversationId, SendMessageRequest req) {
    conversationService.assertActiveMember(senderId, conversationId);   // authorization

    // 1. Idempotency: a retry returns the original message
    Optional<Message> existing = messageRepository
        .findByConversationIdAndSenderIdAndClientMessageId(
            conversationId, senderId, req.clientMessageId());
    if (existing.isPresent()) {
      return mapper.toResponse(existing.get());
    }

    // 2. Atomic per-conversation sequence
    long seq = conversationRepository.nextSeq(conversationId);           // UPDATE ... RETURNING

    // 3. Persist
    Message message = Message.builder()
        .id(UuidCreator.getTimeOrderedEpoch())                           // UUID v7
        .conversationId(conversationId)
        .senderId(senderId)
        .seq(seq)
        .clientMessageId(req.clientMessageId())
        .type(MessageType.TEXT)
        .content(req.content().strip())
        .replyToId(req.replyToId())
        .createdAt(clock.instant())
        .build();
    try {
      messageRepository.saveAndFlush(message);
    } catch (DataIntegrityViolationException e) {
      // Concurrent duplicate won the race: return the winner
      return messageRepository
          .findByConversationIdAndSenderIdAndClientMessageId(
              conversationId, senderId, req.clientMessageId())
          .map(mapper::toResponse)
          .orElseThrow(() -> e);
    }

    MessageResponse response = mapper.toResponse(message);
    events.publishEvent(new MessageCreatedEvent(response));              // broadcast AFTER commit
    log.info("message_sent conversationId={} senderId={} seq={}", conversationId, senderId, seq);
    return response;
  }
}
```

Sequence query (native, single atomic statement):

```java
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  @Query(value = """
      UPDATE conversations
         SET last_seq = last_seq + 1, last_message_at = now()
       WHERE id = :id
   RETURNING last_seq
      """, nativeQuery = true)
  long nextSeq(@Param("id") UUID conversationId);
}
```
> If Hibernate rejects `UPDATE ... RETURNING` via `@Query`, use `JdbcTemplate`/`NamedParameterJdbcTemplate` for this one statement. It must run in the same transaction.

### Broadcast only after commit

```java
@Component
@RequiredArgsConstructor
class MessageEventRelay {

  private final RealtimePublisher publisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void onMessageCreated(MessageCreatedEvent event) {
    publisher.publishToConversation(event.message().conversationId(), event.message());
  }
}
```

`RealtimePublisher` is an interface with two implementations: `SimpleBrokerPublisher` (Phase 1: `SimpMessagingTemplate.convertAndSend("/topic/conversations." + id, msg)`) and `RedisPublisher` (Phase 3: `redisTemplate.convertAndSend("chat:conv:" + id, json)`, plus a subscriber that forwards to local sessions). Swapping is a config change, not a rewrite.

---

## 4. Pattern: WebSocket (STOMP) Setup

```java
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final WsAuthInterceptor authInterceptor;
  private final AppProperties props;

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
        .setAllowedOrigins(props.cors().allowedOrigins().toArray(String[]::new));
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
    registry.enableSimpleBroker("/topic", "/queue")
        .setHeartbeatValue(new long[] {10_000, 10_000})
        .setTaskScheduler(heartbeatScheduler());
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authInterceptor);
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
    registry.setMessageSizeLimit(64 * 1024).setSendBufferSizeLimit(512 * 1024);
  }

  @Bean
  TaskScheduler heartbeatScheduler() { return new ThreadPoolTaskScheduler(); }
}
```

### Authentication and subscription authorization

```java
@Component
@RequiredArgsConstructor
class WsAuthInterceptor implements ChannelInterceptor {

  private final JwtService jwtService;
  private final ConversationService conversationService;
  private static final Pattern TOPIC = Pattern.compile("^/topic/conversations\\.([0-9a-fA-F-]{36})$");

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (acc == null) return message;

    if (StompCommand.CONNECT.equals(acc.getCommand())) {
      String header = acc.getFirstNativeHeader("Authorization");
      if (header == null || !header.startsWith("Bearer ")) throw new MessagingException("Unauthorized");
      AuthUser user = jwtService.parseAccessToken(header.substring(7));   // throws if invalid/expired
      acc.setUser(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
    }

    if (StompCommand.SUBSCRIBE.equals(acc.getCommand())) {
      AuthUser user = (AuthUser) ((Authentication) acc.getUser()).getPrincipal();
      Matcher m = TOPIC.matcher(acc.getDestination() == null ? "" : acc.getDestination());
      if (m.matches()) {
        conversationService.assertActiveMember(user.id(), UUID.fromString(m.group(1)));
      } else if (!isAllowedUserDestination(acc.getDestination())) {
        throw new MessagingException("Forbidden destination");
      }
    }
    return message;
  }
}
```

### Message handler (thin, delegates to the same service as REST)

```java
@Controller
@RequiredArgsConstructor
class ChatWsController {

  private final MessageService messageService;
  private final SimpMessagingTemplate template;

  @MessageMapping("chat.send")
  void send(@Payload @Valid WsSendMessage payload, Principal principal) {
    AuthUser user = AuthUser.from(principal);
    MessageResponse saved = messageService.send(user.id(), payload.conversationId(),
        new SendMessageRequest(payload.clientMessageId(), payload.content(), payload.replyToId()));
    template.convertAndSendToUser(user.id().toString(), "/queue/acks",
        new SendAck(saved.clientMessageId(), saved.id(), saved.seq(), saved.createdAt()));
  }

  @MessageExceptionHandler
  @SendToUser("/queue/errors")
  ApiError handle(Exception e) { return ApiErrors.fromException(e); }
}
```

---

## 5. Pattern: JWT Auth + Refresh Rotation

**Access token:** short-lived JWT, stateless.
**Refresh token:** random opaque value; only its SHA-256 hash is stored.

```java
@Transactional
public TokenResponse refresh(String rawRefreshToken) {
  String hash = Hashing.sha256(rawRefreshToken);
  RefreshToken token = refreshRepo.findByTokenHash(hash)
      .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN"));

  if (token.getRevokedAt() != null || token.getExpiresAt().isBefore(clock.instant())) {
    throw new UnauthorizedException("INVALID_REFRESH_TOKEN");
  }
  if (token.getUsedAt() != null) {                       // reuse = likely theft
    refreshRepo.revokeFamily(token.getFamilyId(), clock.instant());
    throw new UnauthorizedException("REFRESH_TOKEN_REUSED");
  }

  token.setUsedAt(clock.instant());
  User user = userService.getActive(token.getUserId());
  return issueTokens(user, token.getFamilyId(), token.getDeviceInfo());   // new access + new refresh, same family
}
```

Security filter rules: stateless session, `permitAll` only for `/api/v1/auth/{register,login,refresh}`, `/actuator/health`, and the WS handshake path; everything else `authenticated()`.

---

## 6. Pattern: Global Error Handling

```java
@RestControllerAdvice
@Slf4j
class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest req) {
    List<FieldError> errors = e.getBindingResult().getFieldErrors().stream()
        .map(f -> new ApiError.Field(f.getField(), f.getDefaultMessage())).toList();
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", req, errors);
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ApiError> notFound(NotFoundException e, HttpServletRequest req) {
    return build(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), req, List.of());
  }

  @ExceptionHandler(ForbiddenException.class)
  ResponseEntity<ApiError> forbidden(ForbiddenException e, HttpServletRequest req) {
    return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", req, List.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest req) {
    String traceId = MDC.get("traceId");
    log.error("unexpected_error traceId={}", traceId, e);                  // details only in logs
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", req, List.of());
  }
}
```

Add handlers for `ConflictException` (409), `BusinessRuleException` (422), `UnauthorizedException` (401), `RateLimitException` (429, with `Retry-After`), and `AccessDeniedException` (403).

---

## 7. Pattern: Authorization by Membership

```java
@Transactional(readOnly = true)
public void assertActiveMember(UUID userId, UUID conversationId) {
  boolean ok = memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId);
  if (!ok) throw new ForbiddenException("Not a member of this conversation");
}
```
Return **403** for existing conversations the user is not in, or **404** if you prefer not to reveal existence. Pick one, document it, and be consistent.

---

## 8. Pattern: Cursor Pagination

```java
public interface MessageRepository extends JpaRepository<Message, UUID> {

  @Query("""
      select m from Message m
       where m.conversationId = :cid and (:before is null or m.seq < :before)
       order by m.seq desc
      """)
  List<Message> findBefore(@Param("cid") UUID cid, @Param("before") Long before, Pageable limit);

  @Query("""
      select m from Message m
       where m.conversationId = :cid and m.seq > :after
       order by m.seq asc
      """)
  List<Message> findAfter(@Param("cid") UUID cid, @Param("after") long after, Pageable limit);
}
```
Fetch `limit + 1` rows to compute `hasMore`; return `nextCursor` as the last `seq`.

---

## 9. Pattern: Rate Limiting (Redis)

Fixed-window counter (simple, good enough for Medium):

```java
public boolean allow(String key, int max, Duration window) {
  Long count = redis.opsForValue().increment(key);
  if (count != null && count == 1) redis.expire(key, window);
  return count != null && count <= max;
}
```
Keys: `rl:login:{ip}`, `rl:send:{userId}`. On denial throw `RateLimitException`. For smoother behavior later, use a token bucket via Lua script or Bucket4j.

---

## 10. Pattern: Testing Templates

### Unit test (service, no Spring)

```java
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
  @Mock ConversationService conversationService; /* other mocks */
  @InjectMocks MessageService service;

  @Test
  void send_nonMember_throwsForbidden() {
    doThrow(new ForbiddenException("Not a member"))
        .when(conversationService).assertActiveMember(userId, convId);
    assertThatThrownBy(() -> service.send(userId, convId, request))
        .isInstanceOf(ForbiddenException.class);
    verifyNoInteractions(messageRepository);
  }
}
```

### Integration test with Testcontainers

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class MessageFlowIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");
  @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }
  // register users → login → create DM → send → assert stored, ordered, ack'd
}
```

### Concurrency test (must exist)

```java
@Test
void send_parallel_producesGaplessUniqueSeq() throws Exception {
  int n = 50;
  ExecutorService pool = Executors.newFixedThreadPool(10);
  List<Future<MessageResponse>> futures = IntStream.range(0, n)
      .mapToObj(i -> pool.submit(() -> service.send(userId, convId, req("c-" + i, "hi"))))
      .toList();
  List<Long> seqs = new ArrayList<>();
  for (var f : futures) seqs.add(f.get().seq());
  assertThat(seqs).doesNotHaveDuplicates();
  assertThat(seqs.stream().sorted().toList()).isEqualTo(LongStream.rangeClosed(1, n).boxed().toList());
}

@Test
void send_sameClientMessageIdTwice_createsOneRow() { /* assert count == 1, same id returned */ }
```

---

## 11. Pattern: Configuration

```java
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Chat chat) {
  public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {}
  public record Cors(List<String> allowedOrigins) {}
  public record Chat(int maxMessageLength, int editWindowMinutes) {}
}
```
`application.yml` holds defaults, with secrets as `${JWT_SECRET}`. Fail fast on startup if a required secret is missing (`@Validated` + `@NotBlank`).

---

## 12. Pattern: Flyway Migrations

- Files: `src/main/resources/db/migration/V{n}__{snake_description}.sql`.
- One logical change per file. Never edit a merged migration.
- Backward-compatible steps for rolling deploys: add a column nullable → backfill → add constraint later. Avoid dropping or renaming in the same release as the code that stops using it.
- Always add the index in the same migration as the query path that needs it.

---

## 13. Performance Habits

- Check every list endpoint for N+1 queries (enable `hibernate.generate_statistics` in tests).
- Use projections/DTO queries for list views (conversation list) instead of loading entities and lazy collections.
- Bound everything: page sizes, message length, upload size, WS frame size, search length.
- Never load an entire conversation's messages into memory.
- Push slow side effects (push notifications, email, indexing) to `@Async` or a queue after commit.
- Add timeouts to Redis, HTTP clients, and DB pools.

---

## 14. Code Review Checklist (run on your own code before finishing)

**Correctness**
- [ ] Is send idempotent? Is `seq` assigned atomically? Broadcast after commit?
- [ ] Are edge cases handled (empty content, huge content, deleted message, left member, duplicate)?

**Security**
- [ ] Membership/ownership check present in the service, on REST and WS paths?
- [ ] Identity comes from the token, not the request body?
- [ ] No sensitive data in logs or error responses?

**Design**
- [ ] Controller thin? Entities not exposed? Modules only cross via services?
- [ ] Constants/config instead of magic values?

**Data**
- [ ] Flyway migration added? Indexes and unique constraints in place?
- [ ] Time handled as UTC `Instant`?

**Quality**
- [ ] Tests for happy path, validation, 401, 403, 404, conflict, concurrency where relevant?
- [ ] Naming, formatting, OpenAPI annotations, no dead code?

---

## 15. Common Pitfalls and Fixes

| Pitfall | Fix |
|---|---|
| Duplicate messages after reconnect | `clientMessageId` + unique constraint; client re-sends with the same ID |
| Messages appear out of order | Order by `seq`, never by `createdAt` or arrival time |
| Broadcast for a rolled-back message | `@TransactionalEventListener(AFTER_COMMIT)` |
| Unauthorized user reads a room via socket | Authorize in the `SUBSCRIBE` interceptor |
| Token expiry with open socket | Close at expiry; client reconnects with a fresh token |
| Slow conversation list | Use a projection query with unread count from `last_seq - last_read_seq`; index `conversation_members(user_id)` |
| Lazy-loading exception in mapper | Map inside the transaction, or use DTO projections |
| `@Transactional` not applied | Method must be public, called through the Spring proxy (no self-invocation) |
| Flaky tests | Testcontainers, fixed `Clock`, no `Thread.sleep` (use Awaitility) |
| Read receipts going backward | `UPDATE ... SET last_read_seq = GREATEST(last_read_seq, :seq)` |

---

## 16. Skill Growth Path

To become a top-level backend engineer on this project, master in order:

1. **Basic:** layered architecture, validation, JWT auth, JPA, Flyway, REST design, global errors, unit + integration tests.
2. **Medium:** WebSocket/STOMP security, idempotency, ordering, concurrency control, Redis (presence, rate limits), file upload via pre-signed URLs, query optimization.
3. **Advanced:** horizontal scaling (Redis pub/sub/brokers), async pipelines (Kafka/RabbitMQ), observability (metrics, tracing), load testing, data partitioning and retention, Kubernetes, threat modeling.

For each new task, ask: *What can go wrong? What if the request is retried? What if two users do this at once? What if the user is not allowed? What if the server restarts mid-way?* If the code handles those five, it is production-grade.
