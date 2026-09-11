# Game Checkout (iOS)

Native SwiftUI port of the web app: browse the game catalog, search,
borrow/return a game, and see everything currently borrowed.

Not included (by design, for this first pass): analytics.

## Auth

The web app gates two things behind one shared password: the site itself
(a `CFP-Auth-Key` cookie checked by `functions/_middleware.ts`) and the API
(an `x-cfp` header, whose value is `sha256(password)`, checked by the
`api.chill.ws` backend). This app only needs the second one.

On first launch you're asked for the password. The app hashes it locally with
`CryptoKit`, stores only the hash in the Keychain (never the raw password) for
about a year, and attaches it as the `x-cfp` header on every `/games/*`
request. If the backend ever responds 401/403, the app clears the stored hash
and drops back to the login screen. There's also a manual "Sign Out" button
in the game list's toolbar.

The password itself isn't in this repo — see `AuthManager.swift` /
`LoginView.swift` if you need to change the hint text or expiry.

## Setup

1. Install [XcodeGen](https://github.com/yonaskolb/XcodeGen) if you don't have it:
   ```
   brew install xcodegen
   ```
2. Set your backend URL in **both** `Configs/Debug.xcconfig` and
   `Configs/Release.xcconfig` (same value as the web app's
   `REACT_APP_API_ENDPOINT`). Keep the `$()` escapes around `//` — that's a
   required xcconfig quirk, not a typo.
3. Generate the Xcode project and open it:
   ```
   xcodegen generate
   open GameCheckout.xcodeproj
   ```
4. Run on any iOS 17+ simulator or device.

## Running on a simulator from the command line

`./run-simulator.sh` generates the project if needed, builds, installs, launches,
and brings the Simulator window to the front:

```
./run-simulator.sh                        # iPhone 15 Pro, the default
./run-simulator.sh --device "iPhone 17"   # a different simulator
./run-simulator.sh --list                 # what's available
./run-simulator.sh --no-build             # reinstall the last build
```

The device name is resolved to a UDID first: simulator names repeat across
runtimes (there are several "iPhone 17"s here), and `-destination "name=…"` is
ambiguous when they do.

Build output goes to `.ios-build/` in this directory (gitignored), so it doesn't
collide with the `build/` directory Xcode itself uses.

Two things worth knowing if you build by hand:

- Build for `-destination "id=<UDID>"`, not by name, for the reason above.
- SwiftPM keeps its manifest cache in `~/Library/Caches/org.swift.swiftpm` and
  has no flag to relocate it (`-clonedSourcePackagesDirPath` moves the checkouts,
  not this cache). Where that path isn't writable, package resolution fails with
  `cannot open file '.../ManifestLoading/<pkg>.dia' (Operation not permitted)`,
  and the build cannot proceed until it is.

## Structure

- `GameCheckout/Models/Game.swift` — `Game`, `BorrowedInfo`, and the
  `/games/borrowed` response shape.
- `GameCheckout/Auth/` — `AuthManager` (hashes + stores the password,
  publishes `isAuthenticated`) and `KeychainStore` (thin Keychain wrapper).
- `GameCheckout/Networking/` — `Config` (endpoints) and `APIClient`
  (catalog fetch + borrow/return calls, all `x-cfp`-authenticated except the
  public catalog fetch).
- `GameCheckout/GameListViewModel.swift` — loads/merges catalog + borrowed
  state, drives search filtering, forces re-login on 401/403, and exposes
  `borrowedGames` (filtered + sorted by borrow date) for the admin screen.
- `GameCheckout/Views/` — `LoginView` (password entry), `GameListView`
  (list + search, with a toolbar link to `AdminView`), `GameRowView` (row
  layout, mirrors the strikethrough/blue "borrowed" styling from the web
  app), `BorrowReturnSheet` (the borrow/return form), `AdminView` (the
  `/borrowed` equivalent — game, borrower, email, date for everything
  currently checked out; reuses the list's already-loaded data instead of
  a second network round-trip).

The game catalog is fetched directly from `https://public.chill.ws/games.json`,
same as the web app (no auth needed — it's public). Borrow/return state comes
from your configured API's `GET /games/borrowed`, `PUT /games/borrow`, and
`PUT /games/return`.
