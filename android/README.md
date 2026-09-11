# Game Checkout (Android)

Native Kotlin + Jetpack Compose port of the iOS app in `../ios/GameCheckout`:
browse the game catalog, search, borrow/return a game, and see everything
currently borrowed.

This is a **behavioural** port, not a file-by-file translation. Every screen,
state transition, and edge case follows the Swift original, and each Kotlin
declaration names the Swift file it came from.

## What matches iOS

| Behaviour | iOS | Android |
| --- | --- | --- |
| Password gate | `AuthManager` + `KeychainStore` (CryptoKit sha256) | `AuthManager` + `SecureStore` (Keystore-backed AES-GCM); sha256 sent as `x-cfp` |
| Session lifetime | ~1 year, expiry stored alongside the hash | same, as an ISO-8601 instant |
| Rejected credential | clears storage, returns to login | same, via `AuthSession.handleUnauthorized` |
| Catalog source | `https://public.chill.ws/games.json`, unauthenticated | same |
| Borrowed state | `GET {api}/games/borrowed`, merged onto the catalog by `id` | same |
| Borrow | `PUT {api}/games/borrow` with `{id, borrowed:{name,email,date}}` | same, `date` = today as `yyyy-MM-dd` |
| Return | `PUT {api}/games/return` with `{id}` | same |
| `{"id":"error"}` body | treated as unauthorized despite HTTP 200 | same |
| Borrowed request fails | catalog still loads, without borrowed status | same |
| Search | game name, borrower name, borrower email — case-insensitive substring | `GameSearch`, same three fields |
| Borrowed row styling | blue + strikethrough title, monospaced details | same |
| Borrowed list order | `borrowedGames`, newest borrow date first | same |
| Borrow form prefill | `BorrowerProfileStore` (last name/email) | same |
| Pull to refresh | `.refreshable` | `PullToRefreshBox` |
| Admin screen | reuses the already-loaded list, no second fetch | one shared `GameListViewModel` |

## Analytics

Ported. `analytics/Tracker.kt` mirrors `Analytics/Tracker.swift` one-for-one —
same event names, same XDM field names — and sends through Adobe Experience
Platform Edge Network to the same datastream the iOS app and web app use
(`edge.configId = e8922806-0c73-4c26-a4f8-f102f34c9af6`).

`GameCheckoutApp` registers `Edge` and `Edge Identity` from the AEP SDK
(`com.adobe.marketing.mobile:sdk-bom`), sets that datastream id, then fires the
launch event from the registration callback.

Two details worth knowing before changing this:

- **No `Consent` extension is registered**, deliberately — matching iOS. Edge
  then logs `Collect consent is pending, suspending the Edge queue` followed by
  `Consent extension is not registered yet, using default collect status (yes)`
  and resumes the queue. That warning pair is expected, not a fault. Add
  `edgeconsent` and call `Consent.update` only if per-user consent must be
  honoured.
- **`Tracker.ready` gates every event.** Events raised before registration
  finishes are dropped rather than queued, which is why the SDK is wired up in
  `Application.onCreate` before any UI exists.

Events are sent for real — a debug build running against this datastream puts
real analytics into the production dataset. Point `EDGE_CONFIG_ID` at a
development datastream if that matters, or gate `send` on a build flag.

## What is deliberately different

- **The mailto link.** SwiftUI's `Link(mailto:)` is an `ACTION_SENDTO` intent;
  when no mail client is installed the tap is a no-op (iOS falls back to a plain
  label in that case).
- **Search UI.** `.searchable` in the navigation bar drawer becomes an
  always-visible field under the app bar — the closest Material equivalent of
  `.navigationBarDrawer(displayMode: .always)`.
- **In-app iconography.** iOS uses SF Symbols; Android uses the Compose Material
  icon set (`SportsEsports`, `List`, `Logout`, `WifiOff`, `CheckCircle`,
  `BrokenImage`). The *launcher* icon is the real iOS artwork — see below.

## Launcher icon

The launcher icon is generated from the iOS app icon
(`Assets.xcassets/AppIcon.appiconset/image_76540ca4.png`), not redrawn:

```
python3 tools/make-icons.py      # re-run when the iOS artwork changes
```

