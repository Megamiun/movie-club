# Auth Flows

## Invite → Register → Login

The only way to create an account is through an invite from an existing member.

### 1. Invite

A logged-in member invites someone by email. The server creates a bare member record and returns a one-time token.

```
POST /auth/invite
Authorization: Bearer <jwt>
Content-Type: application/json

{ "email": "carol@example.com" }
```

```
201 Created

{ "inviteToken": "550e8400-e29b-41d4-a716-446655440000" }
```

The inviting member sends Carol a link containing the token, e.g.:
`https://app.example.com/register?token=550e8400-e29b-41d4-a716-446655440000`

**Errors**
| Status | Reason |
|--------|--------|
| 401 | Missing or invalid JWT |
| 409 | Email already exists (invited or registered) |

---

### 2. Register

Carol opens the link and submits her name and password. The token is consumed — it cannot be reused.

```
POST /auth/register
Content-Type: application/json

{
  "inviteToken": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Carol",
  "password": "hunter2"
}
```

```
201 Created

{
  "token": "<jwt>",
  "member": {
    "id": "a1b2c3d4-...",
    "name": "Carol",
    "email": "carol@example.com"
  }
}
```

**Errors**
| Status | Reason |
|--------|--------|
| 400 | `inviteToken` is not a valid UUID |
| 403 | Token not found or already used |

---

### 3. Login

On subsequent visits Carol logs in with email and password.

```
POST /auth/login
Content-Type: application/json

{ "email": "carol@example.com", "password": "hunter2" }
```

```
200 OK

{
  "token": "<jwt>",
  "member": {
    "id": "a1b2c3d4-...",
    "name": "Carol",
    "email": "carol@example.com"
  }
}
```

**Errors**
| Status | Reason |
|--------|--------|
| 401 | Email not found, account not yet registered, or wrong password |

---

## Using the JWT

All authenticated endpoints require the token in the `Authorization` header:

```
Authorization: Bearer <jwt>
```

Tokens are valid for 7 days. There is no refresh endpoint — the user must log in again after expiry.
