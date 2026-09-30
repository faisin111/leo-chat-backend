# Chat Application Backend: Complete Project Documentation

> **Audience:** developers and AI agents. This is the single source of truth for architecture, features, data model, APIs, real-time flow, security, testing, and roadmap.
> **Stack:** Java 21, Spring Boot 3.x, Spring Security (JWT), WebSocket (STOMP), PostgreSQL, Redis.
> **Related files:** `AGENT.md` (how an agent must behave), `SKILL.md` (how to write the code).

---

## 0. How to Use This Document

1. Read sections 1 to 4 to understand the goal, the architecture, and the rules.
2. Check section 15 (Roadmap) to find the current phase and the next task.
3. For any task, follow the module layout (section 5), the API contract (section 8), and the Definition of Done (section 17).
4. For roles and the single-admin rule read 5.4; for Swagger/OpenAPI rules read section 19 (mandatory for every endpoint).
5. Never invent behavior that contradicts this document. If something is missing, add it here first, then implement it.

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
├── admin               (AdminController(s), AdminService, AdminBootstrapRunner, AuditService, reports)
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
Login    ─► POST /api/v1/auth/login       → Set-Cookie: leo_chat_jwt, Set-Cookie: leo_chat_jwt_refresh
Call API ─► Cookie: leo_chat_jwt=<accessToken>
Refresh  ─► POST /api/v1/auth/refresh     → Set-Cookie: new tokens (old one invalidated)
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
- CSRF must be carefully considered because auth uses HttpOnly cookies (`leo_chat_jwt` and `leo_chat_jwt_refresh` with SameSite=Lax).

### 5.4 Authorization and Roles

The system has **exactly two roles**:

| Role | Who | Count |
|---|---|---|
| `USER` | Every registered person | Unlimited |
| `ADMIN` | The platform owner/operator | **Exactly one person, ever, at a time** |

