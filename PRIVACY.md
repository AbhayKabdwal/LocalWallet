# Privacy

Last updated: 21 November 2026

This app stores your financial data on your device and nowhere else. There is no account to create,
no server to sync with, and no analytics.

## What is collected

Nothing. There is no telemetry, no crash reporting, no advertising or attribution SDK, and no
unique identifier assigned to you or your device.

## What leaves your device

Nothing. The app does not declare the `INTERNET` permission, so it cannot make network requests
even in principle. You can confirm this yourself:

```bash
./gradlew :app:assembleRelease
aapt dump permissions app/build/outputs/apk/release/app-release.apk
```

The fork it is based on did contact Google (Firebase Crashlytics and Firestore) and three external
HTTP endpoints. All of that was removed — see the table in the [README](README.md#what-was-removed).

## Where your data lives

- Transactions, accounts, categories, budgets and loans are in a local database in the app's
  private storage.
- Preferences, including your theme, are in the app's private preference store.
- Backups and CSV exports go only where you tell them to. A backup file contains your financial
  data in readable form, so treat it as you would a bank statement.

Uninstalling the app deletes all of it. Nothing is retained anywhere else, because there is nowhere
else.

## Reading your messages (optional)

The app can read bank SMS already on your phone and suggest transactions from them. It is **off by
default**.

- The `READ_SMS` permission is requested only if you enable the feature, and only when you open the
  review screen. If you never enable it, you are never asked.
- Messages are read and parsed entirely on the device. Nothing is uploaded; the app has no network
  access.
- Parsing is read-only. The app never sends, writes, modifies or deletes a message.
- Message text is kept only while an item is waiting for your review. Once you accept, skip or
  ignore it, the text is deleted. The permanent import log keeps only a message id, a fingerprint
  of the transaction and the outcome — never the message body.
- One-time-password messages are rejected before parsing and are never turned into transactions.
- Nothing is added to your accounts without you confirming it.
- You can clear everything the feature has learned, and turn it off, in its settings. Turning it
  off stops all scanning.

Revoking the SMS permission in Android settings also disables the feature; the rest of the app
continues to work normally.

## Permissions

| Permission | Used for |
|---|---|
| `READ_SMS` | Optional message import, described above. Only requested if you enable it. |
| `POST_NOTIFICATIONS` | Transaction reminders you schedule |
| `RECEIVE_BOOT_COMPLETED` | Re-registering those reminders after a restart |
| `SET_ALARM`, `WAKE_LOCK`, `VIBRATE` | Reminder delivery |
| `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` (API 28) | CSV import/export and backups you initiate |

The app does **not** request internet, network state, location, contacts, camera or microphone
access.

## Changes

This file is versioned in the repository, so any change to it is visible in the commit history.
