# Chat Application Backend: Complete Project Documentation

> **Audience:** developers and AI agents. This is the single source of truth for architecture, features, data model, APIs, real-time flow, security, testing, and roadmap.
> **Stack:** Java 21, Spring Boot 3.x, Spring Security (JWT), WebSocket (STOMP), PostgreSQL, Redis.
> **Related files:** `AGENT.md` (how an agent must behave), `SKILL.md` (how to write the code).

---

## 0. How to Use This Document

1. Read sections 1 to 4 to understand the goal, the architecture, and the rules.
2. Check section 15 (Roadmap) to find the current phase and the next task.
3. For any task, follow the module layout (section 5), the API contract (section 8), and the Definition of Done (section 17).
4. Never invent behavior that contradicts this document. If something is missing, add it here first, then implement it.

---

## 1. Project Overview

**Name:** ChatApp Backend
**Goal:** a reliable, secure, scalable messaging backend supporting 1-to-1 chats, group chats, real-time delivery, and message history.

**Non-goals (for now):** voice or video calls, end-to-end encryption, a frontend (only the API contract is defined here).

**Quality goals (priority order):**
1. **Correctness:** no lost, duplicated, or misordered messages.
2. **Security:** authenticated and authorized on every path (REST and WebSocket).
3. **Maintainability:** clean modules, tests, consistent conventions.
4. **Performance:** p95 message send-to-receive under 200 ms on a single node.
5. **Scalability:** horizontally scalable without a rewrite.

---

## 2. Architecture

### 2.1 Chosen style: **Modular Monolith** (evolves to microservices only if needed)

A modular monolith gives one deployable and simple transactions, while strict module boundaries keep future splitting possible. Do not start with microservices.

```
                ┌──────────────────────────────────────────────┐
  Clients  ───► │              API Gateway / LB (Nginx)         │
 (Web/Mobile)   └───────┬───────────────────────┬──────────────┘
                        │ REST (HTTPS)          │ WebSocket (WSS)
                ┌───────▼───────────────────────▼──────────────┐
                │           Spring Boot App (N instances)       │
                │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐  │
                │  │  auth  │ │  user  │ │  chat  │ │  media │  │
                │  └────────┘ └────────┘ └────────┘ └────────┘  │
                │  ┌──────────────┐ ┌──────────────────────────┐ │
                │  │ notification │ │ common (security, errors)│ │
                │  └──────────────┘ └──────────────────────────┘ │
                └───────┬───────────────┬──────────────┬─────────┘
                        │               │              │
                 ┌──────▼─────┐  ┌──────▼─────┐  ┌─────▼──────┐
                 │ PostgreSQL │  │   Redis    │  │ S3 / MinIO │
                 │ (source of │  │ presence,  │  │ (media,    │
                 │  truth)    │  │ pub/sub,   │  │  Phase 2+) │
                 │            │  │ rate limit │  │            │
                 └────────────┘  └────────────┘  └────────────┘
```

### 2.2 Technology decisions

| Concern | Choice | Reason |
|---|---|---|
| Language/runtime | Java 21 (LTS), virtual threads optional | Records, pattern matching, modern GC |
| Framework | Spring Boot 3.x | Ecosystem, security, WebSocket support |
| Real-time | WebSocket + STOMP | Built-in routing, subscriptions, easy auth interceptors |
| Database | PostgreSQL 16 | ACID, JSONB, strong indexing |
| Cache / presence / fan-out | Redis 7 | Fast TTL keys, pub/sub for multi-instance |
| Migrations | Flyway | Versioned, repeatable schema changes |
| Auth | JWT access token + rotating refresh token | Stateless REST, revocable sessions |
| Password hashing | Argon2id or BCrypt (strength 12) | Industry standard |
| Mapping | MapStruct | Compile-time, no reflection overhead |
| Validation | Jakarta Bean Validation | Declarative |
| API docs | springdoc-openapi | Auto-generated Swagger UI |
| Testing | JUnit 5, Mockito, Testcontainers, AssertJ | Real DB/Redis in tests |
| Build | Maven (or Gradle) | Pick one and keep it |
| Observability | Actuator, Micrometer, structured JSON logs | Metrics and traceability |
| Containers | Docker + docker-compose | Reproducible local setup |
| CI | GitHub Actions | Build, test, lint on every PR |

