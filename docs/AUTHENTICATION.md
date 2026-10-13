# Stateless REST Authentication

## Policy

The API uses JSON bodies and HttpOnly browser JWT cookies. Explicit Bearer headers remain supported for API clients. It creates no HTTP sessions, token database, refresh-session records or JWT denylist. Accounts and current roles/status still come from the user database.

Access JWTs expire after 15 minutes by default. Refresh JWTs expire seven days after login. Refresh exchange issues unique tokens but preserves the original refresh expiry; previously issued tokens remain valid. This is not revocable rotation.

Browser logout clears both HttpOnly authentication cookies through the logout endpoint; API clients delete locally held tokens. Password change/reset does not revoke access or refresh JWTs. A copied token remains usable until expiry while its account is active. Server logout-all, device revocation and admin force-logout return 422 instead of claiming to revoke tokens.

## Structure

```text
auth/
  AuthController.java          JSON HTTP contracts; validation; cookie login/refresh/logout
  AuthService.java             Registration, login, refresh and recovery rules
  dto/                         Request records and shared TokenResponse
  bootstrap/
    AdminBootstrapProperties.java
    AdminBootstrapRunner.java  Explicit admin provisioning; never public promotion
security/
  WebSecurityConfig.java       Public POST allowlist; stateless request security
  jwt/
    JwtProperties.java         Validated configuration
    JwtConfiguration.java      Configuration registration
    JwtUtils.java              Signing, token purpose and claim validation
    AuthTokenFilter.java       Cookie authentication with explicit Bearer override
    RestAuthenticationEntryPoint.java
    RestAccessDeniedHandler.java
  services/
    UserDetailsServiceImpl.java Current account status/role lookup
```

## Routes

All paths below start with `/api/v1/auth`. Bodies and responses are JSON except 204 responses.

| Method/path | Authentication | Input | Result |
|---|---|---|---|
| POST `/register` | Public | username, email, password, optional displayName | 201 regular user; verification email |
| POST `/login` | Public + CSRF | username, password | 200 TokenResponse and HttpOnly cookies |
| POST `/refresh` | Refresh cookie + CSRF | No body required; JSON refreshToken fallback | 200 TokenResponse and renewed cookies; unchanged refresh deadline |
| POST `/logout` | Public; browser CSRF still required | No body | 204; clear matching-path authentication cookies |
| POST `/logout-all` | Access Bearer | No body | 422 unsupported |
| POST `/change-password` | Access Bearer | currentPassword, newPassword | 200; JWTs remain valid |
| POST `/forgot-password` | Public | email | Generic 202; reset token sent only by email |
| POST `/reset-password` | Public | token, newPassword | 200; reset link becomes invalid after use |
| POST `/verify-email` | Public | token | 200 verified |
| POST `/resend-verification` | Public | email | Generic 202; token sent only by email |

Malformed JSON/validation errors return 400. Invalid credentials, expired tokens and wrong token types return 401. Existing account conflicts return 409. Unsupported server revocation returns 422 with the standard API error envelope.

## Browser flow

Browser auth uses `leo_chat_jwt` (access, Path=/) and `leo_chat_jwt_refresh` (refresh, Path=/api/v1/auth/refresh). Both are HttpOnly, host-only, and SameSite=Lax by default. Login/refresh/logout also expire the former access cookie at Path=/api, preventing legacy cookies from shadowing the new one. HTTPS production cookies default to Secure. Token responses remain JSON for existing API clients; browser clients can ignore those token fields and rely on cookies.

1. Fetch `GET /api/v1/auth/csrf` with `credentials: "include"`. Save the returned `token` and send it as `X-XSRF-TOKEN` for browser POST/PATCH/PUT/DELETE requests. The server also sets an XSRF-TOKEN cookie; this CSRF cookie is readable and contains no JWT.
2. Log in with JSON credentials and that CSRF header. The browser stores access and refresh cookies automatically.
3. Fetch protected resources with `credentials: "include"`. No Authorization header is needed.
4. Refresh using `POST /api/v1/auth/refresh` with credentials and CSRF header; no JSON body is necessary. Cookies are renewed without extending the original refresh deadline.
5. Log out using `POST /api/v1/auth/logout` with credentials and CSRF header. The server clears both cookies using their original paths. Copied JWTs remain valid until expiry.
6. Fetch a fresh CSRF token after authentication changes, logout or a CSRF rejection. Logout can clear cookies even when the access JWT is expired or the account is inactive.

