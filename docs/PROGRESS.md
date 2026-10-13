# Progress Tracker

This document mirrors Section 10 of the Project Documentation to track implementation progress.

## Audit baseline — 2026-10-13

Earlier completion marks were unsupported by the current source and have been reset. An unchecked item may contain working code or a scaffold; it means the complete behavior and required tests are not verified. No build was run for this documentation update.

- Auth, profile/search, direct/group conversations, messaging, read pointers and admin APIs have implementation code.
- Auth revocation, admin bootstrap/transfer, WebSocket authorization and after-commit broadcast need repairs.
- Reactions and media are scaffolds; device session operations are stubs.
- PostgreSQL fresh-install migrations, concurrent messaging, reconnect sync and OpenAPI contracts need tests.
- Actuator is present; dashboards, tracing and full Prometheus integration are not verified.
- Current structure: `PROJECT_DOCUMENTATION.md` section 3. Prioritized repair plan: section 15.2.

Check items only after their acceptance criteria and tests pass. Complete Basic repairs before Medium or Advanced work.

## LEVEL 1: BASIC (MVP)
- [ ] **B1** Registration and login (Unique username/email, hashed password, JWT access and refresh)
- [ ] **B2** Refresh and logout (Rotation works, revoked tokens rejected)
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
