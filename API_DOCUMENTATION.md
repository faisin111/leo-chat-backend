# Leo Chat Backend - API Documentation

> **Implementation status (2026-10-14):** Authentication uses stateless JWT cookies for browsers, Bearer compatibility for API clients, fixed refresh deadlines and no server token storage. Server-wide/device revocation is unsupported and returns 422. See [authentication contract](docs/AUTHENTICATION.md). Reactions persist; image media uses private Supabase Storage presigned URLs, presence is stored status/last-seen, and conversation cursors remain page numbers. Message history and authorized STOMP messaging have regression tests.

## 1. Project Overview
Leo Chat is a comprehensive real-time chat application backend built with Spring Boot. It provides a robust set of features suitable for building modern messaging clients (web, desktop, and mobile). 

### Key Capabilities:
*   **Authentication & Security:** Stateless HttpOnly JWT cookies for browser sessions, automatic Bearer-token API clients without CSRF bootstrap, JSON token responses, email verification, and password recovery.
*   **User Management:** Profiles, online presence tracking, stateless account authentication, and user search directory.
*   **Messaging System:** Support for both 1-on-1 Direct Messages (DMs) and Group Chats. Features include message history, pagination, persisted read positions, editing/deleting messages, and emoji reactions.
*   **Media Handling:** Cloud-ready media upload flow using presigned URLs for sending images/files securely.
*   **Administrative Control:** A dedicated system admin panel to moderate users, resolve reports, oversee conversations, and view system audit logs.

---

## 2. Authentication Flow
Login/refresh return JSON tokens and set HttpOnly access and refresh JWT cookies. API clients should keep the access token in memory and attach `Authorization: Bearer <access-token>` automatically; these requests do not need CSRF. Cookie-only browser writes still use `GET /api/v1/auth/csrf` and `X-XSRF-TOKEN`. Logout clears cookies; copied JWTs stay valid until expiry. See [complete auth flow](docs/AUTHENTICATION.md).

## 3. API Endpoints

### Authentication (`/api/v1/auth`)
*   `POST /login` - Authenticate and receive JSON access/refresh tokens with expiry metadata.
*   `POST /register` - Register a new account.
*   `POST /refresh` - Exchange a JSON refresh JWT while preserving its original expiry.
*   `POST /logout` - Clear browser authentication cookies (204); copied JWTs remain valid.
*   `POST /logout-all` - Return 422: server-wide token revocation is unsupported.
*   `POST /change-password` - Update password (requires current password).
*   `POST /forgot-password` - Request a password reset link/token.
*   `POST /reset-password` - Reset password using the provided token.
*   `POST /verify-email` - Confirm email using the verification token.
*   `POST /resend-verification` - Resend the email verification token.

### User & Profile Management (`/api/v1/users`)
*   `GET /me` - Get the current authenticated user's profile.
*   `PATCH /me/profile` - Update profile (bio, phone number, age, region, avatar).
*   `DELETE /me` - Deactivate own account.
*   `GET /me/sessions` - Return an empty list; no device sessions are stored.
*   `DELETE /me/sessions/{sessionId}` - Return 422: remote session logout is unsupported.
*   `GET /{id}` - Get the public profile of a specific user.
*   `GET /search?q={query}&page=0&size=20` - Search directory for users.
*   `GET /presence?ids={id1},{id2}` - Get online/offline status for a list of users.

### Conversations (`/api/v1/conversations`)
*   `GET /` - List all active conversations for the current user.
*   `POST /direct` - Create or retrieve a 1-on-1 DM with another user.
*   `POST /group` - Create a new multi-user group chat.
*   `GET /{id}` - Get details for a specific conversation.
*   `PATCH /{id}` - Update group chat details (name, avatar, etc.).
*   `GET /unread-count` - Get the total unread message count across all chats.

#### Conversation Preferences
*   `PUT /{id}/mute` / `DELETE /{id}/mute` - Mute/unmute notifications.
*   `PUT /{id}/pin` / `DELETE /{id}/pin` - Pin/unpin chat to the top of the list.
*   `PUT /{id}/archive` / `DELETE /{id}/archive` - Hide/unhide chat from main list.

#### Group Members
*   `GET /{id}/members` - List all participants in the chat.
*   `POST /{id}/members` - Add new users to the group.
*   `DELETE /{id}/members/{userId}` - Kick a user or leave the group.
*   `PATCH /{id}/members/{userId}/role` - Change a user's role (e.g., promote to Admin).

