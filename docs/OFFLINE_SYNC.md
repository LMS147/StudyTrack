# Offline support, local SQLite cache and per-account isolation

How StudyTrack works with no connectivity, how the local cache syncs, and how
two accounts can share one device without ever seeing each other's data.

---

## 1. The remote source of truth is the REST API, not Firestore

**This is the one place where the implementation deliberately differs from the
original brief, and it is worth being explicit about.**

The brief described Firestore as "already in use via the backend/API layer". It
is not. This project has never contained Firestore:

- The only Firebase artifact in `app/build.gradle.kts` is
  `com.google.firebase:firebase-auth`.
- `grep -rni firestore` across the repository returns **zero matches**.
- All remote access goes through Retrofit against a REST API
  (`data/remote/ApiService.kt`), authenticated with a Firebase **ID token**
  exchanged in `AuthInterceptor` / `TokenManager`.

So the architecture described in the brief was built against the actual remote
layer: **the StudyTrack REST API is the source of truth, Room is the offline
cache and sync queue.** Everything else in the brief — offline-first reads,
write-then-queue, WorkManager retries, last-write-wins, per-UID isolation — is
implemented as specified.

### Decision: is Room a replacement for Firestore offline persistence?

Recorded here so the two can never silently end up fighting each other:

> **Room is the single offline cache. If Firestore is ever added to this
> project, its own offline persistence MUST be disabled.**

Firestore's SDK ships an offline cache of its own. Running both would mean two
caches, two write queues and no shared notion of "pending", so a change could
sit unpushed in one while the other reported success. Concretely, if Firestore
is introduced:

```kotlin
val settings = FirebaseFirestoreSettings.Builder()
    .setPersistenceEnabled(false)   // Room owns offline; Firestore stays online-only
    .build()
FirebaseFirestore.getInstance().apply { setFirestoreSettings(settings) }
```

Firestore would then be used only as a transport, with Room remaining the read
path and the queue. The alternative — letting Firestore own offline and dropping
Room — would give up the per-UID `ownerUid` schema guarantees that this document
spends most of its length on.

---

## 2. Architecture

```
        UI (Fragments / ViewModels)
                 │  unchanged interfaces
                 ▼
     OfflineFirst*Repository           ← reads Room, writes Room, queues sync
        │                    │
        ▼                    ▼
   Room (SQLite)      SyncScheduler → WorkManager → SyncWorker
   ownerUid-scoped            │
                              ▼
                    REST API (source of truth)
```

| Piece | File |
| --- | --- |
| Entities | `data/local/entity/*.kt` |
| DAOs | `data/local/dao/*.kt` |
| Database + migrations | `data/local/StudyTrackDatabase.kt` |
| Sync status enum | `data/local/SyncStatus.kt` |
| Active-account pointer | `auth/CurrentAccount.kt` |
| Offline switch rules | `auth/AccountSwitchGuard.kt` |
| Sign-in / sign-out orchestration | `auth/AccountSessionManager.kt` |
| Repositories | `data/repository/OfflineFirst*.kt` |
| Background sync | `data/sync/SyncWorker.kt`, `SyncScheduler.kt`, `ConnectivityMonitor.kt` |
| Conflict rules | `data/sync/ConflictResolver.kt` |

The previous `LocalStore` (SharedPreferences JSON) and the `Remote*Repository`
implementations were **removed**, not left alongside. Two storage stacks meant
two places to get per-account scoping right, and only one of them would
eventually get it. `api.baseUrl` now only decides whether the offline-first
repositories are handed an `ApiService` to sync against.

### Local mode still works

With `api.baseUrl` blank there is no backend. Repositories are constructed with
`api = null`, writes are stored already `SYNCED` (there is no remote to owe
anything to), and no sync work is ever queued.

---

## 3. Per-account data isolation

This is the critical requirement, so it is enforced at four independent layers.
Any one of them failing does not on its own produce a leak.

### Layer 1 — `ownerUid` is part of the primary key

```kotlin
@Entity(
    tableName = "tasks",
    primaryKeys = ["ownerUid", "taskId"],   // not just an indexed column
)
```

Two accounts therefore **cannot** produce a row collision, and
`OnConflictStrategy.REPLACE` can never overwrite a different user's row — the
keys differ. An accidentally unscoped *write* has nowhere to land.
`ProgressEntity` is keyed by `ownerUid` alone (one aggregate row per user).

### Layer 2 — no unfiltered DAO accessor exists

Every DAO method takes an explicit `ownerUid: String`, and every `@Query`
carries `WHERE ownerUid = :ownerUid`. There is no `getAllTasks()`, no
`deleteAll()`, no `@Query("SELECT * FROM tasks")`. The blanket query the
requirement forbids is not merely avoided — it is **unrepresentable**. A caller
with no UID has no method to call.

The only exception is `AccountDao`, which reads `account_records` unscoped *by
design*. That table holds no study data — only UIDs, emails and sync
timestamps — and its entire purpose is to answer questions about other accounts
before any of their data is touched (see §3.4). It has no columns for tasks,
subjects, progress or sessions, so no amount of unscoped reading there can
surface study data.