```javascript
const api = "http://localhost:8080";
const csrf = await fetch(`${api}/api/v1/auth/csrf`, {
  credentials: "include"
}).then(response => response.json());

await fetch(`${api}/api/v1/auth/login`, {
  method: "POST",
  credentials: "include",
  headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrf.token },
  body: JSON.stringify({ username: "alice", password: "<password>" })
});

const profile = await fetch(`${api}/api/v1/users/me`, {
  credentials: "include"
}).then(response => response.json());
```

Only explicitly configured origins receive credentialed CORS access. Same-site localhost origins on different ports work with Lax cookies. For truly cross-site deployments, configure SameSite=None and Secure=true over HTTPS; cookie and CSRF settings must match. Browser privacy restrictions may still block third-party cookies.

Swagger UI is configured to copy XSRF-TOKEN into X-XSRF-TOKEN; fetch /auth/csrf first before using mutating operations.

Bearer-only clients without authentication cookies may skip CSRF. If an Authorization header is supplied, it takes precedence: an invalid Bearer token never falls back to a valid cookie. Requests carrying authentication cookies always require CSRF on writes, even if a Bearer header is also supplied.

Login/refresh retain TokenResponse with tokenType, expiresIn, refreshExpiresIn and user metadata, alongside accessToken/refreshToken for API compatibility. Responses include Cache-Control: no-store and Pragma: no-cache.

WebSocket is a separate real-time transport. Browser cookies authenticate the HTTP handshake and its STOMP CONNECT principal; API clients can still use an explicit access JWT in STOMP Authorization. The configured origin allowlist protects browser WebSocket handshakes. Expiry/status enforcement for already connected sockets remains future work.

## Configuration and migration

- `JWT_SECRET`: Base64-encoded random signing key with at least 32 decoded bytes. Never use the example secret in deployment.
- `JWT_ACCESS_EXPIRATION_MS`: default 900000.
- `JWT_REFRESH_EXPIRATION_MS`: default 604800000, measured from login rather than the latest refresh.
- `JWT_ISSUER`: default `leo-chat-api`.
- `JWT_AUDIENCE`: default `leo-chat-clients`.
- `FRONTEND_URL`: client origin with `/verify-email` and `/reset-password` pages that submit tokens to the corresponding REST POST routes. Default `http://localhost:3000`.
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM_ADDRESS`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS_ENABLED`, `MAIL_STARTTLS_REQUIRED`: configure email delivery. Dev defaults use localhost port 1025; no SMTP credentials are embedded in source.
- `app.cors.allowed-origins`: explicit comma-separated origins; credentialed CORS is enabled.
- `AUTH_COOKIE_SECURE`: production default true; local dev/test default false.
- `AUTH_COOKIE_SAME_SITE`: Lax by default; None requires Secure=true. Auth and CSRF cookies share these settings.
- `ADMIN_BOOTSTRAP_ENABLED=true`, `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`: enable explicit admin bootstrap. Password length is 12–40 characters. Existing admins are preserved; existing public accounts are never promoted automatically. The database single-admin index remains required.

Browser cookie transport is restored with CSRF protection. Existing Bearer API clients remain supported; browser clients should use credentials and obtain a CSRF token. Legacy JWTs without required issuer/audience/purpose/token-ID claims fail validation and require a fresh login. No database migration is needed for this auth change; existing V5 already removes refresh-token storage.

Rate limiting, proactive socket closure and the existing ownership-transfer repair are still outstanding. PostgreSQL migration readiness is a separate known gap.
