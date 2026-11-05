# StudyTrack

StudyTrack is a native Android app for organising coursework, planning study sessions and revising. Students can manage subjects and deadlines, follow a weekly timetable, review flashcards and ask an AI study assistant for help.

## Features

- **Subjects and tasks:** Create, edit and delete subjects; organise assignments, tests, exams and study tasks by subject, priority and due date. Mark tasks complete and track progress.
- **Dashboard and progress:** See tasks due today, overdue and upcoming work, completion statistics, XP, levels and achievement badges.
- **Calendar and recurring timetable:** View deadlines alongside recurring weekly classes. Add a class with its weekday, time, subject, dates, venue and lecturer; edit or remove the weekly series. Find it under **Calendar → Timetable**. [Timetable details](docs/TIMETABLE.md)
- **Revision flashcards:** Create question-and-answer cards for a subject, filter by subject and review cards that are due. Reveal the answer, then rate your recall **Again**, **Hard** or **Easy** to schedule the next review. Open **Dashboard → Revision flashcards**. [Flashcard details](docs/FLASHCARDS.md)
- **Subject management:** In Revision flashcards, use **Manage subjects** to see, add, edit or delete subjects. Deleting a subject keeps its flashcards on the device; they appear under **All subjects** as **Subject unavailable** until reassigned. Linked tasks may lose their subject.
- **AI study assistant:** Chat with an assistant powered directly by Groq to get study help and task suggestions. Review, edit, accept or reject suggestions before creating tasks. The app requests new replies in the selected app language; model quality and language adherence can vary. An internet connection and a configured AI key are required. [AI connection](docs/AI_CONNECTION.md)
- **App language:** Choose English, Afrikaans, Japanese, Sepedi, Setswana, Spanish, French or the device language from **Profile → App language**. Translated interface resources are stored in the app and work offline. Some messages can remain in English; machine-generated translations require review by fluent speakers. [Interface translations](docs/INTERFACE_TRANSLATIONS.md)
- **Task and class reminders:** Offline phone alerts at saved task reminder times (default: one hour before the deadline) and 15 minutes before weekly classes. Allow notifications and configure precise timing under Profile → Reminder delivery settings. [Reminder setup and limits](docs/REMINDERS.md)
- **Appearance and account:** Light and dark themes, profile information, and app preferences.

## Offline storage and accounts

StudyTrack uses Room (SQLite) for account-scoped on-device data. Tasks and subjects can also synchronise with a compatible StudyTrack REST backend when one is configured; WorkManager handles queued sync attempts. Firebase Authentication manages sign-in. For setup and sync behaviour, see [Offline sync and account isolation](docs/OFFLINE_SYNC.md).

**Timetable entries and flashcards are currently stored on the device only.** They do not synchronise across devices and are not included in the existing JSON export. Uninstalling the app removes this local data. The JSON export is a backup/reference and does not currently offer an import/restore flow. See [timetable](docs/TIMETABLE.md) and [flashcards](docs/FLASHCARDS.md) for the details.

## Install an APK from GitHub