### Layer 3 — the UID comes from one place

`auth/CurrentAccount.kt` is the only source of the active UID. Repositories read
it internally; **no UI code passes a UID**. `requireUid()` throws
`IllegalStateException` when nobody is signed in rather than returning `""` — a
blank UID in a `WHERE ownerUid = ?` clause would return nothing and hide the
bug, so failing loudly is the safer default.

The observed lists are live queries:

```kotlin
override val tasks: StateFlow<List<Task>> = currentAccount.activeUid
    .flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList())
        else db.taskDao().observeForOwner(uid).map { rows -> rows.map { it.toModel() } }
    }
    .stateIn(scope, SharingStarted.Eagerly, emptyList())
```

When the account changes, `flatMapLatest` tears down the old query and starts a
new one. There is no in-memory list to remember to clear, which is the classic
source of cross-account leakage.

### Layer 4 — static enforcement in `tools/check_references.py`

The checker parses every `@Query` in `data/local/dao/` and fails the build if a
query against `tasks`, `subjects`, `progress` or `study_sessions` lacks an
`ownerUid` predicate, or binds `:ownerUid` without declaring the parameter. It
also fails if any entity drops `ownerUid` from its primary key.

This exists because **the Kotlin compiler cannot catch it** — both the scoped
and unscoped SQL are perfectly valid. Verified working:

```
$ python3 tools/check_references.py          # clean tree
OK: 90 Kotlin files, 27 layouts, 327 strings, 53 drawables — all references resolve

# after adding  @Query("SELECT * FROM tasks")  to TaskDao:
FAILED: 1 problem(s)
  - TaskDao.kt: query on ['tasks'] has no ownerUid predicate — it would read or
    write every account's rows: SELECT * FROM tasks
```

### 3.4 Sign-in, sign-out, and the offline switch

All account transitions go through `AccountSessionManager`; nothing else sets
the active UID.

| Event | Behaviour |
| --- | --- |
| **Sign in** (account known + bootstrapped) | Set the active UID; serve from cache instantly. |
| **Sign in** (account never seen here) | Fetch the full remote snapshot **before any screen is shown**. |
| **Sign out** | Clears the pointer **only**. No cached rows are deleted. |
| **Remove account from device** | The *only* path that deletes an account's rows. Not sign-out. |
| **Sign in offline, account unseen** | **Refused**, before Firebase is contacted. |

Sign-out keeping the cache is what makes multi-account use on one device
useful: switching back restores that workspace instantly instead of
re-downloading it.

The offline refusal happens **before** authentication, which matters: once
Firebase has signed the new account in, the previous session is already gone and
a switch that should never have happened cannot be cleanly undone.

```kotlin
const val MESSAGE_OFFLINE_SWITCH =
    "Can't switch accounts while offline — connect to the internet first"
```

The decision matrix, pinned down by `AccountSwitchGuardTest`:

| Online | Known on device | Bootstrapped | Outcome |
| :---: | :---: | :---: | --- |
| no | no | – | **Blocked** (`account-unseen-on-device`) |
| no | yes | no | **Blocked** (`account-cache-incomplete`) |
| no | yes | yes | Allowed, serve from cache |
| yes | no | – | Allowed, **fetch first** (`first-time-account`) |
| yes | yes | no | Allowed, **fetch first** (`cache-incomplete`) |
| yes | yes | yes | Allowed, serve from cache |

Re-authenticating the *already active* account is never blocked, offline or not —
its rows are already scoped to it.

Email matching is case-insensitive and trimmed, because Firebase returns
lowercase emails while users type whatever they like; a mismatch would wrongly
report a known account as unseen.

---

## 4. Sync behaviour

### Writes

Every mutation writes to Room first and returns immediately, so the UI updates
regardless of connectivity:

| Action | Status written |
| --- | --- |
| create | `CREATED` |
| edit an existing row | `UPDATED` |
| edit a row that was never pushed | stays `CREATED` (a `PUT` would 404) |
| delete | `DELETED` (soft — a tombstone) |
| row pulled from the remote | `SYNCED` |

`pendingSync` is derived (`syncStatus != SYNCED`), so it can never drift out of
step with the status.

Deletes are **soft** on purpose. A tombstone stays until the remote confirms the
delete, so an offline delete cannot be resurrected by the next pull. Every
user-facing read filters tombstones out; only the sync worker sees them.

### Push order

`SyncWorker` pushes **subjects before tasks** (a task carries a `subjectId`, so
the subject must exist remotely first), then study sessions. Within a table,
rows go out in `localUpdatedAt` order, so create-then-delete cannot invert into
a resurrected row.

The worker resolves the active UID **once, at the top of `doWork`**, and threads
that same value through every call. A worker that re-read the account per
repository could straddle a sign-out/sign-in and push one account's edits under
another's session. If nobody is signed in it returns success having done
nothing — it never falls back to "any" account's data.

### Retry

Handled by WorkManager rather than by app code:

- `NetworkType.CONNECTED` — the request is held until connectivity returns, so
  "retry when the network comes back" needs no polling loop.
