# AI connection in installed APKs

GitHub runners do not receive your laptop's ignored local.properties. UI changes must not disable the chat or remove the backend routing.

The assistant now resolves its hosted backend in this order: the Connection setting saved on the device, the ai.baseUrl / STUDYTRACK_AI_BASE_URL build setting, then the existing api.baseUrl / STUDYTRACK_API_BASE_URL app backend. Existing local grok.apiKey development builds still work when no hosted backend is configured.

For GitHub APK builds, add STUDYTRACK_AI_BASE_URL under repository Settings → Secrets and variables → Actions → Variables (for example, your real HTTPS site root, with a trailing slash). STUDYTRACK_API_BASE_URL remains available if the same server also handles task sync. The AI-only variable does not turn local task storage into remote sync. The workflow also accepts STUDYTRACK_AI_BASE_URL as a secret.

An already installed APK can use AI → Connection to save your real HTTPS backend site root. The change takes effect on the next request, without reinstalling or restarting. Clearing the override restores the build default. Only use your trusted StudyTrack backend: requests use the existing Firebase Bearer authentication. The server must implement POST /api/ai/task-assistance using the contract in API_CONTRACT.md; this is not a field for a Groq or xAI provider URL.

Do not add provider API keys to a public APK. Keep them on the backend. The backend address and live provider credentials were not supplied in this session, so a live AI reply cannot be verified until a real server is configured. The status text describes configuration, not a health check.