**Single-admin rules (non-negotiable):**
1. **Database guarantee:** a partial unique index makes a second admin impossible, even under concurrency or a coding bug (see migration `V2`, section 6.3).
2. **No public path creates an admin.** `POST /auth/register` always creates `USER`. The request body must never contain or accept a `role` field.
3. **Bootstrap:** the one admin is created at first startup by `AdminBootstrapRunner` from environment variables `ADMIN_EMAIL`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`. It is idempotent: if an admin already exists, it does nothing. If two instances start at once, the unique index lets only one succeed and the other ignores the conflict. The bootstrapped admin has `must_change_password = true`.
4. **No promotion endpoint.** There is no API to "make user X an admin". The only way to change who is admin is the guarded **ownership transfer** (`POST /admin/transfer-ownership`): the current admin re-enters their password and names the target user; the server demotes the old admin and promotes the target in **one transaction** (demote first, then promote), revokes all sessions of both, and writes an audit log.
5. **The admin cannot be harmed by admin tools:** the admin cannot disable, ban, delete, block, or demote themselves through normal endpoints. `DELETE /users/me` is rejected for the admin (`BUSINESS_RULE`, "Transfer ownership first").
6. **Admin is also a normal chat user** (may chat, join groups). Admin powers are extra endpoints under `/api/v1/admin/**`.
7. **Privacy:** admin endpoints expose metadata, statistics, and reported or moderated content only. The admin **cannot read private conversation history** in general. Reading a message is allowed only when it was reported (`reports`), and that access is audit-logged.

**Enforcement layers (all three, defense in depth):**
- URL rule: `/api/v1/admin/**` requires `hasRole('ADMIN')`.
- Method security: `@PreAuthorize("hasRole('ADMIN')")` on every admin service/controller method (`@EnableMethodSecurity`).
- Live check: admin endpoints re-verify `role = ADMIN` and `status = ACTIVE` from the database (or a Redis-cached `token_version`), so a demoted admin loses power **immediately**, not after the 15-minute access token expires.

**Resource-level checks are mandatory for USER endpoints:** a user may read or send only in conversations where they are a **member**. Enforce in the service layer (`conversationService.assertActiveMember(userId, conversationId)`), not only at the URL level.

**Account statuses:** `ACTIVE`, `DISABLED` (by user or admin, can be restored), `BANNED` (by admin), `DELETED` (soft delete, data anonymized). Any non-`ACTIVE` status: login rejected, refresh tokens revoked, live WebSocket sessions closed.

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
  role          VARCHAR(20)  NOT NULL DEFAULT 'USER',     -- USER | ADMIN (only ONE admin, see V2)
  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE, DISABLED, BANNED, DELETED (see V2)
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

### 6.3 Migration `V2__roles_admin_audit_reports.sql` (single admin, audit, reports)

```sql
-- Roles: only two allowed values
ALTER TABLE users
  ADD CONSTRAINT chk_users_role   CHECK (role   IN ('ADMIN', 'USER')),
  ADD CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'BANNED', 'DELETED')),
  ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN last_seen_at         TIMESTAMPTZ,
  ADD COLUMN status_reason        VARCHAR(300),
  ADD COLUMN status_changed_at    TIMESTAMPTZ;

-- THE rule: at most ONE admin row can ever exist
CREATE UNIQUE INDEX uq_single_admin ON users (role) WHERE role = 'ADMIN';

-- Every sensitive action is recorded
CREATE TABLE audit_logs (
  id           UUID PRIMARY KEY,
  actor_id     UUID REFERENCES users(id),
  action       VARCHAR(60)  NOT NULL,        -- e.g. USER_BANNED, MESSAGE_REMOVED, OWNERSHIP_TRANSFERRED
  target_type  VARCHAR(30),                  -- USER | CONVERSATION | MESSAGE | REPORT
  target_id    UUID,
  details      JSONB,
  ip_address   VARCHAR(45),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_actor   ON audit_logs (actor_id, created_at DESC);

-- User reports of messages/users (moderation queue for the admin)
CREATE TABLE reports (
  id           UUID PRIMARY KEY,
  reporter_id  UUID NOT NULL REFERENCES users(id),
  target_type  VARCHAR(10) NOT NULL,         -- MESSAGE | USER
  target_id    UUID NOT NULL,
  reason       VARCHAR(30) NOT NULL,         -- SPAM | HARASSMENT | ABUSE | OTHER
  details      VARCHAR(500),
  status       VARCHAR(12) NOT NULL DEFAULT 'OPEN',   -- OPEN | RESOLVED | DISMISSED
  resolved_by  UUID REFERENCES users(id),
  resolution   VARCHAR(300),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at  TIMESTAMPTZ,
  UNIQUE (reporter_id, target_type, target_id)   -- one report per reporter per target
);
CREATE INDEX idx_reports_status ON reports (status, created_at DESC);

ALTER TABLE conversations
  ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE';   -- ACTIVE | LOCKED (admin)
ALTER TABLE conversation_members
  ADD COLUMN pinned_at   TIMESTAMPTZ,
  ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE messages
  ADD COLUMN removed_by_admin BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN removal_reason   VARCHAR(300);
```

**Admin bootstrap (application code, not SQL):** on startup, if no row has `role = 'ADMIN'` and the three `ADMIN_*` env variables are set, insert the admin. If the variables are missing, log a `WARN` ("No admin exists") and continue; the app still runs.

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
   │   id, seq, status: SENT}   │                                    │                          │
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

Base path: `/api/v1`. JSON only (except uploads to object storage). Auth header: `Authorization: Bearer <jwt>`.

**Legend**
- **Access:** `Public` = no token; `User` = any authenticated active user (including the admin); `Admin` = the single admin only.
- **Level:** `B` = Basic, `M` = Medium, `A` = Advanced (see section 10).
- Every endpoint is documented in Swagger following section 19.

### 8.1 Auth (`tag: Auth`)
| Method | Path | Access | Lvl | Description |
|---|---|---|---|---|
| POST | `/auth/register` | Public | B | Create a `USER` account (role can never be chosen) |
| POST | `/auth/login` | Public | B | Returns access + refresh tokens |
| POST | `/auth/refresh` | Public | B | Rotate tokens (refresh token in HttpOnly cookie) |
| POST | `/auth/logout` | User | B | Revoke current refresh token |
| POST | `/auth/logout-all` | User | B | Revoke all sessions of the user |
| POST | `/auth/change-password` | User | M | Requires current password; revokes other sessions; clears `must_change_password` |
| POST | `/auth/forgot-password` | Public | M | Email a reset token (always responds 202, no user enumeration) |
| POST | `/auth/reset-password` | Public | M | Set new password with token |
| POST | `/auth/verify-email` | Public | M | Confirm email token |
| POST | `/auth/resend-verification` | Public | M | Resend verification email (rate-limited) |

### 8.2 Users (`tag: Users`)
| Method | Path | Access | Lvl | Description |
|---|---|---|---|---|
| GET | `/users/me` | User | B | Current profile (includes `role`) |
| PATCH | `/users/me` | User | B | Update display name, bio, avatar |
| DELETE | `/users/me` | User | M | Deactivate own account (rejected for the admin) |
| GET | `/users/{id}` | User | B | Public profile |
| GET | `/users/search?q=&page=&size=` | User | B | Search by username/display name (min 2 chars, rate-limited) |
| GET | `/users/presence?ids=` | User | M | Bulk presence for up to 100 user IDs |
| GET | `/users/me/sessions` | User | M | List my devices/sessions |
| DELETE | `/users/me/sessions/{sessionId}` | User | M | Log out one device |
| GET | `/users/me/blocks` | User | M | List users I blocked |
| POST | `/users/{id}/block` | User | M | Block a user |
| DELETE | `/users/{id}/block` | User | M | Unblock a user |

### 8.3 Conversations (`tag: Conversations`)
| Method | Path | Access | Lvl | Description |
|---|---|---|---|---|
| POST | `/conversations/direct` | User | B | Body `{userId}`. Get-or-create DM via `direct_key` (returns 200 if existing, 201 if new) |
| GET | `/conversations?cursor=&limit=` | User | B | My conversations by `last_message_at`, with last message and unread count |
| GET | `/conversations/{id}` | User | B | Conversation details |
| GET | `/conversations/unread-count` | User | B | Total unread across conversations (badge) |
| POST | `/conversations/group` | User | M | Body `{title, memberIds[]}` |
| PATCH | `/conversations/{id}` | User | M | Group title/avatar (group admin/owner) |
| GET | `/conversations/{id}/members` | User | M | List members and roles |
| POST | `/conversations/{id}/members` | User | M | Add members (group admin/owner) |
| DELETE | `/conversations/{id}/members/{userId}` | User | M | Remove member, or leave if it is yourself |
| PATCH | `/conversations/{id}/members/{userId}/role` | User | M | Promote/demote (group owner) |
| PUT / DELETE | `/conversations/{id}/mute` | User | M | Mute until a timestamp / unmute |
| PUT / DELETE | `/conversations/{id}/pin` | User | M | Pin / unpin for me |
| PUT / DELETE | `/conversations/{id}/archive` | User | M | Archive / unarchive for me |

> Group roles (`OWNER/ADMIN/MEMBER`) are **conversation-level** and unrelated to the platform role `ADMIN`.

### 8.4 Messages (`tag: Messages`)
| Method | Path | Access | Lvl | Description |
|---|---|---|---|---|
| GET | `/conversations/{id}/messages?beforeSeq=&afterSeq=&limit=` | User (member) | B | History and offline sync |
| POST | `/conversations/{id}/messages` | User (member) | B | Send (REST fallback, idempotent by `clientMessageId`) |
| POST | `/conversations/{id}/read` | User (member) | B | Body `{upToSeq}` (REST twin of `chat.read`) |
| GET | `/messages/{id}` | User (member) | M | Single message |
| PATCH | `/messages/{id}` | User (sender) | M | Edit within 15 min; sets `edited_at` |
| DELETE | `/messages/{id}` | User (sender / group admin) | M | Soft delete |
| POST | `/messages/{id}/reactions` | User (member) | M | Add reaction |
| DELETE | `/messages/{id}/reactions/{emoji}` | User (member) | M | Remove my reaction |
| GET | `/conversations/{id}/media?cursor=&limit=` | User (member) | M | Shared images/files |
| POST | `/messages/{id}/report` | User (member) | M | Report to the admin `{reason, details}` |
| GET | `/conversations/{id}/messages/search?q=` | User (member) | A | Full-text search |

### 8.5 Media (`tag: Media`)
| Method | Path | Access | Lvl | Description |
|---|---|---|---|---|
| POST | `/media/presign` | User | M | Pre-signed upload URL and `storageKey` (mime and size validated) |
| POST | `/media/confirm` | User | M | Confirm upload, returns attachment ID for a message |

### 8.6 Admin (`tag: Admin`), single admin only
All routes: `Access = Admin`. All mutating routes write an `audit_logs` row.

| Method | Path | Lvl | Description |
|---|---|---|---|
| GET | `/admin/stats/overview` | M | Totals: users, active today, messages today, conversations, open reports, online now |
| GET | `/admin/users?q=&status=&cursor=&limit=` | M | List and filter users |
| GET | `/admin/users/{id}` | M | User detail (profile, status, counts, last seen; **no message content**) |
| PATCH | `/admin/users/{id}/status` | M | Body `{status: ACTIVE\|DISABLED\|BANNED, reason}`. Revokes tokens, closes sockets. Cannot target the admin. |
| POST | `/admin/users/{id}/force-logout` | M | Revoke all sessions and close sockets |
| DELETE | `/admin/users/{id}` | M | Soft delete and anonymize (cannot target the admin) |
| GET | `/admin/conversations?q=&status=&cursor=` | M | Conversation metadata (type, member count, created, last activity) |
| PATCH | `/admin/conversations/{id}/status` | A | Body `{status: ACTIVE\|LOCKED, reason}`. Locked = no new messages |
| GET | `/admin/reports?status=&cursor=` | A | Moderation queue |
| GET | `/admin/reports/{id}` | A | Report detail with the reported message (the only case where content is visible; audit-logged) |
| PATCH | `/admin/reports/{id}` | A | Resolve/dismiss `{resolution}`; optional action (remove message, ban user) |
| DELETE | `/admin/messages/{id}` | A | Remove a reported message (`removed_by_admin = true`, reason required) |
| GET | `/admin/audit-logs?actor=&action=&from=&to=&cursor=` | M | Browse audit trail |
| POST | `/admin/transfer-ownership` | A | Body `{targetUserId, password}`. Atomic admin handover (see 5.4) |

### 8.7 Ops (`tag: Ops`)
| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/actuator/health` (+ `/liveness`, `/readiness`) | Public | Health probes |
| GET | `/actuator/metrics`, `/actuator/prometheus` | Admin (or internal network) | Metrics |
| GET | `/swagger-ui.html`, `/v3/api-docs` | See section 19.7 | API docs |

### 8.8 Permission matrix

| Capability | Public | USER | ADMIN |
|---|:-:|:-:|:-:|
| Register / login / refresh | yes | yes | yes |
| Chat, groups, profile, block, report | no | yes | yes |
| Read own conversations only | no | yes | yes |
| Read anyone's private messages | no | no | **no** (only reported ones, audit-logged) |
| List / ban / disable / force-logout users | no | no | yes |
| Remove reported messages, lock conversations | no | no | yes |
| View stats and audit logs | no | no | yes |
| Transfer admin ownership | no | no | yes |
| Become admin by registering or self-promotion | no | no | n/a |

### 8.9 Example payloads

**Register** `POST /auth/register`
```json
{ "username": "alice_dev", "email": "alice@example.com", "password": "S3cure!Pass", "displayName": "Alice" }
```
Response `201`:
```json
{ "id": "0190a3c4-7f1e-7a2b-9c11-2f4b6d8e0a11", "username": "alice_dev", "displayName": "Alice", "role": "USER" }
```

**Login** `POST /auth/login` → `200`
Response Headers:
`Set-Cookie: leo_chat_jwt=eyJhbGciOi...; Path=/api; HttpOnly; SameSite=Lax`
`Set-Cookie: leo_chat_jwt_refresh=0ff9a1bd...; Path=/api/v1/auth/refresh; HttpOnly; SameSite=Lax`
Response Body:
```json
{ "id": "uuid", "username": "alice", "email": "alice@example.com" }
```

**Send message** `POST /conversations/{id}/messages` → `201`
```json
// request
{ "clientMessageId": "6f1d2c9e-3b1a-4c55-9d0e-1a2b3c4d5e6f", "content": "Hello!" }
// response
{ "id": "0190a3c5-...", "conversationId": "0190a3c4-...", "senderId": "0190a3c4-...", "seq": 42,
  "clientMessageId": "6f1d2c9e-...", "type": "TEXT", "content": "Hello!", "createdAt": "2026-01-15T10:22:31Z" }
```

**Admin: ban user** `PATCH /admin/users/{id}/status` → `200`
```json
{ "status": "BANNED", "reason": "Repeated spam after warning" }
```

**Cursor page shape (all cursor lists)**
```json
{ "items": [ /* ... */ ], "hasMore": true, "nextCursor": "412" }
```

---

## 9. API Conventions

### 9.1 Status codes
`200` OK, `201` created (with `Location`), `202` accepted (async, e.g. forgot-password), `204` no content, `400` validation, `401` unauthenticated, `403` forbidden (not a member), `404` not found, `409` conflict (duplicate username), `422` business rule violated, `429` rate limit, `500` unexpected.

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
| B12 | Two roles (`USER`, `ADMIN`), single-admin bootstrap and DB guarantee (5.4, 6.3) | Register can never create an admin; `uq_single_admin` in place |
| B13 | Swagger baseline (section 19): groups, tags, security scheme, error docs on every endpoint | Swagger UI usable end to end with the Authorize button |

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
| M15 | Admin console: stats, user list/ban/disable/force-logout, audit logs (8.6) |
| M16 | Swagger contract test in CI (19.10) and full examples for all endpoints |

### LEVEL 3: ADVANCED (scale, ops, differentiation)

| # | Feature |
|---|---|
| A1 | Multi-instance real-time via Redis pub/sub (or RabbitMQ relay) |
| A2 | Push notifications (FCM/APNs) for offline users; per-conversation mute respected |
| A3 | Full-text message search (PostgreSQL `tsvector` first, OpenSearch later) |
| A4 | Message delivery status per recipient |
| A5 | Async pipeline with Kafka/RabbitMQ (notifications, search indexing, analytics) |
| A6 | Moderation: reports queue, remove messages, lock conversations, spam detection (8.6) |
| A7 | Observability: Prometheus, Grafana dashboards, distributed tracing (OpenTelemetry) |
| A8 | Load testing (k6/Gatling) with published targets (for example 10k concurrent sockets per node) |
| A9 | Data retention and archiving (partition `messages` by month; cold storage) |
| A10 | Multi-device sessions, device management, remote logout |
| A11 | Optional end-to-end encryption design (Signal-style; big effort) |
| A12 | Kubernetes deployment, HPA, blue/green releases, secrets manager |
| A13 | Optional: threads, pinned messages, stories, bots/webhooks, OAuth2 social login, 2FA (TOTP) |
| A14 | Admin ownership transfer, exported `openapi.json` + Spectral lint + generated client SDKs |

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
- [ ] Exactly one admin: `uq_single_admin` exists; no endpoint accepts a `role`; admin routes have URL + `@PreAuthorize` + live DB check.
- [ ] Admin cannot read private messages except reported ones (audit-logged); all admin mutations write `audit_logs`.
- [ ] Swagger disabled (or protected) in prod.

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
ADMIN_EMAIL, ADMIN_USERNAME, ADMIN_PASSWORD   (first-start admin bootstrap; used only if no admin exists)
APP_PUBLIC_URL=https://api.example.com        (shown as the Swagger server URL)
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
- [ ] Swagger annotations complete per section 19.8 (summary, operationId, tag, examples, errors, security) and the OpenAPI contract test passes.
- [ ] Logs are meaningful and contain no sensitive data.
- [ ] `docs/PROGRESS.md` and this document updated if scope or design changed.

---

## 18. Glossary

- **Conversation:** a chat room; either `DIRECT` (2 users) or `GROUP`.
- **seq:** per-conversation strictly increasing message number.
- **clientMessageId:** client-generated idempotency key for a message.
- **ACK:** server confirmation that a message was persisted.
- **Fan-out:** delivering one message to all conversation members' connected sessions.
- **ADMIN:** the single platform owner role (exactly one person). Not the same as group roles.
- **Group role:** `OWNER/ADMIN/MEMBER` inside one conversation.
- **operationId:** unique stable name of an API operation in OpenAPI (`messages_send`).
- **Rotation:** issuing a new refresh token on every refresh and invalidating the old one.

---

## 19. Swagger / OpenAPI Documentation Standards

**Goal:** a Swagger UI that is clean, consistently named, grouped by audience, self-explanatory for a new developer, and impossible to let rot (enforced by tests in CI). Library: **springdoc-openapi** (OpenAPI 3.1 output, Swagger UI).

### 19.1 Dependency

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>2.x (use the latest 2.x compatible with your Spring Boot 3.x)</version>
</dependency>
```

