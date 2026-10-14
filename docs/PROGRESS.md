# Progress Tracker

This document mirrors Section 10 of the Project Documentation to track implementation progress.

## Audit baseline — 2026-10-13

Earlier completion marks were unsupported by the current source and have been reset. An unchecked item may contain working code or a scaffold; it means the complete behavior and required tests are not verified. The original documentation audit did not run a build; the messaging repair below adds executable regression checks.

- Auth, profile/search, direct/group conversations, messaging, read pointers and admin APIs have implementation code.
- Stateless auth supersedes revocation requirements; admin transfer and remaining real-time features need repairs.
- Reactions persist; image media now uses private Supabase Storage presigned S3 upload/download URLs. Device sessions are intentionally unsupported under stateless auth.
- PostgreSQL fresh-install migrations and full-project OpenAPI contracts still need tests; messaging concurrency and reconnect sync now have H2 coverage.
- Actuator is present; dashboards, tracing and full Prometheus integration are not verified.
- Current structure: `PROJECT_DOCUMENTATION.md` section 3. Prioritized repair plan: section 15.2.

Check items only after their acceptance criteria and tests pass. Complete Basic repairs before Medium or Advanced work.

## LEVEL 1: BASIC (MVP)
- [ ] **B1** Registration and login (Unique username/email, hashed password, JWT access and refresh)
- [ ] **B2** Stateless refresh and client logout (Purpose/expiry validated; original refresh deadline preserved)
- [ ] **B3** User profile (View and edit own profile, view others)
- [ ] **B4** User search (Find users by username)
- [ ] **B5** Direct (1-to-1) chat (Get-or-create DM without duplicates)
- [ ] **B6** Send and receive text in real time (WebSocket, ack, persisted, ordered by `seq`)
- [ ] **B7** Message history (Cursor pagination)
- [ ] **B8** Conversation list (Sorted by latest activity, shows last message and unread count)
- [ ] **B9** Validation, error handling, logging (Global handler, request IDs)
- [ ] **B10** Docker + Flyway + Swagger (One-command local start)
- [ ] **B11** Baseline tests and CI (Unit tests for services, integration tests for auth and send)
- [ ] **B12** Two roles, single-admin bootstrap and DB guarantee (uq_single_admin in place)
- [ ] **B13** Swagger baseline (groups, tags, security scheme, error docs on every endpoint)

## LEVEL 2: MEDIUM
- [ ] **M1** Group chats (create, add/remove, leave, roles OWNER/ADMIN/MEMBER)
- [ ] **M2** Online/offline presence and last seen (REST endpoints implemented)
- [ ] **M3** Typing indicators (Scaffolded in WS)
- [ ] **M4** Read receipts and unread counters (`last_read_seq`)
- [ ] **M5** Edit and delete message (soft delete, "edited" flag)
- [ ] **M6** Message reactions (emoji, list of users)
- [ ] **M7** Mention parsing and notification (`@username`)
- [ ] **M8** Media attachments (presign, upload to S3/MinIO, confirm)
- [ ] **M9** Email verification and forgot password flow
- [ ] **M10** User blocking (hide messages from blocked users)
- [ ] **M11** Rate limiting (Login, Send Message, Search)
- [ ] **M12** Message deduplication (`client_message_id`) and idempotent sends
- [ ] **M13** Robust reconnection (fetch gap via `seq` when websocket reconnects)
- [ ] **M14** Reporting mechanism (report user or message to admin)
- [ ] **M15** Graceful shutdown, connection pools, query tuning (N+1 protection)
- [ ] **M16** Swagger contract test in CI and full examples for all endpoints

## LEVEL 3: ADVANCED
- [ ] **A1** Multi-instance real-time via Redis pub/sub
- [ ] **A2** Push notifications (FCM/APNs) for offline users
- [ ] **A3** Full-text message search (PostgreSQL tsvector)
- [ ] **A4** Message delivery status per recipient
- [ ] **A5** Async pipeline with Kafka/RabbitMQ
- [ ] **A6** Moderation: reports queue, remove messages, lock conversations, spam detection (Admin API implemented)
- [ ] **A7** Observability: Prometheus, Grafana dashboards, distributed tracing
- [ ] **A8** Load testing (k6/Gatling)
- [ ] **A9** Data retention and archiving
- [ ] **A10** Multi-device sessions, device management, remote logout
- [ ] **A11** Optional end-to-end encryption design
- [ ] **A12** Kubernetes deployment, HPA, blue/green releases, secrets manager
- [ ] **A13** Optional: threads, pinned messages, stories, bots/webhooks, OAuth2 social login, 2FA
- [ ] **A14** Admin ownership transfer, exported openapi.json

