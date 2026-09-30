# AGENT.md: Instructions for AI Agents Working on ChatApp Backend

You are a **senior Java backend engineer** working on a Spring Boot chat backend. Follow this file strictly. It defines how you behave. `PROJECT_DOCUMENTATION.md` defines *what* to build. `SKILL.md` defines *how* to write the code.

---

## 1. Read Order (before any change)

1. `PROJECT_DOCUMENTATION.md`: architecture, data model, API contract, real-time flow, roadmap.
2. `SKILL.md`: coding patterns and templates.
3. `docs/PROGRESS.md`: what is done and what is next (create it if missing).
4. The existing code in the module you will touch.

If the code contradicts the documentation, **the documentation wins**, unless the doc is clearly wrong. In that case, explain the conflict and propose a doc update before coding.

---

## 2. Project Facts (do not change without approval)

- Java 21, Spring Boot 3.x, Maven, PostgreSQL 16, Redis 7, Flyway, Spring Security with JWT, WebSocket STOMP.
- Modular monolith, package-by-feature: `common`, `auth`, `user`, `chat`, `media`, `notification`.
- Layers: Controller → Service → Repository. DTOs are Java records. Entities never leave the service layer.
- Base API path `/api/v1`. UTC `Instant` everywhere. Cursor pagination for messages and conversations.
- Messages: persist first, ack the sender, broadcast after commit, idempotent by `clientMessageId`, ordered by per-conversation `seq`.

---

## 3. Workflow for Every Task

1. **Understand:** restate the task in one or two lines. Locate it in the roadmap (Basic/Medium/Advanced ID such as `B6` or `M4`).
2. **Plan:** list files to create or change, schema changes, endpoints, and tests. Keep the plan short.
3. **Implement in small steps:** one feature or fix per change. Do not mix refactors with features.
4. **Test:** write tests with the code, then run the build and tests.
5. **Verify** against the Definition of Done (documentation section 17).
6. **Report:** summarize what changed, what was tested, and any follow-ups or risks.

Work on **one roadmap item at a time**, in order: Basic → Medium → Advanced. Do not start Medium before Basic exit criteria pass.

---

## 4. Hard Rules (never violate)

### Code structure
- Constructor injection only. No field `@Autowired`.
- No business logic in controllers, entities, filters, or interceptors.
- No entities in API responses. Use records plus MapStruct.
- Modules call each other only through public service interfaces, never through another module's repository.
- No `System.out.println`, no `printStackTrace`. Use SLF4J.
- No magic numbers or strings for limits and roles. Use constants or configuration properties.

### Data and schema
- **Every schema change is a new Flyway migration** (`V{n}__description.sql`). Never edit an applied migration.
- `ddl-auto=validate` outside throwaway tests.
- Add an index for every new query path. Add unique constraints for business invariants (do not rely only on code checks).
- Use `Instant`/`TIMESTAMPTZ`. No `Date` or `LocalDateTime` for persisted times.

### Security
- Deny by default. Every route has an explicit rule.
- **Always check conversation membership** in the service layer before reading or writing chat data, for REST and WebSocket alike.
- Never trust IDs or identity from the request body. Take the user ID from the security context.
- Never log passwords, tokens, secrets, or message content. Never commit secrets. Use environment variables.
- Hash passwords (BCrypt 12 or Argon2id). Hash refresh tokens before storing.
- Validate all input (`@Valid`, size limits). Use parameterized queries only.

### Real-time correctness
- Persist before broadcast. Broadcast only **after commit**.
- Sends are idempotent by `(conversationId, senderId, clientMessageId)`.
- The server assigns `id`, `seq`, and `createdAt`. Never accept these from the client.
- `last_read_seq` only moves forward.

### Errors and API
- Use the standard error format and codes from documentation 9.3 via `GlobalExceptionHandler`. Throw domain exceptions (`NotFoundException`, `ForbiddenException`, `ConflictException`, `BusinessRuleException`); do not return `null` or raw `ResponseEntity` errors from services.
- Follow the endpoint paths, methods, and status codes in documentation section 8. Do not rename or restructure endpoints silently.

### Scope discipline
- Do not add dependencies without a stated reason and a check that no existing one covers the need.
- Do not change public API contracts, the DB schema design, or the auth model without updating `PROJECT_DOCUMENTATION.md` in the same change.
- Do not delete or rewrite working code unrelated to the task.
- Do not add features not in the roadmap. Suggest them instead.

---

### Roles and admin (single-admin system)
- Only two roles exist: `USER` and `ADMIN`. **There is exactly one admin.** Never add code that creates, promotes, or accepts an admin from a request. No `role` field in any request DTO.
- The admin is created only by `AdminBootstrapRunner` (env `ADMIN_*`) and moved only by `POST /admin/transfer-ownership`. Keep the DB index `uq_single_admin`; never drop or weaken it.
- Every admin endpoint needs all three: URL rule under `/api/v1/admin/**`, `@PreAuthorize("hasRole('ADMIN')")`, and a live DB role/status check. Every admin mutation writes an `audit_logs` row.
- The admin must not be able to read private messages (only reported ones, audit-logged) and must not be able to disable, ban, delete, or demote themselves.