### 2.3 Layering inside each module

```
Controller (REST/WS)  →  Service (business rules, transactions)  →  Repository (data access)
        ▲ DTOs only                 ▲ entities + domain logic              ▲ entities only
```

Rules:
- Controllers never touch repositories or entities directly.
- Entities never leave the service layer; expose **DTOs (Java records)**.
- Modules talk to each other **only through the other module's public service interface**, never its repositories.
- No business logic in controllers, entities, or interceptors.

---

## 3. Package Structure (Package-by-Feature)

```
com.example.chat
├── ChatApplication.java
├── common
│   ├── config          (SecurityConfig, WebSocketConfig, RedisConfig, OpenApiConfig, AsyncConfig)
│   ├── exception       (GlobalExceptionHandler, ApiError, custom exceptions)
│   ├── security        (JwtService, JwtAuthFilter, CurrentUser, WsAuthInterceptor)
│   ├── web             (ApiResponse, PageResponse, CursorPage, request logging filter)
│   └── util            (Clock provider, IdGenerator)
├── auth
│   ├── AuthController, AuthService, RefreshTokenService
│   ├── dto             (RegisterRequest, LoginRequest, TokenResponse ...)
│   └── model / repo    (RefreshToken, RefreshTokenRepository)
├── user
│   ├── UserController, UserService, UserRepository, User, UserMapper, dto
├── chat
│   ├── conversation    (Conversation, ConversationMember, ConversationService, ConversationController)
│   ├── message         (Message, MessageService, MessageController, MessageRepository, dto)
│   ├── realtime        (ChatWsController, PresenceService, TypingService, RealtimePublisher)
│   └── receipt         (ReadReceiptService)
├── media               (Phase 2)
└── notification        (Phase 3)
```

---

## 4. Global Engineering Rules

1. Java 21, no raw types, no `null` returns from services (use `Optional` or throw).
2. Constructor injection only (`@RequiredArgsConstructor`). No field `@Autowired`.
3. All configuration in `application.yml` with profiles: `local`, `test`, `prod`. **No secrets in Git.** Use environment variables.
4. All schema changes through **Flyway** migrations. `spring.jpa.hibernate.ddl-auto=validate` in every profile except throwaway tests.
5. All time values are **UTC** `Instant`. Never `Date` or `LocalDateTime` for persisted timestamps.
6. IDs: **UUID v7 (time-ordered)** or `BIGINT` identity. Pick one (recommended: UUID for public IDs).
7. Every public endpoint returns a consistent error format (section 9.3).
8. No `System.out.println`. Use SLF4J. Never log passwords, tokens, or message content at INFO.
9. Every feature ships with unit tests and at least one integration test.
10. Follow Google Java Style. Enforce with Spotless or Checkstyle in CI.

---

## 5. Authentication and Authorization

### 5.1 Flow

```
Register ─► POST /api/v1/auth/register
Login    ─► POST /api/v1/auth/login       → { accessToken (15 min), refreshToken (7-30 days) }
Call API ─► Authorization: Bearer <accessToken>
Refresh  ─► POST /api/v1/auth/refresh     → new access + NEW refresh (old one invalidated = rotation)
Logout   ─► POST /api/v1/auth/logout      → revoke refresh token (this device)
Logout all ► POST /api/v1/auth/logout-all → revoke all refresh tokens of the user
```

### 5.2 Token design

| Token | Lifetime | Storage (server) | Notes |
|---|---|---|---|
| Access JWT | 15 min | none (stateless) | Claims: `sub` (userId), `roles`, `iat`, `exp`, `jti` |
| Refresh token | 7-30 days | DB, **hashed (SHA-256)** | Opaque random 256-bit string, one row per device |

**Refresh token rotation and reuse detection:** each refresh issues a new token and marks the old one `used`. If a used token is presented again, revoke the whole token family and force re-login (token theft signal).

