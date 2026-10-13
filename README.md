# Leo Chat Backend

A chat backend built with Java 17, Spring Boot 3.3.4, WebSocket (STOMP), PostgreSQL, and Flyway.

**Status (2026-10-13):** Core APIs exist, but security, migrations and several product features need repairs. See [current audit and plan](PROJECT_DOCUMENTATION.md#15-current-state-audit-and-improvement-plan). Redis and MinIO run in Compose but are not integrated into application services.

## Quick Start (Local Development)

### Prerequisites
- JDK 17+
- Docker and Docker Compose

### 1. Start Infrastructure
Run the following command to start PostgreSQL, Redis, and MinIO locally:
```bash
docker compose up -d
```

### 2. Configure Environment
Copy the example environment file and configure your secrets. `JWT_SECRET` must contain a Base64-encoded random key with at least 32 decoded bytes:
```bash
cp .env.example .env
# Generate a signing key, then paste it into JWT_SECRET in .env:
openssl rand -base64 32
```

### 3. Run the Application
Start the Spring Boot server with the default `dev` profile. Flyway runs on startup, but `V0__init.sql` does not create tables. A fresh database requires migration repair before this setup is reliable.
```bash
./mvnw spring-boot:run
```

The server will start on `http://localhost:8080`.

### 4. API Documentation
Once the server is running, explore the API endpoints using Swagger UI:
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

## Current Implementation

- Stateless HttpOnly JWT cookies with browser CSRF protection, JSON token responses and Bearer API compatibility. See [authentication flow](docs/AUTHENTICATION.md).
- REST and STOMP message sending with database persistence, ACK-before-broadcast after commit, retry deduplication and authorized conversation subscriptions.
- Direct/group conversations, profile/search, message history, read pointers, edit/delete and admin endpoints exist.
- Conversation pagination uses page offsets. Message history supports bounded `beforeSeq` and ascending `afterSeq` sync.
- Reactions persist. Device sessions are intentionally unsupported; media storage remains a stub.
- Current package map and ordered repair plan live in `PROJECT_DOCUMENTATION.md`, sections 3 and 15.

## Testing
Tests cover auth behavior, message services, all eight message REST routes, STOMP delivery/authorization, concurrent sends/retries and message OpenAPI metadata on H2. PostgreSQL migration validation remains outstanding. Run them using:
```bash
./mvnw test
```

Admin provisioning is explicit: set `ADMIN_BOOTSTRAP_ENABLED=true` and `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`. Public registration always creates ROLE_USER.
