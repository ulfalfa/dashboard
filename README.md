# Dashboard

Rotating web dashboard for the ViewSonic ViewBoard (Android 11). Settings portal on port 2411, side menu via swipe from the left edge.

## Build & test
    ./gradlew testDebugUnitTest assembleDebug

CI (`.github/workflows/ci.yml`) runs this on every push to `main` and every pull request.

## Release
Manual: GitHub -> Actions -> Release -> Run workflow (branch `main`, version like `1.2.0`).
Creates tag `v1.2.0` and a GitHub release with the signed `dashboard-1.2.0.apk`. Release notes come from [git-cliff](https://git-cliff.org) (`cliff.toml`), and the workflow commits the full `CHANGELOG.md` directly to `main` (`chore(release): vX.Y.Z`), so commits must follow Conventional Commits (`feat: ...`, `fix: ...`); other commits are left out of the notes.

One-time setup of a signing key (keep the keystore safe - updates must be signed with the same key):

    keytool -genkeypair -v -keystore release.jks -alias dashboard -keyalg RSA -keysize 4096 -validity 10000
    gh secret set KEYSTORE_BASE64 < <(base64 -i release.jks)
    gh secret set KEYSTORE_PASSWORD
    gh secret set KEY_ALIAS
    gh secret set KEY_PASSWORD

## Kiosk setup on the ViewBoard
- Autostart: the app registers as a Home launcher. After installing, press Home and choose "Dashboard" -> "Always"; Android then starts it after every boot.
- Per page `renderDelaySeconds`: wait after the load event (late-rendering Grafana panels) before the display time starts.
- If the WebView renderer process dies, the app replaces the WebView and restarts the rotation.
