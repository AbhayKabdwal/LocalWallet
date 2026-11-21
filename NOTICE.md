# Notice

This project is a modified version of [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet) by
Ivy Apps Ltd. and its contributors, which the original authors discontinued on 5 November 2024.

Forked on 21 November 2026 from commit `eb08c930c` (version 2026.10.22, version code 207).

Licensed under the GNU General Public License v3.0, as is the original. The great majority of this
codebase is the original authors' work, and the in-app contributors credit and attributions screens
were kept for that reason.

## Modifications

Changes made in this fork, beginning 21 November 2026:

- Removed Firebase Crashlytics and the Google Services Gradle plugin, and deleted the committed
  `google-services.json` belonging to the upstream project.
- Removed the Firebase Firestore-backed in-app poll (`:feature:poll`) and the persistent device
  identifier it reported.
- Removed the Google Play Core in-app review and in-app update integrations and the Play Services
  dependencies.
- Removed the Contributors and Releases screens, which queried the GitHub API at runtime.
- Removed the remote exchange-rate data source; rates are now entered manually.
- Removed Ktor and the `INTERNET` and `ACCESS_NETWORK_STATE` permissions. The app makes no network
  requests.
- Removed the unused `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` permissions and
  `usesCleartextTraffic`.
- Added a user-customisable theming engine: palettes, presets, Material You support, a configurable
  glow/emphasis level, and palette export/import. Both the Material3 and legacy colour systems are
  driven from it.
- Added optional on-device import of transactions from bank SMS, off by default.
- Removed the GitHub repository card from the home quick-access menu.
