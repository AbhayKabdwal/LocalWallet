# Ivy Wallet (local-only fork)

A manual expense tracker for Android, written in Kotlin and Jetpack Compose.

This is a fork of [Ivy-Apps/ivy-wallet](https://github.com/Ivy-Apps/ivy-wallet), which the original
authors stopped maintaining on 5 November 2024. It keeps what made the app good and changes three
things:

- **No trackers and no servers.** Firebase Crashlytics, Firebase Firestore and the Google Play
  Services libraries are gone, along with every network call. The app does not declare the
  `INTERNET` permission at all.
- **A fully customisable colour scheme,** including how much visual "glow" the UI uses.
- **Optional on-device import from bank SMS,** so routine transactions do not have to be typed in
  by hand.

Everything the app does happens on your phone.

## What was removed

| Removed | Why |
|---|---|
| Firebase Crashlytics | Auto-initialised with no consent gate or opt-out; sent crash data, device metadata and a persistent install ID to Google |
| Firebase Firestore (in-app poll) | Wrote a permanent per-device UUID into the upstream maintainers' Firebase project |
| `app/google-services.json` | Upstream's Firebase project credentials, committed to the repo |
| Play Core review + in-app update | Proprietary Google libraries; fail on de-Googled devices |
| Contributors & Releases screens | Called the GitHub API at runtime |
| Remote exchange-rate fetch | Called an external currency API |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Nothing left to talk to |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Declared upstream but never used by a single line of code |
| `usesCleartextTraffic="true"` | Allowed plaintext HTTP app-wide for no reason |

Exchange rates are now entered and edited by hand on the Exchange Rates screen, which upstream
already supported.

## Appearance

**Settings → Appearance.**

- **Theme** — light, dark, AMOLED or follow system.
- **Colours** — primary, secondary, income and expense, each as a hex value or from a preset.
  Eight presets ship with the app.
- **Depth** — `Flat`, `Subtle` or `Vivid`. This controls the coloured glow behind cards and
  buttons, and how far gradients ramp. `Subtle` is the default; `Flat` removes the glow entirely.
  Accent hues are identical at all three levels, so turning the glow down does not change your
  colours.
- **Use wallpaper colours** — Material You, on Android 12+.
- **Export / import** — your palette as a short string, to share or re-apply.

The theme drives both of the app's colour systems at once. Most screens here still use the older
`UI.colors` layer rather than Material3, so a palette is resolved once and bridged into both; that
is why a custom accent shows up everywhere rather than on a handful of newer screens.

Colours you choose are contrast-checked against the surface they land on, so a very pale or very
dark accent cannot produce unreadable text.

## Import from messages

Off by default. Turn it on in **Settings → Features → Import from messages**, then open
**Settings → Import from messages**.

When the app opens it looks for bank messages that arrived since the last scan, parses them on the
device, and offers each one for review:

- Amount, account, category and title are pre-filled and editable.
- The original message is one tap away, so you can check the parser got it right.
- **Add**, **Skip**, **Ignore this sender** or **Not a transaction** — nothing is written to your
  accounts without you accepting it.
- The account behind a card number and the category for a merchant are remembered, so later
  reviews are mostly a sequence of taps.

### What it will not do

- **One-time passwords are never parsed.** OTP messages contain bank names, amounts and numbers,
  and they are the main source of false positives, so they are rejected outright and this is
  covered by tests.
- Promotional messages, balance notifications, scheduled-payment and bill-due notices, failed or
  reversed transactions and UPI collect requests are all rejected too.
- It never adds anything silently. Precision is the priority: a missed message is a minor
  annoyance you can fix by hand, while an invented transaction quietly corrupts your balance.

### Privacy

`READ_SMS` is requested only if you enable the feature, and only when you open the review screen.
Parsing is local, the app has no internet permission, and message text is deleted as soon as you
decide on an item — the import log keeps only a message id, a fingerprint and the outcome, never
the body. Message contents are never written to the log.

### Bank coverage

Patterns live in `shared/sms-parsing` as data, not code
([`SmsRules.kt`](shared/sms-parsing/src/main/kotlin/com/ivy/sms/SmsRules.kt)), with generic
fallbacks so an unlisted bank still works at lower confidence. Adding a bank means adding a rule
and a test case. If your bank is not recognised, that is the file to look at.

> **Note on Google Play.** Play restricts `READ_SMS` to a short list of permitted uses. Budget
> tracking is on that list — "SMS-based money management" — but it is a reviewed, revocable
> exception that needs a Permissions Declaration Form. This fork is built for F-Droid-style
> distribution and GitHub releases, where that does not apply. If you publish to Play, either file
> the declaration or build with the feature disabled.

## Building

Requires JDK 17+ and the Android SDK.

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :shared:sms-parsing:test    # SMS parser tests (pure JVM, fast)
./gradlew detekt                      # lint
```

The SMS parser is a plain Kotlin module with no Android or I/O dependencies, so its tests run in
seconds. That is where the bank-format logic lives and where changes should be tested.

## Licence

GPL-3.0, same as upstream — see [LICENSE](LICENSE).

This is a modified version of Ivy Wallet by Ivy Apps Ltd. and its contributors. See
[NOTICE.md](NOTICE.md) for what changed and when. Credit for the great majority of this codebase
belongs to the original authors.
