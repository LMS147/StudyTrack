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
| **Sign In** | Firebase email/password auth, password reset by email, Google sign-in, friendly error mapping |
| **Dashboard** | Time-aware greeting, today's completion ring, AI assistant shortcut card, due-today / overdue / upcoming sections |
| **Subjects** | Subject cards with task counts; create/edit/archive; subject detail with tasks grouped by status |
| **Tasks** | Create/edit with type, priority, subject, due date (+ optional time), reminder; complete from any list |
| **Calendar** | Month grid with locale-aware first-day-of-week, today ring, priority dots; tap a day to see and quick-add tasks |
| **Progress** | Total XP and level ring, level endpoints, completed / pending / percent-done cards, per-category progress, achievement badges |
| **AI Assistant** | Chat UI: text bubbles, structured suggestion cards with Accept / Edit / Reject, typing indicator, conversation history |
| **Create Account** | Three-step wizard — personal details, academic details, password with a live strength checklist |
| **Profile** | Level hero card, personal information rows with edit dialog, notifications, app preferences, account actions, About info, logout |

## Building

```bash
./gradlew assembleDebug
```

CI (GitHub Actions, `.github/workflows/android-ci.yml`) runs the same build on
every push.

### Firebase setup

`app/google-services.json` is a **placeholder** in the public repo; the build in
this checkout uses the project's real file. To run against your own Firebase
project, replace it with the real file from the Firebase console.

In the Firebase console, under **Authentication → Sign-in method**:

- **Email/Password** — enable it. Required for Sign In and the Create Account
  wizard, including the "Forgot password?" reset email.
- **Google** — enable it (pick a support email) if you want the Google button to
  work. Google sign-in additionally needs this build's signing fingerprint
  registered under **Project settings → Your apps → Add fingerprint**; get it
  with `./gradlew signingReport` (the SHA-1 of the `debug` variant). Without it
  the SDK fails with DEVELOPER_ERROR and the app explains what is missing
  instead of failing silently.

The **Microsoft** button is a visual placeholder: Microsoft sign-in needs an
Azure app registration, which this project does not have, so tapping it reports
that rather than pretending to sign in.

The wizard's academic fields (student number, institution, course, year of
study) have no Firebase equivalent and are stored locally on the device.

### It won't run: "Edit configuration" or a failing Gradle sync

Android Studio creates its run configuration during Gradle sync, so **if sync
fails, the Run dialog comes up empty**. Fix the sync error and the
configuration appears by itself. The three local causes, in the order they
usually bite:

| Symptom (Gradle / Build output) | Cause | Fix |
| --- | --- | --- |
| `SDK location not found. Define a valid SDK location ...` | A fresh clone has no `local.properties` (it is gitignored), so Gradle doesn't know where the Android SDK is | Create `local.properties` in the project root with `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk` (see `local.properties.example`), or set the `ANDROID_HOME` environment variable |
| `What went wrong:` followed by nothing but a version number, e.g. `25.0.1` | That number **is the JDK version** Gradle refuses to run on. Gradle 8.7 (this project's wrapper) runs on Java 8–21 only; Java 25 needs Gradle 9.1+. Version 25 is the common "latest LTS" download, so a recent JDK install triggers this | Use JDK 17 — see below |
| `Android Gradle plugin requires Java 17` / `Unsupported class file major version` | Same root cause: Gradle is being run by the wrong JDK | Use JDK 17 — see below |
| `Plugin [id: 'com.android.application', version: '8.4.2'] was not found` or a sync that stops with a version message | The installed Android Studio is older than the Android Gradle Plugin (8.4.2 needs **Jellyfish 2023.3.1 or newer**) or there is no network access to `google()` | Update Android Studio, or build from the terminal (`./gradlew assembleDebug`) |

**Running on JDK 17.** The app is compiled and run by JDK 17 (that is what CI
pins, and it is the minimum the Android Gradle Plugin 8.4.2 accepts). A newer
JDK on the machine will break the build even though the project is fine —
Gradle's own compatibility matrix is the reference:
<https://docs.gradle.org/current/userguide/compatibility.html> (`Support for
running Gradle`: Java 25 requires Gradle 9.1.0+).

- **Android Studio:** Settings → Build, Execution, Deployment → Build Tools →
  Gradle → *Gradle JDK* → **Download JDK…** → Version **17** → Download. Then
  *File → Sync Project with Gradle Files*. This is also what fixes an empty
  Run dialog: the run configuration is produced by a successful sync.
  Pick **17 exactly, not "Latest"** — any 17.x patch release is fine, and the
  vendor matters less than the version (Eclipse Temurin 17 is what CI installs;
  JetBrains Runtime 17 also works). Java 21 would satisfy Gradle 8.7 but buys
  nothing here: 17 is the version the Android Gradle Plugin 8.4.2 targets and
  the one this build is pinned to.
- **Terminal:** run `tools/set-gradle-jdk17.ps1`, which finds the installed
  JDK 17 and writes the setting for you (it backs the file up first and touches
  nothing in the repository). If Windows reports that running scripts is
  disabled, use
  `powershell -ExecutionPolicy Bypass -File .\tools\set-gradle-jdk17.ps1`. To do it by hand instead, add this line to
  `C:\Users\<you>\.gradle\gradle.properties` — machine-local, never
  committed — replacing the path with your JDK 17:

  ```properties
  org.gradle.java.home=C:/Program Files/Java/jdk-17
  ```

  Check `java -version` and `$env:JAVA_HOME` if the build still fails.

The command that separates "my project is broken" from "my IDE is misconfigured"
is the one CI runs:

```bash
./gradlew assembleDebug      # Windows PowerShell: .\gradlew assembleDebug
```

`BUILD SUCCESSFUL` means the code is fine and only the IDE needs attention.

### Local mode (default — no backend needed)

With **no** `api.baseUrl` configured, the app runs fully self-contained:
subjects, tasks, calendar and progress are stored **on-device** (SharedPreferences
via `LocalStore`), and completing tasks awards points (High 20 / Medium 10 /
Low 5) and builds a daily streak locally. Add `api.baseUrl` to `local.properties`
when you deploy a backend and every repository switches to the REST API
(`docs/API_CONTRACT.md`) — the Profile → About screen shows which mode you're in.

### AI brain (optional — works without a backend)

The assistant chat runs directly against any OpenAI-compatible LLM API
(**Groq** or xAI Grok) so it works during development with no StudyTrack
backend deployed. Copy `local.properties.example` to `local.properties`
(it's gitignored) and set:

```properties
grok.apiKey=gsk-your-groq-key-here
grok.baseUrl=https://api.groq.com/openai/v1/
grok.model=openai/gpt-oss-120b
```

Get a Groq key at [console.groq.com](https://console.groq.com) (API Keys).
`openai/gpt-oss-120b` is the quality pick, `openai/gpt-oss-20b` the cheap
one; for xAI use `grok.baseUrl=https://api.x.ai/v1/` + `grok.model=grok-4`.
Note the key is baked into your personal builds — fine for your own use,
never publish such a build.

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