### Messages (`/api/v1/messages` & `/api/v1/conversations/{id}/messages`)
*   `GET /api/v1/conversations/{id}/messages` - Fetch paginated message history.
*   `POST /api/v1/conversations/{id}/messages` - Send a new message.
*   `POST /api/v1/conversations/{id}/read` - Mark messages as read up to a specific point.
*   `GET /api/v1/messages/{id}` - Get a single message.
*   `PATCH /api/v1/messages/{id}` - Edit a sent message.
*   `DELETE /api/v1/messages/{id}` - Delete a sent message.
*   `POST /api/v1/messages/{id}/reactions` - Add an emoji reaction.
*   `DELETE /api/v1/messages/{id}/reactions/{emoji}` - Remove an emoji reaction.

### Media & File Uploads (`/api/v1/media`)
*   `POST /presign` - Request a short-lived private Supabase S3 PUT URL for a JPEG, PNG, WebP, GIF, or AVIF image (maximum 10 MiB).
*   `POST /confirm` - Verify the user-owned object exists and return a short-lived private GET URL.

### System Administration (`/api/v1/admin`)
*(Requires `ROLE_ADMIN` authority)*
*   `GET /stats/overview` - Get system metrics (user count, active chats, reports).
*   `GET /users` - List all users in the system.
*   `GET /users/{id}` - Get detailed system info on a user.
*   `PATCH /users/{id}/status` - Ban, disable, or activate a user account.
*   `POST /users/{id}/force-logout` - Return 422: stateless tokens cannot be force-revoked.
*   `DELETE /users/{id}` - Soft-delete and anonymize a user.
*   `GET /conversations` - List all system conversations.
*   `PATCH /conversations/{id}/status` - Lock or unlock a group chat.
*   `GET /reports` - View the moderation queue.
*   `GET /reports/{id}` - View specific report context.
*   `PATCH /reports/{id}` - Resolve a report and take action.
*   `DELETE /messages/{id}` - Administratively wipe a message.
*   `GET /audit-logs` - View security and action logs.
*   `POST /transfer-ownership` - Transfer the System Admin role to someone else.

---

## 4. WebSocket Connection and Message Flow

Connect to `/ws` (native WebSocket or SockJS). Send a STOMP `CONNECT` frame with `Authorization: Bearer <access-token>`. Missing, invalid and refresh tokens are rejected. HTTP and WebSocket origins use `APP_CORS_ALLOWED_ORIGINS`, a comma-separated exact-origin allowlist.

Subscribe to `/user/queue/acks` and `/user/queue/errors`. Conversation members can subscribe to `/topic/conversations.{conversationId}` and `/topic/conversations.{conversationId}.typing`. Arbitrary broker destinations and another user's queues are rejected.

For calls, subscribe to `/user/queue/calls` and send WebRTC signaling envelopes to `/app/call.signal` (`OFFER`, `ANSWER`, `ICE_CANDIDATE`, `REJECT`, `BUSY`, or `END`). The backend validates both conversation members and relays signaling only; audio/video uses a client-side `RTCPeerConnection`.

Send to `/app/chat.send`:

```json
{"conversationId":"<uuid>","clientMessageId":"msg-1","type":"TEXT","content":"Hello"}
```

The server persists the message, commits, sends its `MessageResponse` to `/user/queue/acks`, then broadcasts it. A retry with the same conversation, sender and `clientMessageId` returns the original message without a second broadcast. Text content is nonblank, trimmed and limited to 4,000 characters; retry keys are limited to 64 characters. `SYSTEM` messages are server-only.

Send typing to `/app/chat.typing` with `{conversationId,isTyping}`. Send read state to `/app/chat.read` with `{conversationId,upToSeq}`. Read state is persisted and only moves forward, up to the conversation's latest sequence. Read-receipt and reaction event broadcasts are not yet implemented. Handler errors return a code to `/user/queue/errors`; transport authorization failures close the STOMP connection with an error frame.

REST history uses `beforeSeq` for descending older messages and `afterSeq` for ascending reconnect sync. Use one cursor at a time and a limit from 1 to 100. Edits and soft deletes broadcast updated message responses after commit. Deleted responses hide content. Reactions are stored and can be removed by their creator.