### 5.3 Rules
- Sign JWT with **HS256 (secret 256+ bit from env)** for a single service, or **RS256** if other services must verify.
- Validate on every request: signature, expiry, and user still active.
- Password rules: min 8 chars, hash with BCrypt (12) or Argon2id, never store or log plaintext.
- Rate-limit `login`, `register`, and `refresh` (for example 5 attempts per minute per IP and username). Temporary lockout after repeated failures.
- Generic login error: "Invalid credentials" (do not reveal whether the username exists).
- CORS: explicit allowed origins, never `*` in prod.
- CSRF disabled only because auth is Bearer-token based (no cookies). If refresh tokens move to cookies, use `HttpOnly; Secure; SameSite`.

### 5.4 Authorization
- Roles: `USER`, `ADMIN` (later `MODERATOR`).
- Resource-level checks are mandatory: a user may read or send only in conversations where they are a **member**. Enforce in the service layer (`conversationService.assertMember(userId, conversationId)`), not only at the URL level.

### 5.5 WebSocket authentication
- Client connects to `/ws` and sends the JWT in the STOMP `CONNECT` header (`Authorization: Bearer ...`).
- A `ChannelInterceptor` (`WsAuthInterceptor`) validates the token on `CONNECT` and sets the `Principal`.
- On every `SUBSCRIBE` to `/topic/conversations.{id}`, the interceptor checks membership. Reject otherwise.
- Access tokens expire while sockets stay open. Policy: the server closes the session at token expiry, and the client reconnects with a fresh token (or sends a `REAUTH` frame; pick one and document it).

---

## 6. Data Model (PostgreSQL)

### 6.1 Entity relationship

```
users 1───* refresh_tokens
users *───* conversations   (via conversation_members)
conversations 1───* messages
messages 1───* message_attachments
messages 1───* message_reactions
users 1───* devices (push tokens, Phase 3)
users *───* users   (blocks, contacts)
```

### 6.2 Schema (initial Flyway migration `V1__init.sql`)

```sql
CREATE TABLE users (
  id            UUID PRIMARY KEY,
  username      VARCHAR(30)  NOT NULL UNIQUE,
  email         VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name  VARCHAR(60)  NOT NULL,
  avatar_url    VARCHAR(500),
  bio           VARCHAR(300),
  role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE, DISABLED
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
  id          UUID PRIMARY KEY,
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  VARCHAR(64) NOT NULL UNIQUE,
  family_id   UUID NOT NULL,
  device_info VARCHAR(200),
  expires_at  TIMESTAMPTZ NOT NULL,
  used_at     TIMESTAMPTZ,
  revoked_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id);

CREATE TABLE conversations (
  id          UUID PRIMARY KEY,
  type        VARCHAR(10) NOT NULL,          -- DIRECT | GROUP
  title       VARCHAR(100),                  -- group only
  avatar_url  VARCHAR(500),
  direct_key  VARCHAR(80) UNIQUE,            -- "minUserId:maxUserId" prevents duplicate DMs
  created_by  UUID NOT NULL REFERENCES users(id),
  last_seq    BIGINT NOT NULL DEFAULT 0,     -- per-conversation message counter
  last_message_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation_members (
  conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role            VARCHAR(10) NOT NULL DEFAULT 'MEMBER',  -- OWNER | ADMIN | MEMBER
  last_read_seq   BIGINT NOT NULL DEFAULT 0,
  muted_until     TIMESTAMPTZ,
  joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  left_at         TIMESTAMPTZ,
  PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX idx_members_user ON conversation_members(user_id) WHERE left_at IS NULL;

CREATE TABLE messages (
  id                UUID PRIMARY KEY,
  conversation_id   UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id         UUID NOT NULL REFERENCES users(id),
  seq               BIGINT NOT NULL,                  -- strictly increasing per conversation
  client_message_id VARCHAR(64) NOT NULL,             -- idempotency key from client
  type              VARCHAR(15) NOT NULL DEFAULT 'TEXT', -- TEXT | IMAGE | FILE | SYSTEM
  content           TEXT,
  reply_to_id       UUID REFERENCES messages(id),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  edited_at         TIMESTAMPTZ,
  deleted_at        TIMESTAMPTZ,                      -- soft delete
  UNIQUE (conversation_id, seq),
  UNIQUE (conversation_id, sender_id, client_message_id)
);
CREATE INDEX idx_messages_conv_seq ON messages(conversation_id, seq DESC);

-- Phase 2+
CREATE TABLE message_attachments (
  id UUID PRIMARY KEY, message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  storage_key VARCHAR(300) NOT NULL, file_name VARCHAR(200), mime_type VARCHAR(100),
  size_bytes BIGINT, width INT, height INT, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE message_reactions (
  message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  emoji VARCHAR(16) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (message_id, user_id, emoji)
);
CREATE TABLE user_blocks (
  blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (blocker_id, blocked_id)
);
```