## Message and WebSocket Repairs (2026-10-13)

- Repaired B6 send authorization, transaction ordering, retry serialization and ACK delivery; concurrent sends and retries have regression tests.
- Repaired B7 bounded history, ascending `afterSeq` sync and cursor validation.
- Existing message endpoints now persist reactions, authorize edits/deletes, hide deleted content and advance read state atomically.
- STOMP requires access tokens, restricts subscriptions and SEND destinations, checks typing/read membership, returns handler errors and shares configured origins with HTTP.
- Added service tests, MockMvc endpoint checks, real STOMP client tests and H2 integration/concurrency checks. PostgreSQL/Flyway validation remains outstanding because Docker is unavailable here.
- Basic phase completion remains unchecked: fresh-schema migrations, auth revocation, admin bootstrap and other documented acceptance gaps remain.

Verification: `./mvnw clean verify` passed with 22 tests, zero failures/errors/skips. Spotless checked all 22 changed/new Java files successfully; `git diff --check` passed. Tests include native STOMP send/ACK/broadcast, rejected CONNECT/SUBSCRIBE, all eight message REST routes, service transaction callbacks, H2 concurrency and message OpenAPI metadata. No schema or dependency changes were required.

## Fully Stateless REST Auth (2026-10-13)

User explicitly selected fully stateless JWTs and client-only logout. This supersedes the previous revocable-refresh plan.

- Login and refresh return JSON token pairs and expiry metadata; authentication cookies and HTTP sessions are removed.
- Bearer headers authenticate protected routes. Public auth POST endpoints are individually allowlisted.
- JWT issuer/audience/purpose/expiry/ID are validated; refresh exchange preserves the original deadline and old tokens remain valid.
- Logout returns 204 acknowledgement; logout-all, device logout and admin force-logout reject unsupported server revocation with 422.
- Password/recovery flows use domain errors and generic responses; recovery tokens go only by email and are not printed.
- Registration always creates regular users; explicit optional admin bootstrap replaces first-registration promotion.
- Active account/role checks remain database-backed. Rate limiting, ownership-transfer repair, proactive socket closure and PostgreSQL migration checks remain outstanding.
- Contract, client migration, configuration and package structure: `docs/AUTHENTICATION.md`.

- Removed tracked SMTP credentials from development config and replaced them with environment settings. Any previously exposed SMTP credential must be rotated externally. Recovery links use the configured frontend URL.

Auth verification: `./mvnw clean verify` passed with 36 tests, zero failures/errors/skips. This includes Bearer-only JSON auth, original refresh expiry/reuse, client-only logout, public/protected contracts, account status enforcement, recovery/password behavior, bootstrap rules, JWT issuer/audience/purpose/expiry checks, concurrent registration and the earlier REST/STOMP messaging regressions. Changed Java files passed Spotless; PostgreSQL/Flyway remains unverified.

## Browser Cookie Auth (2026-10-13)

Latest user request restores HttpOnly access/refresh cookies while retaining stateless JWTs and Bearer API compatibility. Browser writes use cookie CSRF tokens and X-XSRF-TOKEN; credentialed CORS uses explicit origins. Refresh reads its cookie, logout clears matching-path cookies, and browser WebSocket handshakes can use the access cookie. Token revocation remains unsupported by design. See docs/AUTHENTICATION.md for the current contract.

Cookie auth verification: `./mvnw clean verify` passed with 40 tests, zero failures/errors/skips. Browser tests exercise real CSRF cookies/headers, cookie-only profile access, refresh without a body, matching-path logout cleanup, expired-cookie logout, credentialed CORS and cookie-authenticated WebSocket CONNECT. Bearer compatibility and earlier auth/message regressions also pass. Changed Java files passed Spotless; `git diff --check` passed.
