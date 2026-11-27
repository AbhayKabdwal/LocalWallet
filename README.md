# LocalWallet

A money tracker for Android that keeps your financial records on your phone and nowhere else.

LocalWallet is a fork of [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet).

No account. No sync server. No analytics. The app does not declare the `INTERNET` permission at
all, so it cannot send your data anywhere even if it wanted to — you can verify that yourself in
one command, and the instructions are below.

<!-- Screenshots go here once you have a device to capture them on. -->

## Why

Most expense trackers ask you to hand your transaction history to a company. That history says
where you go, what you buy, when you travel, what you are treating, and who you support. It is
some of the most revealing data you produce.

LocalWallet takes the opposite position: the data never leaves the device, and the code is public
so you can check that claim rather than trust it.

## Why you might choose it

- **Your data stays on your phone.** Not "encrypted in transit", not "anonymised" — it never
  leaves. The app has no `INTERNET` permission, so this is a property of the build, not a promise.
- **It fills itself in.** Your bank already tells you about every payment. LocalWallet reads those
  alerts on-device and offers each as a transaction to accept or skip, so the ledger stays current
  without you typing it.
- **It handles your bank, not just the popular ones.** If your bank's wording is not recognised,
  you show the app one real message, confirm the parts it marked, and it reads the rest from then
  on. No waiting for a release.
- **It tells you where the money went.** Savings rate, spend per day, biggest expense, and the few
  categories that account for most of your spending.
- **You choose how it looks.** Accent, income and expense colours by hex or preset, light / dark /
  AMOLED, and a depth control. Every colour is contrast-checked so you cannot end up with text you
  can't read.
- **No account, no subscription, no ads, no upsell.** GPL-3.0, and the whole thing is in this repo.

## Why you might not

These are real, and you should weigh them before switching.

- **It asks for access that looks alarming, and you have to decide if you believe us.** To read
  bank alerts it needs *notification access*, which lets it see notifications from every app.
  Optionally — only if you want to import transactions from *before* you installed it — it needs
  `READ_SMS`, which Android classifies as restricted and which grants access to your whole inbox.
  Those are broad permissions and Android is right to make them sound serious.

  What the app actually does with them: it matches each message against bank patterns on the
  device, shows you anything that looks like a transaction, and throws the text away as soon as
  you decide. Nothing is uploaded, because there is nothing to upload *to* — the app has no
  network permission at all. You do not have to take that on faith:

  ```bash
  aapt dump permissions LocalWallet-*.apk | grep -i internet   # expect no output
  ```

  Both are optional. Skip them and the app is an ordinary manual expense tracker.

- **There is no sync and no cloud backup.** One phone, one copy. Lose the phone without an export
  and the data is gone. Export to CSV regularly — the app will not do it for you.
- **Android only.** No iOS, no web, no desktop, and no plans for any.
- **No Play Store, so no automatic updates.** You sideload the APK and check back for releases
  yourself.
- **The message parser is not magic.** It is tuned to refuse anything ambiguous, which means it
  will sometimes miss a transaction rather than guess. Notification reading also cannot see
  anything that arrived before you switched it on.
- **One maintainer, on a fork of a discontinued app.** Ivy Apps stopped work on the original in
  November 2024. If that matters for something you rely on daily, it should factor in.

## What it does

**Tracks money, properly.** Accounts in multiple currencies, categories, budgets, planned and
recurring payments, loans, transfers, search, statistics and pie charts, CSV import and export,
home-screen widgets, and an optional biometric lock.

**Looks how you want.** Pick your own accent, income and expense colours by hex or from eight
presets. Light, dark, AMOLED or follow-system. Material You on Android 12+. A *Depth* control sets
how much coloured glow and gradient the interface uses — from flat to vivid — without changing
your colours. Palettes export to a short string you can share.

Every colour you choose is contrast-checked against the surface it lands on, so a pale accent
cannot leave you with text you can't read.

**Reads your bank alerts, on the device.** Optional and off by default, from either of two
sources. *Notification access* is the recommended one: it picks up bank alerts as they arrive,
including from bank and UPI apps, and needs none of Android's restricted permissions. *SMS access*
is the other, and the only way to import messages that arrived before you installed the app.

