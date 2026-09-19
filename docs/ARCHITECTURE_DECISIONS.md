# Architecture Decisions

Key decisions for the StudyTrack Android client, with rationale and
consequences. Companion to [API_CONTRACT.md](API_CONTRACT.md).

## 1. View-based UI (Views + ViewBinding), not Compose

Material 3 XML layouts with ViewBinding and the Navigation Component.
**Why:** the project brief pinned the dependency list (Material, Navigation,
Retrofit, coroutines/Flow, Firebase) and views keep the UI code reviewable
with a small surface. **Consequences:** more layout files than Compose would
need (they live in `res/layout/` and are checked by `tools/check_references.py`),
no composition — shared row UI is extracted into a binder instead.

## 2. Manual DI (ServiceLocator), no DI framework

`ServiceLocator` is a ~40-line object of `by lazy` singletons initialized from
`StudyTrackApp.onCreate()`.
**Why:** keeps the dependency list exactly as specified; adding Hilt/Koin would
add build complexity for little gain at this app size.
**Consequences:** ViewModels construct via `viewModelFactory { initializer { … } }`
companions (one per ViewModel); everything is replaceable in tests by
re-initializing the locator.

## 3. Repository layer with StateFlow caches

Each repository (Subject, Task, Calendar, Progress, AI) exposes a
`StateFlow<List<T>>`/`StateFlow<T?>` cache plus suspend CRUD methods. Mutations
update the backend first, then refresh the cache best-effort ("refresh after
write"); on failure the user sees the server error and the cache stays intact.
**Why:** screens observe a single source of truth; the calendar gets its own
repository so month views don't fight the flat task cache.
**Consequences:** offline-first is explicitly out of scope — no local database.
The app is online-first; if that changes, the repository layer is the seam
where a Room-backed cache would slot in.

## 4. Screen state via `combine` in ViewModels

Each screen ViewModel derives its whole UI state (loading flags, sections,
errors) with `combine(...)` over repository flows + local `MutableStateFlow`s,
exposed as one `StateFlow<UiState>`. Fragments only render.
**Why:** one collect site per screen; state updates are declarative and
testable; no imperative cross-widget bookkeeping.
**Consequences:** static one-off data (e.g. the account card) is read directly
in the ViewModel instead of being forced through a flow.

## 5. Auth: Firebase tokens, never passwords

Email/password sign-in happens against Firebase Auth in the app. The REST API
session token is attached by `AuthInterceptor`; `TokenAuthenticator` refreshes
it on 401. `TokenManager` stores it in `SharedPreferences` (see §9).
**Why:** the backend never sees credentials; refresh is transparent to callers.
**Consequences:** logout must clear both Firebase and the API token; one
`AuthStateListener` in `MainActivity` owns all auth-related navigation, so no
fragment ever navigates on auth events itself.

## 6. AI date handling: backend resolves, client never guesses

The client sends `today` (device-local ISO date) and `timezone` (IANA id) with
every AI request and expects the **backend** to resolve relative dates.
Suggestions with `dueDate: null` are rendered with an editable date field;
Accept stays disabled until the user picks a date.
**Why:** guessing "next Friday" client-side risks wrong dates; the device
context belongs to the client, interpretation belongs to the LLM/backend.
**Consequences:** subtasks of an accepted suggestion inherit the parent's
resolved date when they lack their own (deterministic, documented in
API_CONTRACT.md).

## 7. AI suggestions flow through the regular Tasks repository

Accepting a suggestion creates the parent task and its subtasks via
`POST /api/tasks` — the chat has no separate write path. "Edit" opens the
normal task editor with the suggestion pre-filled; the editor reports back via
`NavController` `savedStateHandle` (`NavResultKeys.AI_SUGGESTION_CREATED`) and
the chat marks the card as accepted.
**Why:** one write path to validate; the editor is the richest editing surface
and reusing it avoids duplicating form logic.
**Consequences:** the AI feature can't create data the Tasks screen doesn't
know about.

## 8. Chat history is in-memory (ViewModel), not persisted

The conversation survives rotation and navigation within the session (ViewModel
scoped to the nav destination) but is deliberately lost when the destination is
popped.
**Why:** no server-side conversation endpoint in scope; persisting locally
would desync from the server anyway.
**Consequences:** revisit when the backend adds conversation storage.

## 9. Local settings via SharedPreferences

`SettingsRepository` ("studytrack_settings") stores notification switches and
AI preferences (dashboard AI card on/off, default AI priority). The dashboard
reads `showAiCardOnDashboard` when building state.
**Why:** tiny, synchronous, no schema; notification master switches are ready
to gate a future WorkManager-based reminder scheduler.
**Consequences:** tokens live in `TokenManager`'s prefs, not here, so the two
never mix.

## 10. UI text policy

All screen chrome uses `res/values/strings.xml` resources. The two exceptions
are chat *content* strings produced by business logic in ViewModels (accept
confirmations like "Added to your calendar for Friday, 14 November"): the
fragment injects those as string *templates* (`%1$s`…) into the ViewModel at
`start()`, so the ViewModel formats but never hardcodes UI text. AI reply text
itself is backend-generated content.

## 11. CI is the compile gate

No local Android SDK in the dev environment — GitHub Actions
(`assembleDebug` on JDK 17) is the authoritative build check. A small static
checker, `tools/check_references.py`, validates all `@string`/`@drawable`/…
resource references (Kotlin + XML) before every push to catch typos locally.
On failure CI commits the build log tail to `.build/ci-failure.log`.

## 12. Lenient enum parsing for AI output

AI suggestions carry free-form `taskType`/`priority` strings; `fromRaw()`
parses case-insensitively and falls back to `Study`/`Medium` (or the user's
configured default priority, which the Profile screen can set).
**Why:** LLM output must never crash the client; silent coercion is acceptable
for a suggestion the user reviews before accepting.

## 13. Direct LLM mode ("Grok brain")

`AiBrain` has two implementations: `AiRepository` (backend
`/api/ai/task-assistance`, preferred — key and prompt logic stay server-side)
and `GrokAiRepository`, which calls an OpenAI-compatible chat-completions
endpoint (xAI Grok by default) directly from the app.

**Why:** lets the assistant work during development / personal use with no
deployed backend. **Contract preservation:** the same brain contract (§6 /
API_CONTRACT.md) is enforced client-side via the system prompt — JSON reply +
structured suggestions, relative dates resolved against the device's
today/timezone, null dueDate when unsure — and defensive parsing degrades
non-JSON model output to a plain text bubble instead of crashing.
**Consequences:** the LLM key ships inside the APK and is extractable —
acceptable for personal builds only, which is why it lives in gitignored
`local.properties` → `BuildConfig`, never in source control. Accepting or
editing suggestions still goes through the Tasks repository, so task
persistence still requires the StudyTrack backend.
