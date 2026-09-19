# StudyTrack REST API Contract

The Android client's expectations of the StudyTrack backend. All request/response
bodies are JSON. Source of truth for the client side:
`app/src/main/java/com/studytrack/app/data/remote/ApiService.kt` and
`app/src/main/java/com/studytrack/app/data/model/`.

## Conventions

- **Base URL**: configured in `RetrofitClient.BASE_URL` (trailing slash; all
  paths below are relative to it).
- **Auth**: all endpoints except login exchange require a Firebase ID token in
  the `Authorization: Bearer <token>` header. A `401` triggers a background
  token refresh (see Architecture → token handling).
- **Dates**: ISO-8601 strings, e.g. `2026-09-24T17:00:00`. A date-only value
  (`2026-09-24`) is valid and rendered without a time.
- **IDs**: opaque strings.
- **Enums** are wire-exact strings:
  - Task type: `"Assignment" | "Test" | "Exam" | "Project" | "Presentation" | "Study"`
  - Priority: `"Low" | "Medium" | "High"`
  - The client parses them leniently (case-insensitive, falls back to
    `Study` / `Medium`) so AI free-form output never crashes the app.
- **Unknown fields** in responses are ignored; explicit nulls are omitted on
  the wire (`explicitNulls = false`, `encodeDefaults = true`).

## Auth

| Method & path | Purpose |
| --- | --- |
| `POST /api/auth/login` | Exchange Firebase ID token for an API session (returns the JWT the app uses) |
| `POST /api/auth/logout` | Invalidate the session (best-effort) |
| `GET /api/auth/me` | Current user profile (`displayName`, `email`) |

> Firebase email/password sign-in itself happens directly between the app and
> Firebase Auth; the API only ever sees tokens, never passwords.

## Subjects

| Method & path | Purpose |
| --- | --- |
| `GET /api/subjects` | List subjects (with `taskCount` / `completedCount`) |
| `POST /api/subjects` | Create — body: `SubjectPayload` |
| `PUT /api/subjects/{subjectId}` | Update — body: `SubjectPayload` |
| `DELETE /api/subjects/{subjectId}` | Archive a subject (tasks keep their history) |

`Subject`: `{ subjectId, subjectName, color?, description?, taskCount, completedCount }`
`SubjectPayload`: `{ subjectName, color?, description? }`

## Tasks

| Method & path | Purpose |
| --- | --- |
| `GET /api/tasks?subjectId=&completed=` | List tasks (both filters optional) |
| `GET /api/tasks/{taskId}` | Task details |
| `POST /api/tasks` | Create — body: `TaskPayload` (returns the created task) |
| `PUT /api/tasks/{taskId}` | Update — body: `TaskPayload` |
| `DELETE /api/tasks/{taskId}` | Delete |
| `PUT /api/tasks/{taskId}/complete` | Body: `{ "completed": bool }` |

`Task`: `{ taskId, title, subjectId?, description?, taskType, priority, dueDate?, reminderDate?, completed, points, createdAt, completedAt? }`
`TaskPayload`: same minus server-set fields.

The client treats **due date as required** (UI-level) and defaults a date-only
due date to end-of-day 23:59; reminders default to 09:00 when the user enables
them without a time. Subtasks created from an AI suggestion inherit the parent
task's due date when the AI didn't give them one.

## Calendar

| Method & path | Purpose |
| --- | --- |
| `GET /api/calendar?month=yyyy-MM&subjectId=` | Aggregated month view: per-date task summaries |

`CalendarMonth`: `{ month, days: [{ date, tasks: [Task] }] }`

## Progress

| Method & path | Purpose |
| --- | --- |
| `GET /api/progress` | `{ totalPoints, completedTasks, totalTasks, currentStreak, weeklyGoal, subjectProgress: [{ subjectId, subjectName, completedTasks, totalTasks }] }` |

The client shows points/streak from this endpoint but computes the overall
percentage and the per-subject bars client-side from the cached task list so
the screen stays consistent with the Tasks tab.

## AI Assistant

`POST /api/ai/task-assistance`

Request:

```json
{
  "message": "Help me plan for my calculus test",
  "conversationHistory": [ { "role": "user", "content": "..." },
                           { "role": "assistant", "content": "..." } ],
  "today": "2026-09-19",
  "timezone": "Africa/Johannesburg",
  "taskContext": { "taskId": "...", "title": "...", "dueDate": "..." }
}
```

Response:

```json
{
  "replyText": "Here's a plan …",
  "suggestions": [ {
    "suggestionId": "...",
    "kind": "breakdown",
    "title": "Calculus test prep",
    "description": "...",
    "subjectId": "...",
    "subjectName": "Mathematics",
    "taskType": "Study",
    "priority": "High",
    "dueDate": "2026-09-24T17:00:00",
    "reminderDate": null,
    "subtasks": [ { "title": "Review chain rule", "dueDate": null } ]
  } ]
}
```

**Date resolution is authoritative server-side**: the client sends its device
`today` + IANA `timezone` with every request and expects the backend to resolve
relative dates ("next Friday"). When the backend cannot confidently resolve a
date it returns `dueDate: null` — the client then shows an editable date field
on the suggestion card and **refuses to accept the task until the user picks a
date**. The client never guesses dates.

`conversationHistory` contains only text turns (user/assistant), never
suggestion cards or system confirmations.

## Error shape

Errors are expected as `{ "error": "message" }` (or plain text); the client
surfaces the message to the user and keeps whatever cached data it has.
