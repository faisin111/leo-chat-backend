# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Multiple AI conversations per user: `GET /api/v1/ai/conversations`, `DELETE /api/v1/ai/conversations/{id}`; a new client UUID in `POST /api/v1/ai/chat` creates a new AI conversation
- `POST /api/v1/ai/chat/regenerate` replaces an AI answer without storing a duplicate prompt
- `POST /api/v1/ai/chat` accepts `model`, `reasoning`, `webSearch`, and text `attachments`
- Complete implementation of 8.1 Auth endpoints (JWT access/refresh)
- Complete implementation of 8.2 Users API and search
- Complete implementation of 8.3 Conversations API (DMs and Groups)
- Complete implementation of 8.4 Messages API (pagination, edit, delete, reactions)
- Complete implementation of 8.5 Media presign/confirm flows
- Complete implementation of 8.6 Admin moderation and stats endpoints
- Section 9: Strict Cursor pagination, standardized ApiError handling, HTTP status enforcement
- Section 11: Security headers, CORS lock-down, single-admin DB constraint, WebSocket origin checks
- Section 12: HikariCP sizing, Actuator health/liveness probes, Async support, DB Indexes
- Section 13: JUnit 5 and Mockito test suite setup
- Section 14: Dockerfile, docker-compose.yml, GitHub Actions CI Pipeline, Flyway dependencies

### Fixed
- `/ai` inside a normal chat now answers in that chat instead of the personal AI conversation
- AI prompts no longer include the current request twice in the model context
- Retried AI requests no longer call Gemini again when the answer is already stored
- Deleting an AI message no longer fails with a NullPointerException

### Changed
- Refactored all endpoints to return strictly typed DTOs instead of raw Entities
- Switched to stateless JWT architecture

### Security
- Plugged Privilege Escalation vulnerability in registration
