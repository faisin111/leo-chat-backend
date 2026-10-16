# Admin API

Base URL: `/api/v1/admin`

Every endpoint requires an authenticated admin:

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Bearer requests do not need `/api/v1/auth/csrf`. Cookie-only browser requests need the CSRF token header for `PATCH`, `POST`, and `DELETE`.

The bootstrap admin created in the configured database is:

```text
username: admin
email: admin@leo-chat.app
```

The temporary password was provided in the provisioning response. Change it immediately with `POST /api/v1/auth/change-password`. The account is marked `mustChangePassword`.

## Common responses

Success message:

```json
{ "message": "User status updated successfully." }
```

Cursor page:

```json
{ "items": [], "hasMore": false, "nextCursor": null }
```

Errors:

```json
{
  "timestamp": "2026-10-16T12:00:00Z",
  "status": 403,
  "error": "FORBIDDEN",
  "message": "Access denied",
  "path": "/api/v1/admin/users"
}
```

Status rules:

| Status | Meaning |
|---:|---|
| 200 | Successful read or update |
| 204 | Successful empty response, when applicable |
| 400 | Invalid query, JSON, validation, cursor, or limit |
| 401 | Missing, expired, or invalid access token |
| 403 | Authenticated user is not admin, or CSRF is missing for cookie mode |
| 404 | User, conversation, report, or message does not exist |
| 409 | Database uniqueness or state conflict |
| 422 | Business rule rejected; stateless force logout is unsupported |
| 500 | Unexpected server error |

## Stats

### `GET /stats/overview`

Response `200`:

```json
{
  "totalUsers": 42,
  "totalConversations": 18,
  "totalReports": 3
}
```

## Users

### `GET /users?q=&status=&cursor=0&limit=50`

Lists users. `q` searches username and display name. `status` accepts `ACTIVE`, `DISABLED`, or `BANNED`. Cursor is a zero-based page number; limit is 1–100.

Response `200`:

```json
{
  "items": [{
    "id": "uuid",
    "username": "alice",
    "email": "alice@example.com",
    "displayName": "Alice",
    "role": "ROLE_USER",
    "status": "ACTIVE"
  }],
  "hasMore": false,
  "nextCursor": null
}
```

### `GET /users/{id}`

Response `200`:

```json
{
  "id": "uuid",
  "username": "alice",
  "email": "alice@example.com",
  "displayName": "Alice",
  "role": "ROLE_USER",
  "status": "ACTIVE"
}
```

### `PATCH /users/{id}/status`

Request:

```json
{ "status": "DISABLED", "reason": "Policy violation" }
```

`status`: `ACTIVE`, `DISABLED`, or `BANNED`. Reason max 500 characters.

Response `200`:

```json
{ "message": "User status updated successfully." }
```

Admin target returns `403`.

### `POST /users/{id}/force-logout`

Request: no body.

Response `422`:

```json
{
  "status": 422,
  "error": "BUSINESS_RULE",
  "message": "Server-side logout is unsupported for fully stateless tokens"
}
```

### `DELETE /users/{id}`

Request: no body.

Response `200`:

```json
{ "message": "User anonymized and softly deleted successfully." }
```

Admin target returns `403`.

## Conversations

### `GET /conversations?q=&status=&cursor=0`

Lists conversation metadata. `q` searches title and direct key. `status` accepts `ACTIVE`, `ARCHIVED`, or `DELETED`.

Response `200`:

```json
{
  "items": [{
    "id": "uuid",
    "type": "DIRECT",
    "title": null,
    "status": "ACTIVE",
    "lastSeq": 12,
    "lastMessageAt": "2026-10-16T12:00:00Z",
    "createdAt": "2026-10-15T12:00:00Z"
  }],
  "hasMore": false,
  "nextCursor": null
}
```

### `PATCH /conversations/{id}/status`

Request:

```json
{ "status": "ARCHIVED", "reason": "Moderation complete" }
```

Response `200`:

```json
{ "message": "Conversation status updated successfully." }
```

## Reports

### `GET /reports?status=&cursor=0`

`status` accepts `OPEN`, `INVESTIGATING`, `RESOLVED`, or `DISMISSED`.

Response `200`:

```json
{
  "items": [{
    "id": "uuid",
    "reporterId": "uuid",
    "targetType": "MESSAGE",
    "targetId": "uuid",
    "reason": "SPAM",
    "details": "Repeated links",
    "status": "OPEN",
    "createdAt": "2026-10-16T12:00:00Z"
  }],
  "hasMore": false,
  "nextCursor": null
}
```

### `GET /reports/{id}`

Response `200`: report object shown above, including `resolvedBy`, `resolution`, and `resolvedAt` when resolved.

### `PATCH /reports/{id}`

Request:

```json
{ "resolution": "Confirmed and removed", "action": "DELETE_MESSAGE" }
```

`resolution` is required and max 2,000 characters. `action` is optional and max 100 characters.

Response `200`:

```json
{ "message": "Report resolved successfully." }
```

### `DELETE /messages/{id}?reason=Spam%20content`

Request body: none. `reason` query parameter is required, 1–500 characters.

Response `200`:

```json
{ "message": "Message deleted successfully." }
```

## Audit logs

### `GET /audit-logs?actor={uuid}&action=USER_STATUS_CHANGED&from=&to=&cursor=0`

Lists audit records. `actor`, `action`, `from`, and `to` are accepted filters.

Response `200`:

```json
{
  "items": [{
    "id": "uuid",
    "actorId": "uuid",
    "action": "USER_STATUS_CHANGED",
    "targetType": "USER",
    "targetId": "uuid",
    "details": "{\"status\":\"BANNED\"}",
    "ipAddress": "203.0.113.10",
    "createdAt": "2026-10-16T12:00:00Z"
  }],
  "hasMore": false,
  "nextCursor": null
}
```

## Ownership

### `POST /transfer-ownership`

Transfers the only admin role. Target must be a different active non-admin user.

Request:

```json
{ "targetUserId": "uuid", "password": "current-admin-password" }
```

Response `200`:

```json
{ "message": "Ownership transferred successfully." }
```

Invalid admin password returns `403`. The old admin becomes `ROLE_USER` and the target becomes `ROLE_ADMIN`.