**Why `seq` per conversation?** It gives deterministic ordering, cheap gap detection on the client ("I have 41, I received 43, so I fetch 42"), cursor pagination without timestamp ties, and unread counts as `last_seq - last_read_seq`.

---

## 7. Real-Time Chat: Flow and Message Handling (the core)

### 7.1 Channels (STOMP destinations)

| Direction | Destination | Purpose |
|---|---|---|
| Client → Server | `/app/chat.send` | Send a message |
| Client → Server | `/app/chat.typing` | Typing start/stop |
| Client → Server | `/app/chat.read` | Mark messages read up to a seq |
| Server → Clients | `/topic/conversations.{id}` | New/edited/deleted messages, typing, reactions for that conversation |
| Server → One user | `/user/queue/acks` | Delivery ack for a sent message |
| Server → One user | `/user/queue/errors` | Per-user error frames |
| Server → One user | `/user/queue/notifications` | Being added to a conversation, presence updates |

### 7.2 Send-message sequence (authoritative flow)

```
Client A                    Server (instance 1)                 DB / Redis            Client B (instance 2)
   │  SEND /app/chat.send       │                                    │                          │
   │  {conversationId,          │                                    │                          │
   │   clientMessageId, text}   │                                    │                          │
   │──────────────────────────►│ 1. Authenticate (Principal)         │                          │
   │                            │ 2. Validate payload (size, type)   │                          │
   │                            │ 3. Rate-limit check (Redis)        │                          │
   │                            │ 4. assertMember(A, conv)           │                          │
   │                            │ 5. TX BEGIN                        │                          │
   │                            │    - dedupe by (conv, A, clientId) │                          │
   │                            │      if exists → return existing   │                          │
   │                            │    - seq = UPDATE conversations    │                          │
   │                            │      SET last_seq=last_seq+1       │                          │
   │                            │      RETURNING last_seq            │                          │
   │                            │    - INSERT message                │                          │
   │                            │    TX COMMIT ─────────────────────►│                          │
   │◄───────────────────────────│ 6. ACK → /user/queue/acks          │                          │
   │  {clientMessageId,         │    {clientMessageId, id, seq,      │                          │
   │   id, seq, createdAt}      │     status: SENT}                  │                          │
   │                            │ 7. AFTER COMMIT: publish event     │                          │
   │                            │    to Redis channel                │─────────────────────────►│
   │                            │    "chat:conv:{id}"                │  8. Each instance relays │
   │                            │                                    │     to its local STOMP   │
   │                            │                                    │     subscribers          │
   │◄───────────────────────────┼────────────────────────────────────┼─────/topic/conv.{id}────►│
   │                            │ 9. Offline members → push          │                          │
   │                            │    (Phase 3)                       │                          │
```

**Critical rules**
1. **Persist first, broadcast after commit.** Never broadcast a message that could roll back. Use `@TransactionalEventListener(phase = AFTER_COMMIT)`.
2. **Idempotency:** the client generates `clientMessageId` (UUID). A retry after a network drop must not create a duplicate. The DB unique constraint is the final guard.
3. **Server assigns `id`, `seq`, and `createdAt`.** Never trust client-provided timestamps for ordering.
4. **ACK is separate from broadcast:** the sender sees `SENDING → SENT` from the ack, not from the echo.
5. **Content limits:** text at most 4,000 chars, trimmed, non-empty. Reject or sanitize control chars. Store raw text and escape on the client (never store HTML).
6. **Row lock:** `UPDATE ... RETURNING` on `conversations.last_seq` serializes sends per conversation, which is enough for correctness. It becomes a hot spot only in very large rooms (see 7.6).

### 7.3 Message states

