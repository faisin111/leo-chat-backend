# Leo Chat Backend

A real-time, highly scalable chat backend built with Spring Boot 3, WebSocket (STOMP), PostgreSQL, and Redis.

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
Start the Spring Boot server. Flyway will automatically handle database migrations.
```bash
./mvnw spring-boot:run
```

The server will start on `http://localhost:8080`.

### 4. API Documentation
Once the server is running, explore the API endpoints using Swagger UI:
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

## Features Implemented
- **JWT Authentication:** Secure stateless access/refresh token rotation using HttpOnly cookies.
- **Real-time Messaging:** STOMP over WebSocket with custom `WsAuthInterceptor` for session authorization.
- **Conversations & Groups:** 1-to-1 Direct Messages and Group Chats with member RBAC (Owner, Admin, Member).
- **Cursor Pagination:** Fast, gapless pagination for messages and conversations.
- **Admin Moderation:** Lock conversations, ban users, delete messages, and view system stats.
- **Performance Optimized:** Configured with HikariCP, database B-Tree indexes, and WebSocket thread pools.

## Testing
Run the unit and web-slice tests using:
```bash
./mvnw test
```
