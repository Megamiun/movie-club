# Movie Club Architecture & System Flows

## 1. Auth Flows

### Invite → Register → Login

Accounts can only be created through an invite from an existing member.

#### Step 1: Invite
A logged-in member invites someone by email. The server creates a bare member record and returns a one-time token.

```http
POST /auth/invite
Authorization: Bearer <jwt>
Content-Type: application/json

{ "email": "carol@example.com" }
```
*Response (201 Created):*
```json
{ "inviteToken": "550e8400-e29b-41d4-a716-446655440000" }
```

#### Step 2: Register
The recipient opens the link and submits name and password to consume the token.

```http
POST /auth/register
Content-Type: application/json

{
  "inviteToken": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Carol",
  "password": "hunter2"
}
```
*Response (201 Created):*
```json
{
  "token": "<jwt>",
  "member": {
    "id": "a1b2c3d4-...",
    "name": "Carol",
    "email": "carol@example.com"
  }
}
```

#### Step 3: Login
Subsequent logins require email and password.

```http
POST /auth/login
Content-Type: application/json

{ "email": "carol@example.com", "password": "hunter2" }
```

---

## 2. Club & Member Administration

### Club Creation & Member Management
1. **Create Club**: `POST /clubs` (Creator becomes `ADMIN`, default rating scales are initialized).
2. **Add Member**: `POST /clubs/{clubId}/members` (Requires `memberId` and `role` - `MEMBER` or `ADMIN`).
3. **Change Role**: `PATCH /clubs/{clubId}/members/{memberId}` with `{ "role": "ADMIN" }`.
4. **Member Color**: `PATCH /clubs/{clubId}/members/{memberId}/color` with `{ "color": "#E1BEE7" }` (used for badge avatars and UI indicators).
5. **Rotation Order**: `PUT /clubs/{clubId}/rotation` with `{ "memberIds": ["<id1>", "<id2>"] }` (determines meeting assignment order).

### Language Preferences & Title Resolution
Clubs maintain preferred and ignored language preferences.
```http
PATCH /clubs/{clubId}/language-preferences
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "preferredLanguages": ["pt-BR", "en"],
  "ignoredLanguages": ["fr"]
}
```
*Language codes can be bare ISO 639-1 (`pt`, `en`) or region-qualified (`pt-BR`, `pt-PT`).*

#### Title Resolution Logic (`resolveTitle`):
1. **Custom Title**: If `displayTitlePreference` is `CUSTOM` and `customTitle` exists, use it.
2. **Selected Preference**: If `displayTitlePreference` is `LANGUAGE` and a valid language code is specified, find matching translation.
3. **Preferred Languages**: Iterate over `preferredLanguages` in order:
   - Skip if covered by `ignoredLanguages`.
   - Match against title `translations` (matching language code & region if specified).
4. **Original Title**: Return `originalTitle` unless its `originalLanguage` is in `ignoredLanguages`.
5. **Fallback**: Pick first translation not in `ignoredLanguages`, or fall back to `originalTitle`.

---

## 3. Rating Scale & Option Customization

Clubs have customizable rating scales (e.g. Quality and Sentiment).

1. **Get Rating Scales**: `GET /clubs/{clubId}/rating-scales`
2. **Update Option**: `PATCH /clubs/{clubId}/rating-options/{optionId}` with `{ "label": "Masterpiece", "color": "#81C784" }`
3. **Add Option**: `POST /clubs/{clubId}/rating-scales/{scaleId}/options` with `{ "label": "Okay", "color": "#FFF176" }`
4. **Reorder Options**: `PUT /clubs/{clubId}/rating-scales/{scaleId}/order` with `{ "optionIds": ["id1", "id2"] }`
5. **Delete Option (with Re-assignment)**:
   ```http
   DELETE /clubs/{clubId}/rating-options/{optionId}?reassignToOptionId={replacementOptionId}
   Authorization: Bearer <jwt>
   ```

---

## 4. Meetings & Pick Management

Meetings anchor movies and series episode picks.

1. **Create Meeting**: `POST /clubs/{clubId}/meetings` with `{ "date": "2026-04-15", "assignedMemberId": "<id>" }`.
2. **Pick Movie**: `POST /meetings/{meetingId}/movies` with `{ "imdbUrlOrId": "tt4857264" }` or `{ "tmdbId": 324857 }`.
3. **Pick Episode**: `POST /seasons/{seasonId}/episodes` with `{ "number": 1, "title": "Pilot", "meetingId": "<id>" }` or assign existing via `POST /episodes/{episodeId}/meetings/{meetingId}`.
4. **Move Pick**:
   - Move Movie: `POST /movies/{movieId}/move` with `{ "meetingId": "<targetId>" }`
   - Unassign Episode: `DELETE /episodes/{episodeId}/meetings/{meetingId}`
5. **Swap Meetings**: `POST /meetings/{meetingId}/swap/{otherMeetingId}` (swaps assigned members).
6. **Merge Meetings**: `POST /meetings/{meetingId}/merge/{fromMeetingId}` (transfers picks and deletes empty meeting).

---

## 5. Metadata & Bulk Imports

1. **Refresh Metadata**:
   - `POST /movies/{movieId}/refresh-metadata`
   - `POST /series/{seriesId}/refresh-metadata`
   - `POST /episodes/{episodeId}/refresh-metadata`
2. **Bulk TV Season Import**:
   - `POST /series/{seriesId}/import-seasons` (fetches all seasons and episode metadata from TMDB in bulk).
3. **CSV Imports**:
   - `POST /clubs/{clubId}/import` (`multipart/form-data`) supporting `movies`, `series`, and `reserve` (watchlist) CSV files.

---

## 6. Watchlist Backlog

Each club maintains a shared backlog/watchlist for suggestions.

1. **Add Entry**: `POST /clubs/{clubId}/watchlist` with `{ "title": "Dune: Part Two" }`
2. **List Entries**: `GET /clubs/{clubId}/watchlist`
3. **Update Entry**: `PATCH /watchlist/{entryId}` with `{ "notes": "Must watch in IMAX" }`
4. **Delete Entry**: `DELETE /watchlist/{entryId}`

---

## 7. Site Admin Operations

System-wide administrative endpoints (accessible to site admins):

1. **List Users**: `GET /admin/users`
2. **List Media Items**: `GET /admin/media-items`
