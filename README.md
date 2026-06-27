# Internet Status

A lightweight Android app that monitors your real internet connectivity and shows a persistent status indicator in your notification panel — a simple green or red dot that updates automatically.

Built for Android 6.0+ with AMOLED display support in mind.

---

## Features

- **Real connectivity check** — pings `8.8.8.8` to verify actual internet access, not just whether a network is connected
- **Persistent notification** — minimal notification showing `Internet: Connected ✓` or `Internet: No Access ✗`
- **Auto-updates** — switches between green and red dot instantly when your connection changes
- **Starts on boot** — service launches automatically when the device restarts, no need to open the app
- **Three themes** — Light, True Black AMOLED (`#000000`), and Dark Grey (`#121212`), with your choice remembered across restarts
- **Battery efficient** — pauses pinging when the screen is off, resumes immediately when it turns back on
- **Tiny footprint** — minimal RAM and storage usage, no unnecessary libraries

---

## Screenshots

> Coming soon

---

## Installation

### Download APK (easiest)

1. Go to the [**Actions**](../../actions) tab
2. Open the latest successful **Build APK** workflow run
3. Download the `internet-status-apk` artifact
4. Extract the zip and install `app-debug.apk` on your device
5. Enable **Install from unknown sources** in your device settings if prompted

### Build from Source

Requirements: Android Studio, JDK 17+

```bash
git clone https://github.com/dearprince147-ops/internet-status.git
cd internet-status
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`

---

## How to Use

1. Open the app
2. Grant the requested permissions (notifications, network access)
3. Tap **Activate** — the notification dot appears in your panel
4. The app runs in the background from now on
5. Toggle the theme using the icon in the top-right corner

---

## Permissions

| Permission | Reason |
|---|---|
| `INTERNET` | Pinging to verify real internet access |
| `ACCESS_NETWORK_STATE` | Detecting network connect/disconnect events |
| `FOREGROUND_SERVICE` | Keeping the monitor alive in the background |
| `RECEIVE_BOOT_COMPLETED` | Auto-starting after device reboot |
| `POST_NOTIFICATIONS` | Showing the status dot in the notification panel |

---

## Tech Stack

- **Language** — Kotlin + Java
- **UI** — Jetpack Compose
- **Min SDK** — 24 (Android 7.0)
- **Target SDK** — 35
- **Build** — Gradle 9.3.1, AGP 9.1.1

---

## Project Structure

```
app/src/main/java/com/example/
├── MainActivity.kt          — Main UI, theme toggle, activate button
├── InternetStatusService.java — Foreground service, ping loop, notification
└── BootReceiver.java        — Auto-start on device boot
```

---

## Contributing

This is a personal utility app but PRs and issues are welcome. Keep it minimal — the goal is a small, efficient, no-nonsense tool.

---

## License

MIT — do whatever you want with it.
