# StudyTrack AI: direct Groq connection

The assistant runs in the Android app and calls Groq directly. A StudyTrack backend URL is not required for AI. Task backend/sync settings remain independent.

## Installed GitHub APKs

Open AI → AI settings and enter your own Groq API key (gsk_…). The key is encrypted using Android Keystore AES-GCM and stored in the app's no-backup directory. It persists across ordinary app updates with the same application ID/signing key. It is not exported, displayed again, logged, or committed to GitHub. Removing the app clears it. Remove device key clears only the saved override.

The next request uses the new key without restarting. Device-entered keys always go to https://api.groq.com/openai/v1/chat/completions with Groq Bearer authentication, not Firebase or the obsolete backend setting. Model selection uses the existing AiEndpoints.GROQ default. A configured status does not claim the key has been validated with a live request.

## Existing local development builds

The existing grok.apiKey, grok.baseUrl and grok.model fields in ignored local.properties still work as a fallback when no device key is saved. Do not share a public APK containing a provider key: keys compiled into an APK are extractable. GitHub runners do not have your laptop's ignored local.properties, so enter your own key in AI settings for downloaded builds. Never paste real API keys into chat, issues or source control.

The former AI-backend URL dialog and AI-only CI URL variable were based on an incorrect assumption and have been removed. Existing task REST APIs and Firebase sign-in are preserved. Live Groq validation requires the owner to enter a real key on their device; no live key is available to this session.