- `BackoffPolicy.EXPONENTIAL` — a `Result.retry()` (transient 5xx, dropped
  connection) reschedules with growing delays.
- `ExistingWorkPolicy.KEEP` — a burst of edits queues one worker, not N. Safe
  because the worker reads the pending set from the database *when it runs*.

Nothing is consumed optimistically: a row is only marked `SYNCED` after the
remote confirms, so a retry re-attempts exactly the unconfirmed rows.

### Conflict resolution: last-write-wins

Rules live in `data/sync/ConflictResolver.kt`, in priority order:

1. **An unpushed local edit always wins.** The remote cannot reflect a change it
   has not received; overwriting it would silently delete the user's work.
2. **A local tombstone always wins** over a remote row.
3. Otherwise the **newer timestamp wins**, ties going to the remote.

> **Known limitation, stated plainly:** the wire `Task` / `Subject` models carry
> **no server-side `updatedAt` field**, so rule 3 cannot compare against a real
> remote timestamp. The pull therefore uses the fetch time, which makes the
> effective policy "an unpushed local edit wins; for rows both sides already
> agree on, the remote is authoritative". That is safe but is not yet full
> timestamp LWW across two devices that both edited while offline. Adding an
> `updatedAt` to the API responses upgrades rule 3 to genuine LWW with no change
> to the resolver.

---

## 5. Database migrations

- Schemas are exported to `app/schemas/` (`room.schemaLocation` KSP arg) and
  committed, so every schema change is a reviewable JSON diff.
- **`fallbackToDestructiveMigration()` is never called.** An unhandled version
  bump fails loudly at open time instead of silently dropping every account's
  queued work. For an offline-first app, a crash is recoverable; a wiped queue
  is not.
- Migrations are written ahead of time and filtered by the shipping version, so
  a prepared migration stays dormant until the version it targets actually
  ships, then activates itself:

```kotlin
val ALL_MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2)
val MIGRATIONS: Array<Migration> =
    ALL_MIGRATIONS.filter { it.endVersion <= VERSION }.toTypedArray()
```

Procedure for any future change: edit the entity → bump `VERSION` → write
`MIGRATION_<old>_<new>` with explicit SQL (never let Room recreate the table) →
commit the regenerated schema JSON → add a migration test.

---

## 6. Verification

`./gradlew testDebugUnitTest` runs all of the below on CI. The sandbox this was
written in has no JDK or Android SDK and blocks the Gradle/Maven hosts, so the
Gradle steps run in CI while `tools/check_references.py` runs locally.

| Test | What it proves |
| --- | --- |
| `AccountSwitchGuardTest` | The whole offline switch matrix, including the exact refusal wording. Plain JVM. |
| `TaskDaoIsolationTest` | Per-UID queries against **real SQLite** via Robolectric: observing, id lookup, composite-key inserts, tombstones, pending queues, snapshot replacement, account removal. |
| `OfflineFirstTaskRepositoryIsolationTest` | Repository-level isolation with a fake API: writes are attributed correctly, one account cannot edit/complete/delete another's task, **sign-out keeps the cache**, and a write with no session throws instead of storing an unscoped row. |
| `StudyTrackDatabaseTest` | Inspects the generated schema via `PRAGMA table_info`: every scoped table has a `NOT NULL ownerUid` in its primary key; `account_records` holds no study data; the migration filter behaves. |
| `ConflictResolverTest` | An unpushed edit or delete can never be overwritten by a pull. |

The DAO and repository tests exercise the **real Room-generated implementation**,
not a reimplementation of the queries — if a `WHERE ownerUid = :ownerUid` is
ever dropped, the corresponding test fails.

### Manual test plan (needs a device)

1. Sign in as account A. Airplane mode on. Create three tasks — they appear
   immediately.
2. Airplane mode off. Confirm the tasks reach the backend and their rows flip to
   `SYNCED`.
3. Sign out. **Confirm account A's rows are still in the database.**
4. Sign in as account B (never used on this device, online). Confirm a fetch
   runs before the dashboard appears, and that B sees **none** of A's tasks.
5. Airplane mode on. Sign out of B, try to sign in as account C. Confirm the
   refusal message and that no screens are shown.
6. Sign back in as A while offline. Confirm A's three tasks reappear instantly.

---

## 7. Known gaps

Stated rather than left to be discovered:

- **`SettingsRepository` is not per-UID scoped.** Profile fields
  (`studentId`, `institution`, `course`, `yearOfStudy`) and the theme choice
  still live in a single `SharedPreferences` file, so they are shared across
  accounts on a device. The brief scoped isolation to the Room tables, and this
  was left as a follow-up rather than half-done — but it *is* a real
  cross-account leak of profile metadata and should be fixed next, by moving
  those fields into an `ownerUid`-keyed Room table.
- **No true cross-device LWW** until the API returns an `updatedAt` (§4).
- **No study-time UI yet.** `study_sessions` is written and queued but has no
  screen; the API also has no read endpoint for it.
- **Room's `exportSchema` JSON is generated at build time.** The first CI build
  on this branch produces `app/schemas/…/1.json`; it should be committed so
  future migrations have a baseline to diff against.
