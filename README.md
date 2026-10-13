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
Copy the example environment file and fill in your secrets (or leave the defaults for local testing):
```bash
cp .env.example .env
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

- JWT authentication with HttpOnly cookies; refresh-token revocation and server-side logout remain unimplemented.
- REST and STOMP message sending with database persistence and sender ACKs; after-commit broadcast and subscription authorization need repairs.
- Direct/group conversations, profile/search, message history, read pointers, edit/delete and admin endpoints exist.
- Conversation pagination uses page offsets. Message `afterSeq` sync is not implemented.
- Reactions, device sessions and media storage remain stubs.
- Current package map and ordered repair plan live in `PROJECT_DOCUMENTATION.md`, sections 3 and 15.

## Testing
Current tests cover auth service behavior and an H2 application-context smoke test. Run them using:
```bash
./mvnw test
```
