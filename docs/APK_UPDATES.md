# Installing StudyTrack updates without signature conflicts

Android updates require the same application ID and signing certificate, plus a version code at least as high as the installed app. StudyTrack keeps `com.studytrack.app`, uses one permanent private key for CI debug APKs, and assigns `100000 + GITHUB_RUN_NUMBER` as the version code.

## One-time setup (Windows PowerShell)

Use the keytool bundled with your installed JDK/Android Studio. Keep this keystore and password backed up privately; never commit or share them. If you already have the signing key for an installed APK, reuse it instead of generating a replacement. This workflow expects alias `studytrack` and matching key/store passwords.

1. Generate a permanent key on your own computer:

   ```powershell
   keytool -genkeypair -keystore studytrack-update.jks -alias studytrack -keyalg RSA -keysize 3072 -validity 10000
   ```

   Choose a strong password. If asked for a separate key password, press Enter to use the same password.

2. Copy the keystore's Base64 to the clipboard:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\studytrack-update.jks).Path)) | Set-Clipboard
   ```

3. Open https://github.com/LMS147/StudyTrack/settings/secrets/actions and add these **repository secrets**:
   - `STUDYTRACK_KEYSTORE_BASE64`: paste the clipboard contents.
   - `STUDYTRACK_KEYSTORE_PASSWORD`: the keystore password.

4. Merge this change and run Android CI, or use **Actions → Android CI → Run workflow** with branch `fix/permanent-apk-signing` selected to verify before merging. The workflow checks the key/alias and verifies that the APK certificate matches it before uploading **StudyTrack-debug-apk**.

The private signing key cannot be configured through the connected GitHub tools in this session; only you need to perform this setup. Do not paste the password or Base64 into a chat, issue or pull request.

## Transition from earlier APKs

Earlier GitHub runs generated different temporary debug keys. If Android rejects the first permanently signed APK, the old private key is needed for an in-place update; a new key cannot bypass Android's signature check. Export important data through **Profile → Export Study Data** before uninstalling. Uninstalling deletes local app data. JSON export is for backup/reference; the app does not currently provide an import/restore flow.

You may need to uninstall the old app **once**, then install the permanently signed APK. Later CI APKs use the same certificate and install as updates while retaining app data. Do not replace the secrets or install APKs signed by another local/debug/release key.

Register the new certificate's SHA-1/SHA-256 fingerprints in the Firebase Android app settings if you use Google sign-in. They can be inspected locally with `keytool -list -v -keystore studytrack-update.jks -alias studytrack`.

## Local builds and limits

Android Studio's default debug key differs from the permanent CI key. For locally built APKs to update a CI installation, set `STUDYTRACK_KEYSTORE_PATH`, `STUDYTRACK_KEYSTORE_PASSWORD` and an appropriate `STUDYTRACK_VERSION_CODE` in the build process environment. Do not put passwords on a shared command line or in tracked files.

These are debug test APKs, not production releases. Keep production signing and distribution separate. Signing/version consistency does not prevent unrelated install failures such as insufficient storage, incompatible Android versions, or a database migration bug.
