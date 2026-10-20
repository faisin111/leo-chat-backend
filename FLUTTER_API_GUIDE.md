# LeoChat Flutter API Guide

This is the complete contract for building the Flutter mobile client. It covers REST, cookie/JWT authentication, WebSocket chat, media uploads, admin APIs, JSON shapes, validation, and errors.

## 1. Project connection

Use the deployed backend URL in production. Do not hard-code localhost:

```dart
const apiBaseUrl = 'https://leo-chat-backend.onrender.com/api/v1';
const wsUrl = 'wss://leo-chat-backend.onrender.com/ws';
```

Use `http` or `dio` for REST and `stomp_dart_client` for STOMP. Keep the base URL in a build-time environment setting so staging and production can differ.

## 2. Flutter authentication setup

The backend uses short-lived access JWTs plus database-backed refresh sessions. Login sets HttpOnly access and refresh cookies and also returns the token pair as JSON. Flutter cannot read HttpOnly cookies, so use a cookie jar (`dio_cookie_manager` + `cookie_jar`) and send cookies automatically. `POST /auth/refresh` refreshes the stored refresh session. Do not put passwords or refresh tokens in logs.

Recommended client flow:

1. `POST /auth/login`.
2. Persist the cookie jar in secure storage if the app should stay signed in.
3. Send cookies on every protected REST request.
4. On `401`, call `/auth/refresh`, then retry the request once.
5. On logout, call `/auth/logout`, clear the cookie jar, and clear local user state.

Bearer tokens are also supported for clients that cannot use cookies:

```dart
final response = await dio.post('/auth/login', data: {
  'username': username,
  'password': password,
});
final accessToken = response.data['accessToken'];
dio.options.headers['Authorization'] = 'Bearer $accessToken';
```

For cookie mode, browser-style CSRF is required only for mutating requests when the client is using ambient cookies. Call `GET /auth/csrf`, then send the returned token in the returned header (normally `X-XSRF-TOKEN`). Bearer requests do not need CSRF.

## 3. Shared JSON and error rules

All IDs are UUID strings. All timestamps are UTC ISO-8601 strings. JSON requests use `Content-Type: application/json`.

Success message:

```json
{ "message": "Operation completed successfully" }
```

Cursor page:

```json
{ "items": [], "hasMore": false, "nextCursor": null }
```

HTTP error shape:

```json
{
  "timestamp": "2026-10-17T10:20:30Z",
  "status": 422,
  "code": "BUSINESS_RULE",
  "message": "Message is deleted",
  "path": "/api/v1/messages/uuid",
  "traceId": "uuid",
  "errors": []
}
```

Status handling:

| Status | Flutter action |
|---:|---|
| 200 | Decode response normally. |
| 201 | Resource created; decode response. |
| 202 | Accepted; show neutral recovery/verification message. |
| 204 | Success with no body. |
| 400 | Fix request/query validation. |
| 401 | Refresh once; otherwise navigate to login. |
| 403 | User lacks permission, membership, or CSRF is missing. |
| 404 | Resource no longer exists; remove stale local item. |
| 409 | Duplicate username/email, duplicate reaction, or data conflict. |
| 422 | Business rule failed; show `message`. |
| 500 | Show retry message and include `traceId` in support logs. |

Flutter error decoder:

```dart
String apiError(Object error) {
  if (error is DioException && error.response?.data is Map) {
    final body = Map<String, dynamic>.from(error.response!.data);
    return body['message']?.toString() ?? 'Request failed';
  }
  return 'Network error';
}
```

Base URL: `/api/v1`

All JSON requests use `Content-Type: application/json`. IDs are UUID strings. Timestamps are ISO-8601 UTC strings. Protected HTTP routes accept the browser cookies created by login. Frontend requests must use `credentials: "include"`.

## Authentication

### Browser setup