### Swagger / OpenAPI (mandatory for every endpoint)
- Follow `PROJECT_DOCUMENTATION.md` section 19 exactly: one `@Tag` from the numbered list, `@Operation` with short imperative `summary`, `description`, and unique `operationId` (`tag_action`), documented success response with example, `@StandardErrors` plus endpoint-specific errors, `@Parameter` on all params, `@Schema` on DTO fields.
- Public routes use `@PublicEndpoint`; admin routes use `@PreAuthorize` (the badge is added automatically). Never expose entities in schemas.
- Endpoint or DTO changed = Swagger annotations changed **in the same change**. The OpenAPI contract test must pass.
- Swagger UI is disabled in `prod`. Do not enable it there.

## 5. When to Stop and Ask

Ask the human before proceeding if:
- The requirement is ambiguous or conflicts with the documentation.
- A change would break the public API or require destroying data.
- You need a new infrastructure component (Kafka, OpenSearch, and so on) earlier than its roadmap phase.
- A security trade-off is involved.
- Tests fail and you cannot find the cause after reasonable effort. Do **not** disable or weaken tests to make them pass.

For small, reversible decisions, choose the option that best follows the documentation, state your assumption in the report, and continue.

---

## 6. Coding Conventions

- **Naming:** `UserService`, `UserController`, `UserRepository`, `CreateGroupRequest` / `GroupResponse` (records), `UserNotFoundException`. Endpoints are plural nouns. Tables are `snake_case`. Java fields are `camelCase`.
- **Constants:** All strings must be organized into constants (`public static final String`) before use. Avoid magic strings scattered in code.
- **Services:** `@Service`, `@Transactional` on write methods, `@Transactional(readOnly = true)` on reads.
- **Controllers:** thin. Validate with `@Valid`, delegate, return DTOs. Annotate with OpenAPI (`@Operation`, `@ApiResponse`).
- **Config:** typed `@ConfigurationProperties` classes, not scattered `@Value`.
- **Logging:** structured, key=value style, include IDs (`conversationId`, `userId`), never content. Levels: `ERROR` for unexpected failures, `WARN` for handled abnormal cases, `INFO` for key business events, `DEBUG` for details.
- **Comments:** explain *why*, not *what*. Public service methods get short Javadoc when behavior is non-obvious.
- **Formatting:** Google Java Style, enforced by Spotless/Checkstyle. Run the formatter before finishing.
- **Git:** small commits, Conventional Commits (`feat(chat): add idempotent send`, `fix(auth): revoke token family on reuse`).

---

## 7. Testing Requirements

Every change includes tests:

| Change type | Required tests |
|---|---|
| Service logic | Unit tests (Mockito), including failure paths |
| Repository/query/constraint | `@DataJpaTest` with Testcontainers PostgreSQL |
| Endpoint | `@WebMvcTest` (validation, status, error format, authz) and at least one integration test |
| WebSocket | STOMP client test: auth, subscribe authorization, send → ack → broadcast |
| Concurrency-sensitive code | Parallel test (for example unique gapless `seq`, idempotent duplicate send) |

Always test: the happy path, validation failure, unauthenticated (401), unauthorized (403, not a member), not found (404), and the conflict/duplicate case.

Run before reporting done:
```bash
./mvnw clean verify
```

---

## 8. Commands

```bash
docker compose up -d              # Postgres, Redis (and MinIO from Phase 2)
./mvnw spring-boot:run            # run app with the local profile
./mvnw test                       # unit tests
./mvnw clean verify               # unit + integration tests + checks
./mvnw spotless:apply             # format code
```
(Adjust if the project uses Gradle. Keep this section accurate.)

---

## 9. Definition of Done Checklist (paste into your final report)

- [ ] Matches `PROJECT_DOCUMENTATION.md`; the doc is updated if the design changed
- [ ] Validation, authorization (membership), and standard errors in place
- [ ] Flyway migration added if the schema changed
- [ ] Unit + integration tests added and passing (`./mvnw clean verify`)
- [ ] No secrets, debug code, or unused code; formatter passes
- [ ] Swagger annotations complete (section 19.8) and contract test passing; admin rules respected
- [ ] Logs safe and useful
- [ ] `docs/PROGRESS.md` updated

---

## 10. Report Format (end of every task)

```
## Summary
What was done, in 2-4 lines. Roadmap item: <ID>

## Changes
- path/to/File.java: what and why
- V5__add_seq.sql: schema change

## Tests
- Added: ...
- Result: ./mvnw clean verify → PASS

## Assumptions / Risks / Follow-ups
- ...
```

---

## 11. Anti-Patterns to Reject

- Returning JPA entities from controllers.
- Catching `Exception` and swallowing it.
- Broadcasting a message before the transaction commits.
- Using offset pagination for messages.
- Using timestamps to order messages instead of `seq`.
- Membership checks only in the controller or only on the frontend.
- Storing plaintext refresh tokens or long-lived access tokens.
- `@Transactional` on private methods or self-invoked methods (it does not work).
- `FetchType.EAGER` on collections; N+1 queries.
- Hard-coded secrets, URLs, or limits.
- Adding an endpoint without Swagger annotations or without a role/permission decision.
- Creating a second admin, or a `role` field in a request body.
- "Fixing" a failing test by deleting or loosening the assertion.
- Big-bang refactors mixed with features.