It emits both halves of a modern Android icon:

- `drawable-<density>/ic_launcher_foreground.png` — the artwork pre-scaled to
  92% of the 108dp adaptive canvas and centred. Only the centre 72/108 (66.7%) is
  guaranteed visible, so staying under ~0.92 keeps the whole box inside whatever
  mask the launcher applies (circle, squircle, rounded square, …).
- `mipmap-<density>/ic_launcher[_round].png` — full-bleed legacy icons with the
  iOS squircle corner radius, for launchers that predate adaptive icons.
- `mipmap-anydpi-v26/ic_launcher{,_round}.xml` — the `<adaptive-icon>` tying the
  foreground to `@color/ic_launcher_background`, which is the artwork's own
  background colour sampled from the source so the padding blends seamlessly.

The generator is pure stdlib (zlib + struct) — no Pillow, no ImageMagick, no
window server — because it has to run anywhere the build does.

To adjust how much breathing room the artwork gets inside the mask, change
`FOREGROUND_FRACTION` in `tools/make-icons.py` and re-run it.

### Login-screen mark

The login screen also uses the artwork, with its backdrop keyed out
(`drawable/login_mark.png`, generated by the same script). Two details make that
non-trivial and are worth knowing before touching it:

- The key is a **flood fill from the image border, not a global colour match**.
  The illustration's parchment and muted greys are chromatically close to the
  cream backdrop, so a global key punches holes in the box; a flood fill stops
  at the silhouette.
- The drop shadow is **re-authored as neutral black at real alpha**, not keyed.
  The artwork fakes the shadow by darkening the backdrop, so those pixels are
  cream and opaque. Leaving them makes the mark glow on a dark surface; keying
  them with a threshold clears the shadow's dark core but keeps its lighter rim,
  which turns a shadow into a halo. Estimating per-channel coverage and
  un-multiplying against the backdrop gives a shadow that behaves correctly on
  either theme.

`KEY_THRESHOLD`, `SHADOW_MAX_DIFF` and `SHADOW_MAX_SPREAD` in
`tools/make-icons.py` control this; each is set from measurements recorded in the
comments there.

## Configuration

The API base URL is a Gradle `BuildConfig` field, the Android equivalent of the
iOS project's `Configs/*.xcconfig`. Resolution order:

1. `-PAPI_BASE_URL=https://…` on the Gradle command line
2. `API_BASE_URL` in `local.properties`
3. `https://api.chill.ws` (the value both iOS xcconfigs use)

`local.properties` also holds `sdk.dir`. It is gitignored — recreate it with:

```properties
sdk.dir=/path/to/Android/sdk
API_BASE_URL=https://api.chill.ws
```

iOS `fatalError`s when the base URL is unset; Android has no equivalent
off-switch, so a blank value falls back to the default instead of crashing.

## Setup

1. Install a JDK (17 or 21) and the Android SDK (`platforms;android-35`,
   `build-tools;35.0.0`, `platform-tools`). Android Studio's SDK manager or
   `sdkmanager` both work.
2. Point `sdk.dir` in `local.properties` at that SDK.
3. Build and install:
   ```
   ./gradlew :app:assembleDebug
   ./gradlew :app:installDebug
   ```
4. Run the unit tests:
   ```
   ./gradlew :app:testDebugUnitTest
   ```

`minSdk` is 26 and `targetSdk`/`compileSdk` are 35. iOS 17 is the iOS
deployment target, so both sit on a modern baseline.

## Running on an emulator

`./emulator.sh` creates the AVD if needed, boots it, builds, installs, and
launches the app:

```
./emulator.sh                # boot windowed, install, launch
./emulator.sh --no-install   # just boot
./emulator.sh --headless     # no window, for scripted checks
./emulator.sh --wipe         # discard saved state first
```

Everything it needs lives inside this directory — `.android-sdk/` (platform,
build-tools, emulator, system image) and `.android-home/` (AVD and emulator
state) — so it works even when `$HOME` is not writable. Both are gitignored.

If `adb` is not on your PATH, use it directly:

```
.android-sdk/platform-tools/adb shell am start -n ws.chill.gamecheckout/.MainActivity
.android-sdk/platform-tools/adb exec-out screencap -p > /tmp/screen.png
.android-sdk/platform-tools/adb emu kill
```

