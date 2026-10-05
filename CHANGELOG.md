# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
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

### Changed
- Refactored all endpoints to return strictly typed DTOs instead of raw Entities
- Switched to stateless JWT architecture

### Security
- Plugged Privilege Escalation vulnerability in registration