### 19.2 Look and behavior (`application.yml`)

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha              # tag order is controlled by numbered tag names (19.4)
    operations-sorter: method       # GET, POST, PUT, PATCH, DELETE inside each tag
    doc-expansion: none             # start collapsed = clean first view
    display-request-duration: true
    persist-authorization: true     # keep the token after page refresh
    filter: true                    # search box
    default-models-expand-depth: 0  # hide the long Schemas list by default
    default-model-expand-depth: 2
    try-it-out-enabled: true
  default-produces-media-type: application/json
  default-consumes-media-type: application/json
  show-actuator: false
  writer-with-order-by-keys: true

---
spring.config.activate.on-profile: prod
springdoc:
  api-docs.enabled: false
  swagger-ui.enabled: false
```

### 19.3 API groups (three clean views)

Use `GroupedOpenApi` so each audience sees only its endpoints:

| Group | Paths | Purpose |
|---|---|---|
| `1-public` | `/api/v1/auth/**` | Sign-up, login, tokens: first thing a new developer tries |
| `2-user` | `/api/v1/**` excluding `/admin/**` | Everything a normal user (and the admin as a chat user) does |
| `3-admin` | `/api/v1/admin/**` | Single-admin console endpoints |

```java
@Bean GroupedOpenApi publicApi() {
  return GroupedOpenApi.builder().group("1-public").displayName("Public (Auth)")
      .pathsToMatch("/api/v1/auth/**").build();
}
@Bean GroupedOpenApi userApi() {
  return GroupedOpenApi.builder().group("2-user").displayName("User")
      .pathsToMatch("/api/v1/**").pathsToExclude("/api/v1/admin/**", "/api/v1/auth/**").build();
}
@Bean GroupedOpenApi adminApi() {
  return GroupedOpenApi.builder().group("3-admin").displayName("Admin (single owner)")
      .pathsToMatch("/api/v1/admin/**").build();
}
```

### 19.4 Global OpenAPI definition (title, description, security, ordered tags)

```java
@Configuration
class OpenApiConfig {

  @Bean OpenAPI chatOpenApi(AppProperties props) {
    return new OpenAPI()
      .info(new Info()
        .title("ChatApp API")
        .version("v1")
        .description("""
          Real-time chat backend: authentication, users, conversations, messages, moderation.

          ## Quick start
          1. `POST /api/v1/auth/register` to create an account.
          2. `POST /api/v1/auth/login` and copy `accessToken`.
          3. Click **Authorize** (top right), paste the token (without `Bearer`), then try any endpoint.

          ## Roles
          | Role | Access |
          |---|---|
          | `USER` | Chat, profile, groups, reports |
          | `ADMIN` | Exactly one person. Admin console under `/admin/**` |

          ## Conventions
          - Errors always use the `ApiError` schema.
          - Lists use cursor pagination (`items`, `hasMore`, `nextCursor`).
          - Send endpoints are idempotent through `clientMessageId`.

          ## Real-time (WebSocket)
          Swagger cannot describe STOMP. Connect to `/ws` with header `Authorization: Bearer <token>`.
          | Direction | Destination | Purpose |
          |---|---|---|
          | send | `/app/chat.send` | Send message |
          | send | `/app/chat.typing` | Typing |
          | send | `/app/chat.read` | Mark read |
          | subscribe | `/topic/conversations.{id}` | Live events |
          | subscribe | `/user/queue/acks` | Send acknowledgements |
          | subscribe | `/user/queue/errors` | Errors |
          """)
        .contact(new Contact().name("ChatApp Team").email("dev@example.com"))
        .license(new License().name("Proprietary")))
      .servers(List.of(new Server().url(props.publicUrl()).description("Current environment")))
      .components(new Components().addSecuritySchemes("bearerAuth",
        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
          .description("Access token from `/auth/login`")))
      .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))   // secured by default
      .tags(List.of(
        new Tag().name("01 Auth").description("Register, login, refresh, logout, password flows"),
        new Tag().name("02 Users").description("Profile, search, presence, sessions, blocking"),
        new Tag().name("03 Conversations").description("Direct and group chats, members, mute, pin, archive"),
        new Tag().name("04 Messages").description("History, send, edit, delete, reactions, reports"),
        new Tag().name("05 Media").description("Pre-signed uploads and attachments"),
        new Tag().name("06 Admin").description("Single-admin console: users, moderation, audit, stats"),
        new Tag().name("07 Ops").description("Health and metrics")));
  }
}
```
Tag names use a numeric prefix so the order is fixed and readable. Every controller has exactly one `@Tag(name = "0X Name")` from this list.

### 19.5 Reusable error documentation (write once, use everywhere)

Composed annotations keep controllers short. Public endpoints use `@PublicEndpoint` (no lock icon); everything else is secured by default.

```java
@Target({METHOD, TYPE}) @Retention(RUNTIME)
@ApiResponses({
  @ApiResponse(responseCode = "400", description = "Validation failed",
      content = @Content(schema = @Schema(implementation = ApiError.class),
      examples = @ExampleObject(name = "VALIDATION_ERROR", value = """
        {"status":400,"code":"VALIDATION_ERROR","message":"Request validation failed",
         "errors":[{"field":"email","message":"must be a well-formed email address"}]}"""))),
  @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired token",
      content = @Content(schema = @Schema(implementation = ApiError.class))),
  @ApiResponse(responseCode = "403", description = "Not allowed (not a member, or not the admin)",
      content = @Content(schema = @Schema(implementation = ApiError.class))),
  @ApiResponse(responseCode = "500", description = "Unexpected error",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public @interface StandardErrors {}

@Target(METHOD) @Retention(RUNTIME)
@SecurityRequirements            // empty = no auth required, removes the lock icon
public @interface PublicEndpoint {}
```

### 19.6 Auto-label admin endpoints (so nobody forgets)

An `OperationCustomizer` reads `@PreAuthorize` and appends a visible line to the description:

```java
@Bean OperationCustomizer roleBadge() {
  return (operation, handlerMethod) -> {
    PreAuthorize pre = handlerMethod.getMethodAnnotation(PreAuthorize.class);
    if (pre == null) pre = handlerMethod.getBeanType().getAnnotation(PreAuthorize.class);
    if (pre != null && pre.value().contains("ADMIN")) {
      operation.setDescription("**🔒 Admin only.** " + Optional.ofNullable(operation.getDescription()).orElse(""));
    }
    return operation;
  };
}
```

### 19.7 Who may open Swagger

| Environment | Swagger UI / `/v3/api-docs` |
|---|---|
| `local`, `test` | Open (`permitAll`) |
| `staging` | Enabled, protected (Basic auth or ADMIN only) |
| `prod` | **Disabled** (19.2). Publish a static `openapi.json` artifact from CI to your internal docs site instead |

### 19.8 Rules for every endpoint (the "beautiful Swagger" checklist)

Every controller method must have:
1. **`@Operation`** with `summary` (max 8 words, imperative, Title Case: "Send a message"), a `description` (rules, limits, side effects, who can call it), and an explicit **`operationId`** in `tag_action` form (`auth_login`, `messages_send`, `admin_ban_user`). It keeps generated client SDK names stable.
2. **One `@Tag`** from 19.4.
3. **Success response** with code (`200/201/204`) and a documented schema. Include a realistic **example**.
4. **`@StandardErrors`**, plus endpoint-specific responses (`404`, `409` duplicate, `422` business rule, `429` with `Retry-After`) with an `@ExampleObject` each.
5. **`@Parameter(description, example)`** for every path/query parameter, including defaults, min, max (`limit` 1-100, default 30).
6. **DTO fields documented** with `@Schema(description, example, requiredMode, maxLength)`.
7. **Security:** secured by default. Public routes carry `@PublicEndpoint`. Admin routes carry `@PreAuthorize("hasRole('ADMIN')")` (the badge appears automatically).
8. **No entities in schemas.** Only DTO records. Give generics concrete names to avoid ugly schema names: `@Schema(name = "MessageCursorPage")`.
9. **Enums documented** with allowed values (`@Schema(allowableValues = {...})` or plain enums).
10. **Deprecations** use `@Deprecated` + `@Operation(deprecated = true)` with the replacement in the description.

### 19.9 Full endpoint example (copy this shape)

```java
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "06 Admin")
@PreAuthorize("hasRole('ADMIN')")
@StandardErrors
class AdminUserController {

  private final AdminUserService adminUserService;

  @PatchMapping("/{id}/status")
  @Operation(
      operationId = "admin_change_user_status",
      summary = "Change a user's status",
      description = """
        Sets the user to `ACTIVE`, `DISABLED`, or `BANNED`.
        Side effects: all refresh tokens are revoked and live WebSocket sessions are closed.
        The admin account cannot be targeted. Writes an audit log entry.""")
  @ApiResponse(responseCode = "200", description = "Status updated")
  @ApiResponse(responseCode = "404", description = "User not found")
  @ApiResponse(responseCode = "422", description = "Target is the admin account",
      content = @Content(schema = @Schema(implementation = ApiError.class),
      examples = @ExampleObject(value = """
        {"status":422,"code":"BUSINESS_RULE","message":"The admin account cannot be modified"}""")))
  AdminUserResponse changeStatus(
      @Parameter(description = "User ID", example = "0190a3c4-7f1e-7a2b-9c11-2f4b6d8e0a11")
      @PathVariable UUID id,
      @Valid @RequestBody ChangeStatusRequest request,
      @AuthenticationPrincipal AuthUser admin) {
    return adminUserService.changeStatus(admin.id(), id, request);
  }
}

public record ChangeStatusRequest(
    @Schema(description = "New status", example = "BANNED", allowableValues = {"ACTIVE","DISABLED","BANNED"})
    @NotNull UserStatus status,
    @Schema(description = "Why (kept in the audit log)", example = "Repeated spam after warning", maxLength = 300)
    @NotBlank @Size(max = 300) String reason
) {}
```

### 19.10 Keeping Swagger correct (automated)

- **Contract test** (runs in CI): start the app, fetch `/v3/api-docs`, and assert for **every operation**: has `summary`, `operationId` (unique), exactly one tag from the allowed list, a `401` response unless public, and a `403` response for admin paths. The build fails otherwise.
- **Admin-security test:** iterate all `/api/v1/admin/**` operations and assert a `USER` token gets `403` and no token gets `401`.
- **Single-admin tests:** registering never yields `ADMIN`; inserting a second `ADMIN` row violates `uq_single_admin`; ownership transfer leaves exactly one admin; a demoted admin's old token is rejected on admin routes immediately.
- **Export and lint:** CI exports `openapi.json` (springdoc-openapi Maven/Gradle plugin) and lints it (Spectral). The file is published as a build artifact. Client SDKs can be generated from it.
- **Change rule:** any endpoint added, renamed, or changed must update its Swagger annotations in the same change (Definition of Done, section 17).