Two environment quirks this setup works around, in case you set up your own SDK:

- The AVD is written directly as `.ini` + `config.ini` rather than via
  `avdmanager`, because `avdmanager` rejects a hand-extracted system image
  (it wants a populated SDK repo cache). The two files are exactly what
  `avdmanager` would produce.
- The emulator aborts with `Failed to create jwk directory ...` on macOS when
  `~/Library/Caches/TemporaryItems` is not writable. It ignores `TMPDIR`, so
  grant it access to that path if you hit this.

### Building without a writable `~/.android`

AGP keeps its auto-generated debug signing key in `~/.android/debug.keystore`,
which is created on first use. On a normal machine you can ignore this. In a
locked-down or sandboxed environment that cannot write there, `assembleDebug`
fails at `validateSigningDebug`; generate a project-local key instead:

```
keytool -genkeypair -keystore app/debug/debug.keystore \
  -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

`app/build.gradle.kts` picks it up automatically when it exists and falls back
to the standard location otherwise. The file is gitignored — never commit a
signing key.

### About `gradlew`

This project's `gradlew` is a small launcher, not the usual generated wrapper —
there is no `gradle-wrapper.jar` in the tree. It looks for Gradle 8.13+ via
`$GRADLE_HOME`, an existing wrapper-cache distribution, or `gradle` on `$PATH`,
and defaults `GRADLE_USER_HOME` to `.gradle-home/` inside the project.

## Structure

```
app/src/main/java/ws/chill/gamecheckout/
├── GameCheckoutApp.kt        GameCheckoutApp.swift — dependency graph + launch hook
├── MainActivity.kt           GameCheckoutApp.body — login gate, then nav host
├── analytics/Tracker.kt      Analytics/Tracker.swift — same events via AEP Edge
├── auth/
│   ├── AuthManager.kt        Auth/AuthManager.swift — hash, validate, session expiry
│   ├── AuthSession.kt        the slice of auth the network layer needs
│   └── SecureStore.kt        Auth/KeychainStore.swift — Keystore-backed AES-GCM
├── data/Game.kt              Models/Game.swift — Game, BorrowedInfo, BorrowedEntry
├── di/AppContainer.kt        the single instances iOS holds as @StateObject
├── network/
│   ├── ApiClient.kt          Networking/APIClient.swift — catalog + borrow/return
│   ├── ApiException.kt       enum APIError
│   └── Config.kt             Networking/Config.swift — endpoints
├── profile/
│   ├── BorrowerProfile.kt    interface, so the repository stays Context-free
│   └── BorrowerProfileStore.kt  BorrowerProfileStore.swift
├── repository/GameRepository.kt  the load/merge/borrow/return half of GameListViewModel.swift
├── ui/
│   ├── GameDates.kt          GameRowView.formattedDate — yyyy-MM-dd → MMM d
│   ├── components/
│   │   ├── BorrowReturnSheet.kt  Views/BorrowReturnSheet.swift
│   │   └── GameRow.kt            Views/GameRowView.swift
│   ├── screens/
│   │   ├── AdminScreen.kt        Views/AdminView.swift
│   │   ├── GameListScreen.kt     Views/GameListView.swift
│   │   └── LoginScreen.kt        Views/LoginView.swift
│   └── theme/Theme.kt
└── viewmodel/GameListViewModel.kt  GameListViewModel.swift — search + borrowed ordering
```

Tests mirror the two pieces of logic worth pinning down:

- `GameSearchTest` — the three-field case-insensitive search.
- `GameRepositoryTest` — the catalog/borrowed merge, the graceful degradation
  when the borrowed request fails, the sign-out on a rejected credential, and
  local updates after borrow/return.
- `GameDatesTest` — the `yyyy-MM-dd` → `MMM d` formatting.

## Navigating the code from iOS

If you know the Swift file, the Kotlin file has the same name and the same
public shape — `GameListViewModel` still exposes `filteredGames`,
`borrowedGames`, `searchText`, `isLoading`, and `errorMessage`; the differences
are that `@Published` becomes `StateFlow`, `@StateObject` becomes a process-wide
`AppContainer`, and the SwiftUI views become composables.