1. `POST /auth/login` returns an access token and refresh token. Store the access token in memory and attach it as `Authorization: Bearer <accessToken>` on every API request.
2. On a `401`, call `POST /auth/refresh` with `{ "refreshToken": "..." }`, replace the access token, then retry once.
3. `POST /auth/logout` revokes the current refresh session and clears browser cookies. The current access JWT remains valid only until its short expiry.
4. CSRF is only needed when using browser cookies without a Bearer header. Token clients do not call `/auth/csrf`.

Bearer API requests:

```js
const response = await fetch(`${API}/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ username, password })
});

const tokens = await response.json();
const api = (path, options = {}) => fetch(`${API}${path}`, {
  ...options,
  headers: {
    "Content-Type": "application/json",
    ...(options.headers || {}),
    Authorization: `Bearer ${tokens.accessToken}`
  }
});
```

### Common response objects

Token response:

```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "refreshToken": "jwt",
  "expiresIn": 900,
  "refreshExpiresIn": 604800,
  "id": "uuid",
  "username": "alice",
  "email": "alice@example.com"
}
```

Message response:

```json
{ "message": "Operation completed successfully" }
```

Page response:

```json
{ "items": [], "hasMore": false, "nextCursor": null }
```

### Auth endpoints

| Method | Path | Auth | Success | Request body |
|---|---|---|---:|---|
| GET | `/auth/csrf` | Public | 200 | none |
| POST | `/auth/register` | Public | 201 | `username`, `email`, `password`, optional `displayName` |
| POST | `/auth/login` | Public | 200 | `username`, `password` |
| POST | `/auth/refresh` | Cookie or JSON | 200 | optional `{ "refreshToken": "jwt" }` |
| POST | `/auth/logout` | Public | 204 | none |
| POST | `/auth/logout-all` | User | 204 | revokes all refresh sessions |
| POST | `/auth/change-password` | User | 200 | `currentPassword`, `newPassword` |
| POST | `/auth/forgot-password` | Public | 202 | `{ "email": "..." }` |
| POST | `/auth/reset-password` | Public | 200 | `token`, `newPassword` |
| POST | `/auth/verify-email` | Public | 200 | `{ "token": "..." }` |
| POST | `/auth/resend-verification` | Public | 202 | `{ "email": "..." }` |

Registration validation: username 3–20 characters using letters, numbers, `.`, `_`, `-`; password 8–40 characters with upper, lower, number, and special character. Password changes and resets use 8–40 characters.

Token response:

```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "refreshToken": "jwt",
  "expiresIn": 900,
  "refreshExpiresIn": 604800,
  "id": "uuid",
  "username": "alice",
  "email": "alice@example.com"
}
```

Register response contains the created account, verification state, and access/refresh token fields. Registration creates a refresh session and sets auth cookies, so Flutter can open the main page immediately. Recovery and verification endpoints intentionally return generic `202` responses so account existence is not exposed.

## Users

All user routes require authentication.

| Method | Path | Success | Request/query |
|---|---|---:|---|
| GET | `/users/me` | 200 | returns `username` and `displayName` |
| GET | `/users/{id}` | 200 | UUID path parameter; returns `username` and `displayName` |
| PATCH | `/users/me/profile` | 200 | profile fields below |
| DELETE | `/users/me` | 200 | Deactivates own account; admin cannot use this |
| GET | `/users/search?q=alice&page=0&size=20` | 200 | `q` required; page ≥ 0; size 1–100 |
| GET | `/users/presence?ids={uuid}&ids={uuid}` | 200 | one or more UUIDs |
| GET | `/users/me/sessions` | 200 | stored sessions with device, IP, expiry, active state, and `logoutAt` |
| DELETE | `/users/me/sessions/{sessionId}` | 200 | revoke one session and record `logoutAt` |

Profile update body:

```json
{
  "profilePictureUrl": "https://...",
  "phoneNumber": "+15551234567",
  "bio": "Hello",
  "age": 30,
  "region": "US",
  "displayName": "Alice"
}
```

User profile response fields: `id`, `username`, `email`, `role`, `profilePictureUrl`, `phoneNumber`, `bio`, `age`, `region`, `isEmailVerified`.

Session item:

```json
{
  "sessionId": "uuid",
  "createdAt": "2026-10-20T10:00:00Z",
  "lastUsedAt": "2026-10-20T10:15:00Z",
  "expiresAt": "2026-10-27T10:00:00Z",
  "logoutAt": null,
  "userAgent": "Flutter/3.x",
  "ipAddress": "203.0.113.10",
  "active": true
}
```

`GET /users/me/sessions` returns current and historical sessions. `DELETE /users/me/sessions/{sessionId}` revokes one session and records `logoutAt`.

## Conversations

All conversation routes require authentication and active membership where an ID is used.

| Method | Path | Success | Request/query |
|---|---|---:|---|
| GET | `/conversations?cursor=0&limit=50&q=alice` | 200 | cursor page; searches conversation title, participant username, or display name; limit 1–100 |
| POST | `/conversations/direct` | 200 | `{ "targetUserId": "uuid" }` |
| POST | `/conversations/group` | 201 | `{ "title": "Team", "memberIds": ["uuid"] }` |
| GET | `/conversations/{id}` | 200 | none |
| GET | `/conversations/unread-count` | 200 | none |
| PATCH | `/conversations/{id}` | 200 | optional `title`, `avatarUrl` |
| GET | `/conversations/{id}/members` | 200 | none |
| POST | `/conversations/{id}/members` | 200 | `{ "memberIds": ["uuid"] }` |
| DELETE | `/conversations/{id}/members/{userId}` | 200 | none; removes or leaves |
| PATCH | `/conversations/{id}/members/{userId}/role` | 200 | `{ "role": "ADMIN" | "MEMBER" }` |
| PUT | `/conversations/{id}/mute` | 200 | `{ "mutedUntil": "2026-10-14T12:00:00Z" }` |
| DELETE | `/conversations/{id}/mute` | 200 | none |
| PUT | `/conversations/{id}/pin` | 200 | none |
| DELETE | `/conversations/{id}/pin` | 200 | none |
| PUT | `/conversations/{id}/archive` | 200 | none |
| DELETE | `/conversations/{id}/archive` | 200 | none |

Conversation response fields: `id`, `type` (`DIRECT` or `GROUP`), `title`, `avatarUrl`, `directKey`, `createdBy`, `lastSeq`, `lastMessageAt`, `createdAt`, `status`, and optional direct-chat target fields.

## Messages

All message routes require active conversation membership.

| Method | Path | Success | Request/query |
|---|---|---:|---|
| GET | `/conversations/{id}/messages?beforeSeq=10&limit=50` | 200 | history; use `beforeSeq` for older messages |
| GET | `/conversations/{id}/messages?afterSeq=10&limit=50` | 200 | history; use `afterSeq` for ascending sync |
| GET | `/conversations/{id}/messages?q=invoice&limit=50` | 200 | searches message content for this conversation |
| POST | `/conversations/{id}/messages` | 200 | message body below |
| POST | `/conversations/{id}/read` | 200 | `{ "upToSeq": 10 }` |
| GET | `/messages/{id}` | 200 | message UUID |
| PATCH | `/messages/{id}` | 200 | `{ "content": "edited" }` |
| DELETE | `/messages/{id}` | 200 | none |
| POST | `/messages/{id}/reactions` | 200 | `{ "emoji": "👍" }` |
| DELETE | `/messages/{id}/reactions/{emoji}` | 200 | URL-encode emoji |

Send body:

```json
{
  "conversationId": "uuid",
  "clientMessageId": "client-generated-retry-key",
  "type": "TEXT",
  "content": "Hello",
  "replyToId": null
}
```

Message types: `TEXT`, `IMAGE`, `FILE`, and `VOICE`; `SYSTEM` is server-only. `content` is limited to 4000 characters and `clientMessageId` to 64. Message response also includes `senderUsername`, `senderDisplayName`, `replyTo` (a non-recursive replied-message preview), `reactions` (each emoji with `count` and every reacting user's `id`, `username`, and `displayName`), and `deleted`. Deleted messages keep their ID and timestamps, return `deleted: true`, and return `content: null` so clients can render “Message deleted”.

## Media

All media routes require authentication. Storage must be enabled with a private Supabase bucket named `images`.

| Method | Path | Success | Request |
|---|---|---:|---|
| POST | `/media/presign` | 200 | `{ "mimeType": "image/png", "sizeInBytes": 12345 }` |
| POST | `/media/confirm` | 200 | `{ "storageKey": "users/{userId}/{id}.png" }` |

Supported MIME types: images, documents, `audio/mpeg`, `audio/wav`, `audio/webm`, `audio/ogg`, and video types. The configured server limit is 10 MiB. Presign returns `uploadUrl`, `method` (`PUT`), `contentType`, `sizeInBytes`, `bucket`, `storageKey`, and `expiresIn`. Upload bytes directly to `uploadUrl`, preserving the returned content type and size. Confirm returns a private short-lived download `url`.

## Administration

All admin routes require the single active `ROLE_ADMIN` account.

| Method | Path | Success | Request/query |
|---|---|---:|---|
| GET | `/admin/stats/overview` | 200 | none |
| GET | `/admin/users?q=alice&status=ACTIVE&cursor=0&limit=50` | 200 | optional filters; limit 1–100 |
| GET | `/admin/users/{id}` | 200 | none |
| PATCH | `/admin/users/{id}/status` | 200 | `{ "status": "ACTIVE|DISABLED|BANNED", "reason": "..." }` |
| POST | `/admin/users/{id}/force-logout` | 200 | revokes all refresh sessions for the user |
| DELETE | `/admin/users/{id}` | 200 | soft-delete and anonymize |
| GET | `/admin/conversations?q=team&status=ACTIVE&cursor=0` | 200 | optional filters |
| PATCH | `/admin/conversations/{id}/status` | 200 | `{ "status": "ACTIVE|ARCHIVED|DELETED", "reason": "..." }` |
| GET | `/admin/reports?status=OPEN&cursor=0` | 200 | optional status |
| GET | `/admin/reports/{id}` | 200 | none |
| PATCH | `/admin/reports/{id}` | 200 | `{ "resolution": "...", "action": "..." }` |
| DELETE | `/admin/messages/{id}?reason=spam` | 200 | reason query required, 1–500 chars |
| GET | `/admin/audit-logs?actor={uuid}&action=LOGIN&cursor=0` | 200 | optional filters |
| POST | `/admin/transfer-ownership` | 200 | `{ "targetUserId": "uuid", "password": "current-admin-password" }` |

## WebSocket / STOMP

Connect to `/ws` with the access cookie or `Authorization: Bearer <accessToken>`. Subscribe to `/user/queue/errors` and conversation topics. Send JSON frames to:

| Destination | Body |
|---|---|
| `/app/chat.send` | Same body as HTTP send message |
| `/app/chat.read` | `{ "conversationId": "uuid", "upToSeq": 10 }` |
| `/app/chat.typing` | `{ "conversationId": "uuid", "isTyping": true }` |

Server broadcasts messages to `/topic/conversations.{conversationId}` and typing events to `/topic/conversations.{conversationId}.typing`. WebSocket errors use `{ "code": "FORBIDDEN|NOT_FOUND|BUSINESS_RULE|VALIDATION_ERROR|INTERNAL_ERROR", "message": "Request failed" }`.

### Flutter STOMP example

```dart
final client = StompClient(
  config: StompConfig.sockJS(
    url: 'https://leo-chat-backend.onrender.com/ws',
    onConnect: (frame) {
      client.subscribe(
        destination: '/topic/conversations.$conversationId',
        callback: (frame) => onMessage(jsonDecode(frame.body!)),
      );
      client.subscribe(
        destination: '/topic/conversations.$conversationId.typing',
        callback: (frame) => onTyping(jsonDecode(frame.body!)),
      );
      client.subscribe(
        destination: '/user/queue/errors',
        callback: (frame) => onApiError(jsonDecode(frame.body!)),
      );
    },
  ),
);
client.activate();
```

For Bearer WebSocket clients, pass the token in STOMP `connectHeaders`. For cookie clients, use the cookie-aware transport. Subscribe before sending messages. Reconnect with backoff and resubscribe after reconnect.

### Message response

```json
{
  "id": "uuid",
  "conversationId": "uuid",
  "senderId": "uuid",
  "senderUsername": "alice",
  "senderDisplayName": "Alice",
  "seq": 12,
  "clientMessageId": "mobile-123",
  "type": "TEXT",
  "content": "Hello",
  "replyToId": null,
  "replyTo": null,
  "reactions": [
    {
      "emoji": "👍",
      "count": 2,
      "users": [
        { "id": "uuid", "username": "alice", "displayName": "Alice" }
      ]
    }
  ],
  "createdAt": "2026-10-17T10:20:30Z",
  "editedAt": null,
  "deletedAt": null,
  "deleted": false
}
```

Deleted messages keep their ID and sequence, set `deleted` to `true`, and set `content` to `null`. A reply preview has `id`, `senderId`, `senderUsername`, `senderDisplayName`, `content`, and `deleted`.

## Flutter data-layer rules

- Use repositories: `AuthRepository`, `UserRepository`, `ConversationRepository`, `MessageRepository`, `MediaRepository`, and `AdminRepository`.
- Keep DTOs separate from widgets. Parse UUIDs as strings or `UuidValue`; parse timestamps with `DateTime.parse`.
- Use `clientMessageId` for retry-safe sends. Do not create duplicate messages when a request times out; retry the same ID.
- Load older messages with `beforeSeq`; sync new messages with `afterSeq`.
- Merge REST history and WebSocket messages by message ID, then order by `seq`.
- Treat WebSocket events as updates: replace the message when reactions, edits, or deletion change.
- URL-encode reaction emoji in `DELETE /messages/{id}/reactions/{emoji}`.

## Media upload flow

1. Call `POST /media/presign` with MIME type and byte size.
2. Upload bytes with `PUT` to the returned `uploadUrl`, using exactly the returned `Content-Type`.
3. Call `POST /media/confirm` with `storageKey`.
4. Save the returned URL only where the API expects a profile or attachment URL. Presigned URLs expire; request a new URL when needed.

The maximum file size is 10 MiB. Unsupported types return `422 BUSINESS_RULE`.

## Environment and deployment

Production requires HTTPS/WSS, configured CORS for the Flutter web origin if using Flutter Web, Supabase storage credentials for media, and a TURN server for WebRTC calls. Render must deploy the latest Git commit. The backend listens on the `PORT` environment variable.

## Response shapes

User search item:

```json
{
  "id": "uuid",
  "username": "alice",
  "displayName": "Alice",
  "lastSeenAt": "2026-10-14T10:20:30Z",
  "status": "ACTIVE",
  "profilePictureUrl": "https://...",
  "bio": "Hello",
  "region": "US",
  "age": 30
}
```

Conversation member:

```json
{
  "conversationId": "uuid",
  "userId": "uuid",
  "role": "OWNER",
  "lastReadSeq": 12,
  "mutedUntil": null,
  "joinedAt": "2026-10-14T10:00:00Z",
  "leftAt": null,
  "pinnedAt": null,
  "archivedAt": null
}
```

Presence item: `{ "id": "uuid", "status": "ACTIVE", "lastSeenAt": "2026-10-14T10:20:30Z" }`.

Admin stats: `{ "totalUsers": 10, "totalConversations": 4, "totalReports": 2 }`.

Admin report objects contain `id`, `reporterId`, `targetType`, `targetId`, `reason`, `details`, `status`, `resolvedBy`, `resolution`, `createdAt`, and `resolvedAt`. Report statuses are `OPEN`, `RESOLVED`, and `DISMISSED`.

Media presign response:

```json
{
  "bucket": "images",
  "storageKey": "users/uuid/file.png",
  "uploadUrl": "https://...",
  "method": "PUT",
  "contentType": "image/png",
  "sizeInBytes": 12345,
  "expiresIn": 900
}
```

Media confirm response uses the same `bucket`, `storageKey`, `contentType`, `sizeInBytes`, and `expiresIn` fields, with `url` instead of `uploadUrl`.

## Error contract

Every HTTP error uses this shape:

```json
{
  "timestamp": "2026-10-14T10:20:30Z",
  "status": 422,
  "code": "BUSINESS_RULE",
  "message": "Image storage is not configured",
  "path": "/api/v1/media/presign",
  "traceId": "uuid",
  "errors": []
}
```

Validation errors include field details:

```json
{
  "timestamp": "...",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/auth/register",
  "traceId": "uuid",
  "errors": [{ "field": "email", "message": "must be a valid email address" }]
}
```

| HTTP status | Code | Frontend action |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | Show field errors; fix JSON, UUID, type, or validation input |
| 401 | `UNAUTHORIZED` | Refresh once; if refresh fails, clear local auth state and show login |
| 403 | `FORBIDDEN` | Show permission/membership error |
| 404 | `NOT_FOUND` | Remove stale resource or show not found |
| 409 | `CONFLICT` | Refresh state; resolve duplicate or concurrent change |
| 422 | `BUSINESS_RULE` | Show `message`; request is valid but cannot be completed |
| 429 | `RATE_LIMITED` | Back off and retry later |
| 500 | `INTERNAL_ERROR` | Show generic error and include `traceId` in support report |

Empty success responses have no JSON body. Do not call `response.json()` for `204` or for action routes documented as empty.

## 4. Admin response details

Admin routes use the same error contract. They require a user whose role is `ROLE_ADMIN`.

`GET /admin/stats/overview` returns:

```json
{ "totalUsers": 42, "totalConversations": 18, "totalReports": 3 }
```

`GET /admin/users`, `GET /admin/conversations`, `GET /admin/reports`, and `GET /admin/audit-logs` return the cursor page shape. User items contain `id`, `username`, `email`, `displayName`, `role`, and `status`. Conversation items contain `id`, `type`, `title`, `status`, `lastSeq`, `lastMessageAt`, and `createdAt`. Report items contain `id`, `reporterId`, `targetType`, `targetId`, `reason`, `details`, `status`, `createdAt`, and optional resolution fields. Audit items contain the actor, action, target, metadata, and `createdAt`.

Admin mutations use these request and response forms:

```http
PATCH /api/v1/admin/users/{id}/status
{ "status": "DISABLED", "reason": "Policy violation" }
```

```http
PATCH /api/v1/admin/conversations/{id}/status
{ "status": "ARCHIVED", "reason": "Moderation complete" }
```

```http
PATCH /api/v1/admin/reports/{id}
{ "resolution": "Confirmed", "action": "DELETE_MESSAGE" }
```

```http
DELETE /api/v1/admin/messages/{id}?reason=Spam%20content
```

Each returns `200` with `{ "message": "..." }`. `POST /admin/users/{id}/force-logout` revokes all stored refresh sessions for the user. `POST /admin/transfer-ownership` accepts `{ "targetUserId": "uuid", "password": "current-admin-password" }` and returns a success message.

Admin status values: user `ACTIVE|DISABLED|BANNED`; conversation `ACTIVE|ARCHIVED|DELETED`; report `OPEN|INVESTIGATING|RESOLVED|DISMISSED`.
