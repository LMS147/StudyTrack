# StudyTrack

A native Android study-planning app: subjects, tasks, a calendar, progress
tracking and an AI study assistant that turns plain-language requests into
calendar-ready tasks.

Android client for the StudyTrack REST API (JWT-authenticated). Authentication
uses Firebase Auth email/password; the app exchanges the Firebase ID token for
an API session and attaches it to every request.

## Tech stack

- **Kotlin**, single-activity Navigation Component app (view-based UI with ViewBinding)
- **Material 3** components (`com.google.android.material:material:1.11.0`)
- **Retrofit 2.11 + OkHttp 4.12** with `kotlinx-serialization` converters
- **Coroutines + Flow** (`StateFlow` in ViewModels, `combine` for screen state)
- **Firebase Auth** (email/password) + `google-services` plugin
- **ViewModel + manual DI** via a small `ServiceLocator` (no DI framework, by design)
- Min SDK 26, target/compile SDK 34, AGP 8.4.2, Kotlin 1.9.24, JDK 17

## Screens

| Screen | What it does |
| --- | --- |
| **Login / Register** | Firebase email/password auth with friendly error mapping |
| **Dashboard** | Time-aware greeting, today's completion ring, AI assistant shortcut card, due-today / overdue / upcoming sections |
| **Subjects** | Subject cards with task counts; create/edit/archive; subject detail with tasks grouped by status |
| **Tasks** | Create/edit with type, priority, subject, due date (+ optional time), reminder; complete from any list |
| **Calendar** | Month grid with locale-aware first-day-of-week, today ring, priority dots; tap a day to see and quick-add tasks |
| **Progress** | Overall completion ring, points / completed / streak stat cards, per-subject progress bars |
| **AI Assistant** | Chat UI: text bubbles, structured suggestion cards with Accept / Edit / Reject, typing indicator, conversation history |
| **Profile** | Account card, notification & AI preferences (local), About info, logout |

## Building

```bash
./gradlew assembleDebug
```

CI (GitHub Actions, `.github/workflows/android-ci.yml`) runs the same build on
every push.

### Firebase setup

`app/google-services.json` is a **placeholder**. To run against your own
Firebase project, replace it with the real file from the Firebase console
(Authentication → Sign-in method → Email/Password must be enabled).

### API endpoint

`RetrofitClient.BASE_URL` (in
`app/src/main/java/com/studytrack/app/data/remote/RetrofitClient.kt`) points to
`https://api.studytrack.example.com/` and must be changed to your deployed
StudyTrack backend. The full REST contract the app expects is documented in
[docs/API_CONTRACT.md](docs/API_CONTRACT.md).

## Project layout

```
app/src/main/java/com/studytrack/app/
├── ai/                  # AI assistant chat (adapter, view model, fragment)
├── auth/                # Login / register
├── calendar/            # Month grid calendar
├── dashboard/           # Home dashboard
├── data/
│   ├── model/           # @Serializable wire models (enums with lenient parsing)
│   ├── remote/          # Retrofit ApiService, auth interceptor/authenticator, JSON config
│   └── repository/      # Repositories + local SettingsRepository (SharedPreferences)
├── progress/            # Progress screen
├── profile/             # Profile & settings screen
├── subjects/            # Subject list / detail / editor
├── tasks/               # Task list, editor, details (+ shared TaskRowBinder)
└── util/                # ApiResult, DateTimeUtils, TokenManager, extensions
```

Architecture notes and the reasoning behind key decisions live in
[docs/ARCHITECTURE_DECISIONS.md](docs/ARCHITECTURE_DECISIONS.md).