1. Open [Actions → Android CI](https://github.com/LMS147/StudyTrack/actions/workflows/android-ci.yml).
2. Select a **successful** run for the branch you want, usually `main`.
3. Under **Artifacts**, download **StudyTrack-debug-apk**, extract the ZIP and install the APK on your Android device.

GitHub may require you to sign in to download an artifact. APKs are debug builds for testing. The CI workflow uses a permanent signing key and increasing version codes so compatible builds can update an existing installation. An APK signed with a different key may require a one-time uninstall, which removes local data. See [APK updates and signing](docs/APK_UPDATES.md) before changing signing keys or uninstalling.

## Technology

| Area | Technology |
| --- | --- |
| App | Kotlin, Android Views, ViewBinding, Navigation Component, Material 3 |
| Architecture | ViewModels, coroutines, Flow and a manual `ServiceLocator` |
| Local storage and sync | Room / SQLite, WorkManager |
| Authentication | Firebase Authentication |
| Networking | Retrofit, OkHttp and Kotlin serialization |
| AI | Direct Groq-compatible chat-completions API |
| Builds and tests | Gradle, GitHub Actions, JUnit and Robolectric |

The app uses **minimum Android API 26**, **compile/target SDK 34** and **JDK 17** in CI.

## Build locally

1. Clone the repository and open it in Android Studio.
2. Install the Android SDK and select JDK 17 for Gradle. Set `ANDROID_HOME` or configure `sdk.dir` in your local `local.properties` if Android Studio has not done so.
3. Use the included Firebase Android configuration or replace `app/google-services.json` with one from your Firebase project. Enable Email/Password authentication; Google sign-in also requires the correct signing fingerprints in Firebase.
4. Sync Gradle, then build and test:

   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```

   On Windows PowerShell use `.\\gradlew` instead of `./gradlew`. The APK is generated under `app/build/outputs/apk/debug/`.

If Gradle sync fails, check the Android SDK path, JDK version and your network connection to the Gradle and Google repositories. This project uses in-process Kotlin compilation; `tools/fix-kotlin-daemon.ps1` and `tools/set-gradle-jdk17.ps1` are available for Windows troubleshooting.

### Configure the AI assistant

The app can use a device-entered Groq key via **AI → AI settings**. The device key is stored encrypted using Android Keystore and takes precedence over a key bundled in a personal build. See [AI connection](docs/AI_CONNECTION.md).

For local development, `local.properties` can also contain `grok.apiKey`; it may additionally specify `grok.baseUrl` and `grok.model`. GitHub Actions can use the repository secret `GROQ_API_KEY` for a personal build. **A key bundled into an APK can be extracted**, so do not distribute such an APK publicly. Never commit keys, keystores or passwords, and do not paste them into issues or chats.

### Optional REST backend

Without `api.baseUrl`, the app uses local mode for study data. Set `api.baseUrl` in the ignored `local.properties`, or the `STUDYTRACK_API_BASE_URL` GitHub Actions variable for CI, to use a compatible backend for supported data. The expected endpoints are described in [API contract](docs/API_CONTRACT.md). The direct Groq assistant does not require the StudyTrack backend.

## Project structure

```text
app/src/main/java/com/studytrack/app/
├── ai/            AI chat and suggestions
├── auth/          Sign-in, registration and account sessions
├── calendar/      Calendar and timetable display
├── dashboard/     Home dashboard
├── data/          Room entities and DAOs, networking and repositories
├── flashcards/    Flashcard editing and spaced review
├── language/      App language and translated task labels
├── profile/       Profile and app preferences
├── progress/      Progress and achievements
├── subjects/      Subject list, details and editing
├── tasks/         Task list, editing and details
├── timetable/     Recurring class management
└── util/          Shared helpers
```

Additional technical documentation is available in [docs/](docs/), including [architecture decisions](docs/ARCHITECTURE_DECISIONS.md).

## Project links

- [GitHub repository](https://github.com/LMS147/StudyTrack)
- [Project video] (https://advtechonline-my.sharepoint.com/:v:/r/personal/st10436200_rcconnect_edu_za/_layouts/15/stream.aspx?id=%2Fpersonal%2Fst10436200_rcconnect_edu_za%2FDocuments%2FStudy-Track.mp4&nav=eyJyZWZlcnJhbEluZm8iOnsicmVmZXJyYWxBcHAiOiJPbmVEcml2ZUZvckJ1c2luZXNzIiwicmVmZXJyYWxBcHBQbGF0Zm9ybSI6IldlYiIsInJlZmVycmFsTW9kZSI6InZpZXciLCJyZWZlcnJhbFZpZXciOiJNeUZpbGVzTGlua0NvcHkifX0&share=cQpKqtRVRkroRaK86wHwsvE5EgUC8kDyn51i0m2Plz1aBxEZdQ)
