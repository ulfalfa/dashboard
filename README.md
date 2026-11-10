# Dashboard

Rotating web dashboard for the ViewSonic ViewBoard (Android 11). Settings portal on port 2411, side menu via swipe from the left edge.

## Build & test
    ./gradlew testDebugUnitTest assembleDebug

CI (`.github/workflows/ci.yml`) runs this on every push to `main` and every pull request.

## Release
Manual: GitHub -> Actions -> Release -> Run workflow (branch `main`). Leave `version` empty: it is derived from the Conventional Commits since the last tag (`fix` -> patch, `feat` -> minor, breaking `!` -> minor while below 1.0.0, see `[bump]` in `cliff.toml`). Enter a version like `1.2.0` only to override it. The run fails if there are no commits since the last tag.
Creates tag `v1.2.0` and a GitHub release with the signed `dashboard-1.2.0.apk`. Release notes come from [git-cliff](https://git-cliff.org) (`cliff.toml`), and the workflow commits the full `CHANGELOG.md` directly to `main` (`chore(release): vX.Y.Z`), so commits must follow Conventional Commits (`feat: ...`, `fix: ...`); other commits are left out of the notes.

One-time setup of a signing key (keep the keystore safe - updates must be signed with the same key):

    keytool -genkeypair -v -keystore release.jks -alias dashboard -keyalg RSA -keysize 4096 -validity 10000
    gh secret set KEYSTORE_BASE64 < <(base64 -i release.jks)
    gh secret set KEYSTORE_PASSWORD
    gh secret set KEY_ALIAS
    gh secret set KEY_PASSWORD

## Kiosk setup on the ViewBoard
- Autostart: the app registers as a Home launcher. After installing, press Home and choose "Dashboard" -> "Always"; Android then starts it after every boot.
- Per page `desktop` (default `true`): sends a desktop user agent (no `Mobile`/WebView marker) so sites like spiegel.de serve their desktop layout instead of the phone layout. Switch it off per page in the portal if a page needs its mobile layout. Configs without the field behave as `true`.
- Per page `renderDelaySeconds`: wait after the load event (late-rendering Grafana panels) before the display time starts.
- Preloading: every page has its own WebView; the next page loads in the background while the current one is shown, so switching is instant. Per page `reload`: `true` reloads it in the background before every appearance (fresh data), `false` loads it only once (use with the page's own auto refresh, e.g. Grafana). Each page keeps a WebView in memory.
- If the WebView renderer process dies, the app replaces the WebView and restarts the rotation.

## Status and remote control
The portal (port 2411) shows a status card above the rotation settings, refreshed every 5 s: per page the last successful load (age, load time), the last error and the failure count, plus free memory, the number of WebViews and the app uptime. It warns when there are more than 8 pages (every page keeps a WebView in memory) or when Android reports low memory.

Buttons (previous / pause-resume / next / reload current) control the rotation without touching the board. The same is available for scripts and monitoring (Basic auth as below):

    curl -u admin:<password> http://<board-ip>:2411/api/status
    curl -u admin:<password> -X POST -d '{"action":"next"}' http://<board-ip>:2411/api/control   # next | prev | pause | resume | reload

Pause keeps the current page on screen while the next one keeps preloading; a config change or the app going to the background resets it.

## Portal login
The settings portal (port 2411) is protected with HTTP Basic auth: user `admin`, password generated on first start. It is shown in the side menu (swipe from the left edge) and can be regenerated there ("Neues Passwort erzeugen"). Basic auth is not encrypted on plain HTTP - use it in a trusted LAN only.