Either way the message is parsed locally and shown as a card you accept, edit or skip. The app
learns which account a card number belongs to and which category a merchant goes in, so reviewing
gets faster each week. The same transaction arriving by both routes is recognised and offered
once.

**Learns your bank's wording.** If a format is not recognised, open *Settings → Message formats →
Teach a new format*, pick one of the messages it could not read, and confirm the parts it marked
as the amount, account and balance. It reads that format from then on.

**Checks the app against your bank.** Most bank messages state a balance. When your review queue
is empty, LocalWallet compares that figure with what its own transactions add up to and offers to
write the difference off as a single, visible, deletable adjustment.

**Saves for several things at once.** Savings goals in the home menu — a phone, a monitor — each
with its own target and progress, instead of one lump figure for everything.

**Reminds you about planned payments.** A notification on the morning a planned payment is due,
so the reminder reaches you rather than waiting inside the app.

It is deliberately cautious about what it treats as a transaction. One-time passwords, promotions,
balance notifications, bill reminders, failed and reversed payments, and UPI collect requests are
all rejected before parsing. A missed message is an annoyance you fix by hand; an invented one
quietly corrupts your balance, so the parser is tuned for precision and most of its test suite is
cases it must *refuse*.

Nothing is ever added without you confirming it, and the original message is one tap away on every
card so you can check the parser got it right.

## Privacy, concretely

| | |
|---|---|
| Network permissions | none — no `INTERNET`, no `ACCESS_NETWORK_STATE` |
| Analytics / crash reporting | none |
| Accounts or cloud sync | none |
| Identifiers | none generated or stored |
| Where data lives | a database in the app's private storage |
| Notification access | optional, off by default, read-only, never uploaded |
| SMS access | optional, off by default, read-only, never uploaded |

Verify the network claim on the APK you are about to install:

```bash
aapt dump permissions LocalWallet-*.apk | grep -i internet   # expect no output
```

Message text is kept only while an item waits for your review. Once you decide, the text is
deleted — the permanent log keeps a message id, a fingerprint and the outcome, never the body.
Full detail in [PRIVACY.md](PRIVACY.md).

## Install

Grab the APK from [Releases](https://github.com/AbhayKabdwal/LocalWallet/releases) and open it on
your phone. Android will ask you to allow installs from this source.

There is no Play Store listing. Optional SMS import needs `READ_SMS`, which Play only permits
under a reviewed exception, and the point of this app is to not depend on that. Reading bank
alerts through notification access carries no such restriction, which is why it is the default.

## Importing your data

LocalWallet reads CSV exports from Ivy Wallet, Money Manager, Wallet by BudgetBakers, Spendee,
Monefy, 1Money, Bluecoins, KTW Money Manager, Fortune City and Financisto.

**Settings → Import data**, pick the app you are coming from, and choose the file. Accounts and
categories named in the file are created as needed, and transfers are linked to both sides.

## Building

JDK 17+ and the Android SDK.

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew test                        # unit tests
./gradlew detekt                      # lint
```

The SMS parser lives in `shared/sms-parsing` as a plain Kotlin module with no Android or I/O
dependencies, so its tests run in seconds. Bank patterns are **data**, not code — see
[`SmsRules.kt`](shared/sms-parsing/src/main/kotlin/com/ivy/sms/SmsRules.kt). Adding support for a
bank means adding a rule and a test case, which is the cheapest kind of contribution to review.

## Contributing

Bank coverage is the most useful thing to contribute, and the easiest: add a rule plus a test.

**Never commit a real bank message.** Redact account numbers to `XX1234` and replace merchant
names before adding a test case — a real SMS in a public repository is a data leak with your name
on the commit.

## Licence and credit

GPL-3.0. See [LICENSE](LICENSE).

LocalWallet began as a fork of [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet) by Ivy Apps
Ltd., which its authors discontinued in November 2024. The tracking, the Google dependencies and
the network calls were removed; the theming engine and the on-device message import were added.
The great majority of the underlying codebase is still the original authors' work and the credit
for it is theirs. [NOTICE.md](NOTICE.md) records exactly what changed.
