# LeoChat Frontend API Guide

Base URL: `/api/v1`

All JSON requests use `Content-Type: application/json`. IDs are UUID strings. Timestamps are ISO-8601 UTC strings. Protected HTTP routes accept the browser cookies created by login. Frontend requests must use `credentials: "include"`.

## Authentication

### Browser setup

1. `GET /auth/csrf` before the first browser write. Store the returned `token` in memory.
2. Send that token as `X-XSRF-TOKEN` on browser `POST`, `PATCH`, `PUT`, and `DELETE` requests.
3. `POST /auth/login` sets HttpOnly `ACCESS_TOKEN` and `REFRESH_TOKEN` cookies. The JSON token pair is also returned for non-browser clients. Registration creates the account but does not log the browser in; call login after register.
4. On a `401`, call `POST /auth/refresh` with cookies, then retry once. Do not put the refresh token in an Authorization header.
5. `POST /auth/logout` clears this browser's cookies. Fully stateless JWTs copied elsewhere remain valid until expiry. A `403` with code `CSRF_INVALID` means refresh `/auth/csrf`, update the `X-XSRF-TOKEN` header, and retry.

Cookie requests:

```js
fetch(`${API}/auth/login`, {
  method: "POST",
  credentials: "include",
  headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrfToken },
  body: JSON.stringify({ username, password })
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
| POST | `/auth/logout-all` | User | 422 | none; unsupported for stateless tokens |
| POST | `/auth/change-password` | User | 200 | `currentPassword`, `newPassword` |
| POST | `/auth/forgot-password` | Public | 202 | `{ "email": "..." }` |
| POST | `/auth/reset-password` | Public | 200 | `token`, `newPassword` |
| POST | `/auth/verify-email` | Public | 200 | `{ "token": "..." }` |
| POST | `/auth/resend-verification` | Public | 202 | `{ "email": "..." }` |

Registration validation: username 3–20 characters using letters, numbers, `.`, `_`, `-`; password 8–40 characters with upper, lower, number, and special character. Password changes and resets use 8–40 characters.

## Users

All user routes require authentication.

| Method | Path | Success | Request/query |
|---|---|---:|---|
| GET | `/users/me` | 200 | none |
| GET | `/users/{id}` | 200 | UUID path parameter |
| PATCH | `/users/me/profile` | 200 | profile fields below |
| DELETE | `/users/me` | 200 | Deactivates own account; admin cannot use this |
| GET | `/users/search?q=alice&page=0&size=20` | 200 | `q` required; page ≥ 0; size 1–100 |
| GET | `/users/presence?ids={uuid}&ids={uuid}` | 200 | one or more UUIDs |
| GET | `/users/me/sessions` | 200 | legacy compatibility; stateless session list is empty |
| DELETE | `/users/me/sessions/{sessionId}` | 422 | server-side session logout unsupported |

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

## Conversations

All conversation routes require authentication and active membership where an ID is used.

| Method | Path | Success | Request/query |
|---|---|---:|---|
| GET | `/conversations?cursor=0&limit=50` | 200 | cursor page; limit 1–100 |
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

Message types: `TEXT`, `IMAGE`, `FILE`; `SYSTEM` is server-only. `content` is limited to 4000 characters and `clientMessageId` to 64. Message response includes `id`, `conversationId`, `senderId`, `seq`, `clientMessageId`, `type`, `content`, `replyToId`, `createdAt`, `editedAt`, and `deletedAt`.

## Media

All media routes require authentication. Storage must be enabled with a private Supabase bucket named `images`.

| Method | Path | Success | Request |
|---|---|---:|---|
| POST | `/media/presign` | 200 | `{ "mimeType": "image/png", "sizeInBytes": 12345 }` |
| POST | `/media/confirm` | 200 | `{ "storageKey": "users/{userId}/{id}.png" }` |

Supported MIME types: `image/jpeg`, `image/png`, `image/webp`, `image/gif`, `image/avif`. The configured server limit is 10 MiB. Presign returns `uploadUrl`, `method` (`PUT`), `contentType`, `sizeInBytes`, `bucket`, `storageKey`, and `expiresIn`. Upload bytes directly to `uploadUrl`, preserving the returned content type and size. Confirm returns a private short-lived download `url`.

## Administration

All admin routes require the single active `ROLE_ADMIN` account.

| Method | Path | Success | Request/query |
|---|---|---:|---|
| GET | `/admin/stats/overview` | 200 | none |
| GET | `/admin/users?q=alice&status=ACTIVE&cursor=0&limit=50` | 200 | optional filters; limit 1–100 |
| GET | `/admin/users/{id}` | 200 | none |
| PATCH | `/admin/users/{id}/status` | 200 | `{ "status": "ACTIVE|DISABLED|BANNED", "reason": "..." }` |
| POST | `/admin/users/{id}/force-logout` | 422 | unsupported for stateless JWTs |
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