```
SENDING (client-only) → SENT (persisted, ack'd) → DELIVERED (reached recipient's device) → READ
                                   └──► FAILED (client-only, retry with same clientMessageId)
```
- **Delivered:** the recipient's client sends an ack frame after receiving. Persist per-member if you need it (Phase 2), otherwise derive from connection.
- **Read:** stored as `conversation_members.last_read_seq` (one number per member, not one row per message). "Read by all" means `min(last_read_seq)` of the other members. This scales.

### 7.4 Reconnect and missed messages (offline sync)

1. The client stores the last seen `seq` per conversation.
2. On reconnect, call `GET /conversations/{id}/messages?afterSeq={lastSeq}&limit=100`, repeating until empty.
3. Then resubscribe to the live topic. Dedupe by `id` or `seq` on the client, because live and history can overlap.
4. Client outbox: unsent messages are retried with the **same** `clientMessageId`.

### 7.5 History and pagination

- **Cursor-based, not offset:** `GET /conversations/{id}/messages?beforeSeq=500&limit=30` returns messages with `seq < 500`, descending. Fast with `idx_messages_conv_seq`.
- Response includes `hasMore` and `nextCursor`.
- Max `limit` 100.

### 7.6 Presence, typing, receipts

| Feature | Implementation |
|---|---|
| Online presence | Redis key `presence:user:{id}` with TTL 60 s, refreshed by heartbeat (STOMP heartbeat 10 s) and on `SessionConnect`/`Disconnect`. Multi-device: use a counter or set of session IDs. |
| Last seen | On last session disconnect, write `last_seen_at` to `users` (or Redis with long TTL). |
| Typing | Ephemeral only (never stored). Broadcast `{userId, typing:true}` with a 5 s auto-expire on the client. Throttle to 1 event per 2 s per user per conversation. |
| Read receipts | `chat.read {conversationId, upToSeq}` updates `last_read_seq` (only forward, never backward), then broadcasts a receipt event. |
| Unread count | `conversations.last_seq - member.last_read_seq`. |

### 7.7 Scaling the real-time layer

| Stage | Approach |
|---|---|
| Phase 1 (single node) | Spring's in-memory simple broker. |
| Phase 3 (multi-node) | **Redis pub/sub** relay: after commit, publish to `chat:conv:{id}`; every node has a subscriber that forwards to local sessions via `SimpMessagingTemplate`. Load balancer uses sticky sessions or not, since any node can receive. |
| Alternative | RabbitMQ STOMP broker relay (native Spring support). |
| Very large scale | Kafka for the message log, dedicated WebSocket gateway service, sharding by `conversationId`. |

Redis pub/sub is fire-and-forget. That is acceptable because the DB is the source of truth and the client re-syncs by `seq` (7.4).

---

## 8. REST API Contract (v1)

Base path: `/api/v1`. JSON only. Auth header: `Authorization: Bearer <jwt>` (except public routes).

### 8.1 Auth
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | none | Create account |
| POST | `/auth/login` | none | Returns tokens |
| POST | `/auth/refresh` | none (refresh token in body) | Rotate tokens |
| POST | `/auth/logout` | user | Revoke current refresh token |
| POST | `/auth/logout-all` | user | Revoke all sessions |
| POST | `/auth/change-password` | user | Requires current password; revokes other sessions |
| POST | `/auth/forgot-password` | none | Phase 2 (email token) |
| POST | `/auth/reset-password` | none | Phase 2 |
| POST | `/auth/verify-email` | none | Phase 2 |

### 8.2 Users
| Method | Path | Description |
|---|---|---|
| GET | `/users/me` | Current profile |
| PATCH | `/users/me` | Update display name, bio, avatar |
| GET | `/users/{id}` | Public profile |
| GET | `/users/search?q=&page=&size=` | Search by username/display name (min 2 chars, rate-limited) |
| POST/DELETE | `/users/{id}/block` | Block/unblock (Phase 2) |

### 8.3 Conversations
| Method | Path | Description |
|---|---|---|
| POST | `/conversations/direct` | Body `{userId}`. Get-or-create DM using `direct_key`. |
| POST | `/conversations/group` | Body `{title, memberIds[]}` |
| GET | `/conversations?cursor=&limit=` | My conversations sorted by `last_message_at`, with unread counts |
| GET | `/conversations/{id}` | Details and members |
| PATCH | `/conversations/{id}` | Group title/avatar (admin) |
| POST | `/conversations/{id}/members` | Add members (admin) |
| DELETE | `/conversations/{id}/members/{userId}` | Remove member or leave |
| PATCH | `/conversations/{id}/members/{userId}/role` | Promote/demote (owner) |
| POST | `/conversations/{id}/mute` | Mute until timestamp |

