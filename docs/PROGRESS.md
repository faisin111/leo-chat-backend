# Progress Tracker

This document mirrors Section 10 of the Project Documentation to track implementation progress.

## LEVEL 1: BASIC (MVP)
- [x] **B1** Registration and login (Unique username/email, hashed password, JWT access and refresh)
- [x] **B2** Refresh and logout (Rotation works, revoked tokens rejected)
- [x] **B3** User profile (View and edit own profile, view others)
- [x] **B4** User search (Find users by username)
- [x] **B5** Direct (1-to-1) chat (Get-or-create DM without duplicates)
- [x] **B6** Send and receive text in real time (WebSocket, ack, persisted, ordered by `seq`)
- [x] **B7** Message history (Cursor pagination)
- [x] **B8** Conversation list (Sorted by latest activity, shows last message and unread count)
- [x] **B9** Validation, error handling, logging (Global handler, request IDs)
- [x] **B10** Docker + Flyway + Swagger (One-command local start)
- [x] **B11** Baseline tests and CI (Unit tests for services, integration tests for auth and send)
- [x] **B12** Two roles, single-admin bootstrap and DB guarantee (uq_single_admin in place)
- [x] **B13** Swagger baseline (groups, tags, security scheme, error docs on every endpoint)

## LEVEL 2: MEDIUM
- [x] **M1** Group chats (create, add/remove, leave, roles OWNER/ADMIN/MEMBER)
- [x] **M2** Online/offline presence and last seen (REST endpoints implemented)
- [x] **M3** Typing indicators (Scaffolded in WS)
- [x] **M4** Read receipts and unread counters (`last_read_seq`)
- [x] **M5** Edit and delete message (soft delete, "edited" flag)
- [x] **M6** Message reactions (emoji, list of users)
- [x] **M7** Mention parsing and notification (`@username`)
- [x] **M8** Media attachments (presign, upload to S3/MinIO, confirm)
- [x] **M9** Email verification and forgot password flow
- [x] **M10** User blocking (hide messages from blocked users)
- [x] **M11** Rate limiting (Login, Send Message, Search)
- [x] **M12** Message deduplication (`client_message_id`) and idempotent sends
- [x] **M13** Robust reconnection (fetch gap via `seq` when websocket reconnects)
- [x] **M14** Reporting mechanism (report user or message to admin)
- [x] **M15** Graceful shutdown, connection pools, query tuning (N+1 protection)
- [x] **M16** Swagger contract test in CI and full examples for all endpoints

## LEVEL 3: ADVANCED
- [ ] **A1** Multi-instance real-time via Redis pub/sub
- [ ] **A2** Push notifications (FCM/APNs) for offline users
- [ ] **A3** Full-text message search (PostgreSQL tsvector)
- [ ] **A4** Message delivery status per recipient
- [ ] **A5** Async pipeline with Kafka/RabbitMQ
- [x] **A6** Moderation: reports queue, remove messages, lock conversations, spam detection (Admin API implemented)
- [x] **A7** Observability: Prometheus, Grafana dashboards, distributed tracing
- [ ] **A8** Load testing (k6/Gatling)
- [ ] **A9** Data retention and archiving
- [x] **A10** Multi-device sessions, device management, remote logout
- [ ] **A11** Optional end-to-end encryption design
- [ ] **A12** Kubernetes deployment, HPA, blue/green releases, secrets manager
- [ ] **A13** Optional: threads, pinned messages, stories, bots/webhooks, OAuth2 social login, 2FA
- [x] **A14** Admin ownership transfer, exported openapi.json
