# GTDdo Nota rapida for Android

Community Android companion for [tududi](https://github.com/chrisvel/tududi), built for a fork.
This is not an official tududi Android application.

## Features (MVP 0.2.0)

- Native Android Quick Settings tile called "Nota rapida".
- Compact native capture dialog directly to the native tududi Inbox.
- Also accepts text shared from other Android applications (Android Share menu).
- Configurable HTTPS server base URL and personal `tt_...` API token.
- Optional Cloudflare Access service token for an Access-protected server: `CF-Access-Client-Id` and `CF-Access-Client-Secret` headers on every request.
- Cloudflare Client ID and Client Secret encrypted with Android Keystore AES-256-GCM and saved together atomically; a dedicated button clears both credentials.
- API token encrypted using Android Keystore AES-256-GCM; Android backups disabled.
- Unsaved text retained as an encrypted local draft; no background auto-retries.
- Connection check before first use.
- Button to request system installation of the Quick Settings tile on Android 13+.
- Android 8.0+ supported (minSdk 26). Modern TileService PendingIntent supported on Android 14+.

## Server API, verified against upstream source

Uses the existing endpoint `POST /api/v1/inbox` with:

```json
{"content":"Remember to call the designer", "source":"android_quick_tile"}
```

Successful response: HTTP `201 Created`. Authentication: `Authorization: Bearer tt_...`.

See upstream source:

- `backend/modules/inbox/routes.js`
- `backend/modules/inbox/controller.js`
- `backend/modules/inbox/service.js`
- `backend/middleware/auth.js`

No modifications to tududi's backend are required for this MVP.

## Build

Requires Android SDK API 35, Java 17, Gradle 8.11.1.

```sh
cd android-quick-capture
gradle :app:testDebugUnitTest :app:assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.
The included root GitHub Actions workflow can compile the APK automatically after the module is added to the fork.

## First launch

1. Deploy and configure tududi in your own environment.
2. In tududi's user account settings, generate a personal API key (format `tt_...`).
3. Install the debug APK on Android. This is a test build, not a Play Store release.
4. Open "GTDdo Nota rapida".
5. Enter the **HTTPS root URL** of your GTDdo server, e.g. `https://tasks.example.com`. The URL must not include `/api` or `/api/v1/inbox`.
6. Paste the GTDdo personal API key. If the server is protected by Cloudflare Access, also paste the **Client ID** and **Client Secret** for a Service Token authorized by a **Service Auth** policy. Do not paste your Cloudflare account API token.
7. Tap "Salva configurazione", then "Verifica connessione". Leave both Cloudflare fields empty to preserve existing saved credentials. If changing to a new server, re-enter both credentials or use "Elimina credenziali Cloudflare".
8. Tap "Aggiungi pulsante rapido". If your phone does not show the request, open the Quick Settings edit menu manually and add "Nota rapida".
9. Tap the new tile, dictate with your keyboard or type a note, then tap "Salva nella Inbox".
## Security and known limits

- HTTPS with a publicly or privately trusted certificate is required. HTTP and invalid TLS certificates are intentionally rejected.
- Protect your personal API token and both Cloudflare service token credentials. Never put secrets into source code, GitHub issues or `.env` files committed to a public fork.
- Cloudflare Access must have a **Service Auth** policy that explicitly includes this Android service token, separate from the browser's **Allow** policy. Never add an Everyone/Bypass rule for GTDdo.
- An Access service token is a bearer credential, **not** cryptographically bound to a Samsung device. It can be reused if stolen. Rotate or revoke it when the phone is lost and renew it before expiration.
- HTTPS and Android Keystore encryption protect network traffic and secrets at rest, but do not protect against every compromised/rooted device scenario.
- The app doesn't follow HTTP redirects, so an unexpected Cloudflare login redirect fails the connection test instead of sending tokens to a different origin.
- No offline synchronisation queue in this MVP. If posting fails, the note remains in the editor and is saved as a local encrypted draft for retry.
- On a successful response the draft is cleared. No automatic duplicate-prone retry is performed.
- A short network outage may cause a timeout after the server has already stored the note. Check the Inbox before retrying a timed-out submission.
- The tile opens a native Activity rather than modifying Android's system Quick Settings panel directly.
- Android/OEM implementations may display the tile in different places. Samsung devices also require the user to add the tile manually in some cases.

## Licence

The Android companion is supplied under the MIT licence, see `LICENSE`. The upstream tududi source has its own original MIT licence, which must remain in the fork.