### 8.4 Messages
| Method | Path | Description |
|---|---|---|
| GET | `/conversations/{id}/messages?beforeSeq=&afterSeq=&limit=` | History and sync |
| POST | `/conversations/{id}/messages` | REST fallback for send (same idempotency rules) |
| PATCH | `/messages/{id}` | Edit (sender only, within 15 min; sets `edited_at`) |
| DELETE | `/messages/{id}` | Soft delete (sender or group admin) |
| POST | `/messages/{id}/reactions` | Add reaction (Phase 2) |
| DELETE | `/messages/{id}/reactions/{emoji}` | Remove reaction |
| GET | `/conversations/{id}/messages/search?q=` | Full-text search (Phase 3) |
| POST | `/conversations/{id}/read` | Body `{upToSeq}` (REST equivalent of `chat.read`) |

### 8.5 Media (Phase 2)
| Method | Path | Description |
|---|---|---|
| POST | `/media/presign` | Returns pre-signed upload URL and `storageKey` (validate mime and size) |
| POST | `/media/confirm` | Confirms upload, returns attachment ID to reference in a message |

### 8.6 Ops
`GET /actuator/health`, `/actuator/metrics`, `/actuator/prometheus` (protected in prod). Swagger UI at `/swagger-ui.html` (disabled in prod).

---

## 9. API Conventions

### 9.1 Status codes
`200` OK, `201` created (with `Location`), `204` no content, `400` validation, `401` unauthenticated, `403` forbidden (not a member), `404` not found, `409` conflict (duplicate username), `422` business rule violated, `429` rate limit, `500` unexpected.

### 9.2 Pagination
Lists that grow (messages, conversations) use **cursor** pagination. Small bounded lists (search results) may use `page/size`.

### 9.3 Error format (always the same)

