# Leo Chat Backend - API Documentation

## 1. Project Overview
Leo Chat is a comprehensive real-time chat application backend built with Spring Boot. It provides a robust set of features suitable for building modern messaging clients (web, desktop, and mobile). 

### Key Capabilities:
*   **Authentication & Security:** Secure JWT-based authentication using HTTP-only cookies, token rotation (refresh tokens), email verification, and password recovery.
*   **User Management:** Profiles, online presence tracking, session management across multiple devices, and user search directory.
*   **Messaging System:** Support for both 1-on-1 Direct Messages (DMs) and Group Chats. Features include message history, pagination, read receipts, editing/deleting messages, and emoji reactions.
*   **Media Handling:** Cloud-ready media upload flow using presigned URLs for sending images/files securely.
*   **Administrative Control:** A dedicated system admin panel to moderate users, resolve reports, oversee conversations, and view system audit logs.

---

## 2. Authentication Flow
The API uses **HTTP-Only Cookies** for secure authentication. 
*   Upon calling `/api/v1/auth/login`, the server attaches two cookies to the response: `leo_chat_jwt` (short-lived access token) and `leo_chat_jwt_refresh` (long-lived refresh token).
*   The frontend does **not** need to manually store or attach tokens in the `Authorization` header. The browser/HTTP client will automatically send the cookies with every request to `/api/v1/*`.
*   When the access token expires (401 error), the frontend should call `/api/v1/auth/refresh` to rotate the tokens automatically.

---

## 3. API Endpoints

### Authentication (`/api/v1/auth`)
*   `POST /login` - Authenticate user, receive user data, and set JWT cookies.
*   `POST /register` - Register a new account.
*   `POST /refresh` - Generate a new access token using the refresh cookie.
*   `POST /logout` - Log out the current session and clear cookies.
*   `POST /logout-all` - Revoke all active sessions for the user across all devices.
*   `POST /change-password` - Update password (requires current password).
*   `POST /forgot-password` - Request a password reset link/token.
*   `POST /reset-password` - Reset password using the provided token.
*   `POST /verify-email` - Confirm email using the verification token.
*   `POST /resend-verification` - Resend the email verification token.

### User & Profile Management (`/api/v1/users`)
*   `GET /me` - Get the current authenticated user's profile.
*   `PATCH /me/profile` - Update profile (bio, phone number, age, region, avatar).
*   `DELETE /me` - Deactivate own account.
*   `GET /me/sessions` - List all active login sessions/devices for the user.
*   `DELETE /me/sessions/{sessionId}` - Log out a specific remote session.
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
*   `POST /presign` - Request a secure upload URL from the server.
*   `POST /confirm` - Confirm successful upload and attach media to the chat.

### System Administration (`/api/v1/admin`)
*(Requires `ROLE_ADMIN` authority)*
*   `GET /stats/overview` - Get system metrics (user count, active chats, reports).
*   `GET /users` - List all users in the system.
*   `GET /users/{id}` - Get detailed system info on a user.
*   `PATCH /users/{id}/status` - Ban, disable, or activate a user account.
*   `POST /users/{id}/force-logout` - Forcefully terminate all sessions for a user.
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

## 4. WebSocket Implementation Note
While the HTTP endpoints above are used for history, configuration, and fallback, this application is designed for Real-Time chat. 

When building your frontend (Web or App), you should establish a WebSocket connection (typically at `ws://<domain>/ws` or similar, depending on your `ChatWsController` setup). 
*   Send messages via HTTP `POST` and listen for incoming messages, typing indicators, and read receipts over the WebSocket connection to provide a seamless, live chat experience.