```json
{
  "timestamp": "2026-01-15T10:22:31Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/auth/register",
  "traceId": "b7f2c1...",
  "errors": [ { "field": "email", "message": "must be a well-formed email address" } ]
}
```
Standard codes: `VALIDATION_ERROR`, `UNAUTHORIZED`, `TOKEN_EXPIRED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `BUSINESS_RULE`, `INTERNAL_ERROR`. Never leak stack traces or SQL.

### 9.4 Versioning
URL versioning (`/api/v1`). Additive changes stay in v1. Breaking changes go in v2.

---

## 10. Feature Roadmap: Basic → Medium → Advanced

### LEVEL 1: BASIC (MVP, make it solid first)

| # | Feature | Acceptance criteria |
|---|---|---|
| B1 | Registration and login | Unique username/email, hashed password, JWT access and refresh |
| B2 | Refresh and logout | Rotation works, revoked tokens rejected |
| B3 | User profile | View and edit own profile, view others |
| B4 | User search | Find users by username |
| B5 | Direct (1-to-1) chat | Get-or-create DM without duplicates |
| B6 | Send and receive text in real time | WebSocket, ack, persisted, ordered by `seq` |
| B7 | Message history | Cursor pagination |
| B8 | Conversation list | Sorted by latest activity, shows last message and unread count |
| B9 | Validation, error handling, logging | Global handler, request IDs |
| B10 | Docker + Flyway + Swagger | One-command local start |
| B11 | Baseline tests and CI | Unit tests for services, integration tests for auth and send |

**Exit criteria for Basic:** two users can register, log in, chat in real time, reload, and see history in the right order, with no duplicates after a forced reconnect.

### LEVEL 2: MEDIUM (product features and robustness)

| # | Feature |
|---|---|
| M1 | Group chats (create, add/remove, leave, roles OWNER/ADMIN/MEMBER) |
| M2 | Online/offline presence and last seen |
| M3 | Typing indicators |
| M4 | Read receipts and unread counters (`last_read_seq`) |
| M5 | Edit and delete message (soft delete, "edited" flag) |
| M6 | Reply to message |
| M7 | Reactions |
| M8 | Media messages (image/file via pre-signed upload) |
| M9 | Email verification, forgot/reset password |
| M10 | Block user; muted conversations |
| M11 | Rate limiting (Redis token bucket) on auth and message send |
| M12 | Offline sync by `afterSeq`; REST fallback for send |
| M13 | Audit of login history / device sessions list |
| M14 | Testcontainers integration test suite; test coverage at least 70% on services |

### LEVEL 3: ADVANCED (scale, ops, differentiation)

| # | Feature |
|---|---|
| A1 | Multi-instance real-time via Redis pub/sub (or RabbitMQ relay) |
| A2 | Push notifications (FCM/APNs) for offline users; per-conversation mute respected |
| A3 | Full-text message search (PostgreSQL `tsvector` first, OpenSearch later) |
| A4 | Message delivery status per recipient |
| A5 | Async pipeline with Kafka/RabbitMQ (notifications, search indexing, analytics) |
| A6 | Content moderation hooks: report message/user, admin tools, spam detection |
| A7 | Observability: Prometheus, Grafana dashboards, distributed tracing (OpenTelemetry) |
| A8 | Load testing (k6/Gatling) with published targets (for example 10k concurrent sockets per node) |
| A9 | Data retention and archiving (partition `messages` by month; cold storage) |
| A10 | Multi-device sessions, device management, remote logout |
| A11 | Optional end-to-end encryption design (Signal-style; big effort) |
| A12 | Kubernetes deployment, HPA, blue/green releases, secrets manager |
| A13 | Optional: threads, pinned messages, stories, bots/webhooks, OAuth2 social login, 2FA (TOTP) |

---

## 11. Security Checklist (must pass before each release)

- [ ] Every REST route has an explicit authorization rule (deny by default).
- [ ] Membership checked for every conversation/message access (REST and WS).
- [ ] Input validated (`@Valid`), max sizes enforced on body, text, and uploads.
- [ ] No IDOR: never trust IDs from the client without checking ownership or membership.
- [ ] Passwords hashed; refresh tokens hashed in DB; secrets from env.
- [ ] Rate limiting on auth, search, and send.
- [ ] CORS locked to known origins; WebSocket `Origin` check enabled.
- [ ] Security headers (HSTS, `X-Content-Type-Options`, etc.), HTTPS/WSS only in prod.
- [ ] SQL only via JPA/parameterized queries (no string concatenation).
- [ ] Uploads: whitelist mime types, size limit, random storage keys, no direct path use, virus scan (Advanced).
- [ ] Dependency scan (OWASP Dependency-Check or Dependabot) in CI.
- [ ] Logs contain no secrets or PII; message content is not logged.
- [ ] Actuator endpoints protected.

---

## 12. Performance and Reliability Guidelines

- Add DB indexes for every query path used in a hot endpoint (verify with `EXPLAIN ANALYZE`).
- Avoid N+1 queries: use `JOIN FETCH`, `@EntityGraph`, or DTO projections. Enable Hibernate statistics in tests for critical endpoints.
- Set explicit HikariCP pool sizes; set timeouts on every external call.
- WebSocket: configure heartbeats, message size limit (for example 64 KB), send-buffer limits, and inbound channel thread pool.
- Do slow work (push, indexing, emails) **off the request path** (`@Async` or a queue).
- Use Redis for ephemeral data only (presence, rate limits, pub/sub). PostgreSQL remains the source of truth.
- Graceful shutdown (`server.shutdown=graceful`).
- Health checks: liveness and readiness probes.

---

## 13. Testing Strategy

| Level | Tools | What |
|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ | Services, JWT logic, validators, mappers |
| Repository | `@DataJpaTest` + Testcontainers Postgres | Queries, constraints (idempotency, seq uniqueness) |
| Web slice | `@WebMvcTest` + MockMvc | Validation, status codes, error format, security rules |
| Integration | `@SpringBootTest` + Testcontainers (Postgres, Redis) | Full auth flow, send/receive, pagination |
| WebSocket | `StompSession` test client | Connect with/without token, subscribe authorization, send → ack → broadcast |
| Concurrency | `ExecutorService` + latches | 50 parallel sends in one conversation → unique gapless `seq`; duplicate `clientMessageId` → 1 row |
| Load | k6/Gatling (Advanced) | Connection count, latency |

**Mandatory tests for the core:** duplicate send is idempotent; non-member cannot send or subscribe; `seq` is gapless under concurrency; refresh token reuse revokes the family; pagination cursors are stable.

Naming: `methodName_condition_expectedResult`.

---

## 14. Configuration, Environments, Deployment

### 14.1 Environment variables
```
SPRING_PROFILES_ACTIVE=prod
DB_URL, DB_USER, DB_PASSWORD
REDIS_HOST, REDIS_PORT, REDIS_PASSWORD
JWT_SECRET (>= 32 bytes), JWT_ACCESS_TTL=PT15M, JWT_REFRESH_TTL=P14D
CORS_ALLOWED_ORIGINS=https://app.example.com
S3_ENDPOINT, S3_BUCKET, S3_ACCESS_KEY, S3_SECRET_KEY   (Phase 2)
```

### 14.2 Local development
`docker-compose.yml` runs Postgres, Redis, and (Phase 2) MinIO. Run `docker compose up -d && ./mvnw spring-boot:run`.

### 14.3 CI pipeline (each PR)
Build → format check → unit tests → integration tests (Testcontainers) → dependency scan → build Docker image.

### 14.4 Release
Semantic versioning, `CHANGELOG.md`, Docker image tagged with the git SHA, Flyway migrations run automatically at startup (backward compatible, so rolling deploys are safe).

---

## 15. Current-State Audit and Improvement Plan (for the existing project)

The project already has auth, some endpoints, and real-time chat, but quality is low. Run this **before adding new features**:

1. **Inventory:** list existing endpoints and map them to section 8. Note the gaps.
2. **Structure:** refactor into the package-by-feature layout (section 3). Remove business logic from controllers.
3. **DTOs:** stop returning entities. Introduce request/response records and MapStruct.
4. **Errors:** add `GlobalExceptionHandler` with the standard format (9.3).
5. **Auth hardening:** move to access+refresh with rotation, add password hashing check, rate limits.
6. **Schema:** move to Flyway; add `seq`, `client_message_id`, unique constraints, indexes.
7. **Real-time:** implement the flow in 7.2 (persist → ack → broadcast after commit), WS auth interceptor, subscription authorization.
8. **Tests:** add the mandatory tests from section 13.
9. **Config:** externalize secrets, add profiles, Dockerize.
10. **Docs:** OpenAPI annotations, README quick start.

Only after Basic exit criteria pass, start Medium.

---

## 16. Milestone Plan

| Milestone | Scope | Outcome |
|---|---|---|
| M0 | Section 15 audit and refactor | Clean base, tests, CI |
| M1 | Basic B1-B11 | Working 1-to-1 real-time chat (MVP) |
| M2 | Medium M1-M7 | Groups, presence, typing, receipts, edit/delete, reactions |
| M3 | Medium M8-M14 | Media, email flows, blocking, rate limiting, robust sync |
| M4 | Advanced A1-A4 | Multi-node, push, search, delivery status |
| M5 | Advanced A5-A10 | Async pipeline, moderation, observability, retention |
| M6 | Advanced A11-A13 | Hardening, Kubernetes, optional extras |

Track progress in `docs/PROGRESS.md` (checkbox list mirroring section 10).

---

## 17. Definition of Done (every task)

- [ ] Behavior matches this document (or the document was updated first).
- [ ] Input validated; authorization enforced; errors use the standard format.
- [ ] Unit tests and at least one integration test added and passing.
- [ ] Flyway migration added if the schema changed (never edit an applied migration).
- [ ] No new warnings; formatter/lint passes; no secrets or debug code committed.
- [ ] OpenAPI annotations updated for new or changed endpoints.
- [ ] Logs are meaningful and contain no sensitive data.
- [ ] `docs/PROGRESS.md` and this document updated if scope or design changed.

---

## 18. Glossary

- **Conversation:** a chat room; either `DIRECT` (2 users) or `GROUP`.
- **seq:** per-conversation strictly increasing message number.
- **clientMessageId:** client-generated idempotency key for a message.
- **ACK:** server confirmation that a message was persisted.
- **Fan-out:** delivering one message to all conversation members' connected sessions.
- **Rotation:** issuing a new refresh token on every refresh and invalidating the old one.
