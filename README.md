# FocusLock

A minimalist Android app that blocks distracting apps while you study.

Pick the apps you want to block (Instagram, YouTube, WhatsApp, …), choose a duration, and tap **Start Focus**. If you try to open a blocked app, FocusLock covers it with a **Stay Focused** screen until your timer ends.

Built with Kotlin and Jetpack Compose. Dark, quiet, no ads, no accounts, no internet access.

> **Status:** personal project, built for my own phone. It is not on the Play Store and has not been tested on a wide range of devices. Issues and pull requests are welcome.

---

## Features

| Feature | What it does |
|---|---|
| **App selection** | Choose any installed apps to block, with search. |
| **One-tap focus** | A single large button starts and stops a session. |
| **Timer** | Presets of 25, 45, 60, 90 and 120 minutes, or a custom duration. |
| **App blocking** | Opening a blocked app shows a *Stay Focused* screen with the time left. |
| **Automatic unlock** | Apps unblock by themselves when the timer reaches zero. |
| **Strict mode** | While focusing, you can't stop the timer or change blocked apps or schedules. |
| **Scheduled blocking** | Block automatically on chosen days and hours (12-hour AM/PM, overnight windows supported). |
| **Schedule reminder** | A notification 10 minutes before a scheduled study window starts. |
| **Emergency break** | A 2, 3 or 5 minute break, followed by a 30-minute cooldown. |
| **Focus statistics** | Daily focus time, completed sessions, distractions blocked, and a 7-day chart. |
| **Study goal** | Set a daily target and see your progress on the home screen. |

---

## How blocking works

FocusLock does **not** use an Accessibility service.

1. When a session (or scheduled window) is active, a lightweight **foreground service** runs.
2. About once per second it asks Android's `UsageStatsManager` for new "app moved to foreground" events.
3. If the newest foreground app is one you selected, it launches `BlockActivity`, the *Stay Focused* screen.
4. The service stops itself the moment nothing is blocking, so it costs nothing outside focus time.

The session is stored as an absolute end time, so it survives the app being closed and phone restarts. A short **watchdog alarm** restarts the service if it is killed while blocking is active (for example by tapping *Stop* in Android's "Active apps" list).

Scheduled windows use exact alarms to start, end and trigger the 10-minute reminder.

### Why not AccessibilityService?

On recent Android versions, apps installed from a downloaded APK are heavily restricted from using Accessibility, and Google Play Protect may block them. Usage Access plus overlay permission avoids both problems.

---

## Permissions

| Permission | Why it is needed |
|---|---|
| **Usage access** (`PACKAGE_USAGE_STATS`) | Detect which app is open during focus time. Granted by you in Settings. |
| **Display over other apps** (`SYSTEM_ALERT_WINDOW`) | Allow the Stay Focused screen to appear on top of a blocked app. Granted by you in Settings. |
| **Notifications** (`POST_NOTIFICATIONS`) | Show the "focus is on" notification and the schedule reminder. |
| **Foreground service** (`FOREGROUND_SERVICE`, `..._SPECIAL_USE`) | Keep blocking reliable while a session is running. |
| **Alarms & reminders** (`SCHEDULE_EXACT_ALARM`) | Start scheduled blocking and the 10-minute reminder on time, and restart the service if it is killed. Without it, schedules still work but may run a few minutes late. |
| **Run at startup** (`RECEIVE_BOOT_COMPLETED`) | Resume blocking and restore schedules after a reboot. |

## Privacy

Everything stays on your phone. FocusLock has no internet permission, no analytics, no ads and no account. Settings and statistics are stored locally in app preferences. Foreground-app detection only runs during focus time.

---

## Install

### Option 1: build the APK with GitHub Actions (no tools needed on your PC)

1. Fork or clone this repo into your own GitHub account.
2. Open the **Actions** tab, select **Build APK**, and click **Run workflow** (it also runs on every push).
3. When the run is green, open it and download **FocusLock-apk** (an installable APK in a zip) or **FocusLock-aab-for-Play** (for Google Play).
4. Copy the APK to your phone and install it.

### Option 2: build with Android Studio

1. Install Android Studio and open this folder. Let Gradle sync.
2. Connect your phone with USB debugging enabled and press **Run**.

Or from a terminal with Gradle 8.11 and JDK 17 installed:

```bash
gradle assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
gradle bundleRelease
# AAB for Google Play: app/build/outputs/bundle/release/app-release.aab
```

By default the release build is signed with the debug key so it installs directly on your own phone. To sign with your own upload key for Google Play, see `docs/PLAY_STORE_CHECKLIST.md`.

### First-run setup on the phone

1. Open FocusLock, choose the apps to block, then tap **Grant access**.
2. Turn on **Usage access** for FocusLock, then come back and tap again.
3. Turn on **Allow display over other apps** for FocusLock.
4. Allow notifications when asked.
5. For reliability, set FocusLock's **Battery** to **Unrestricted**. On Xiaomi, Realme, Oppo and Vivo phones, also enable **Autostart**.

### Installing problems

- **"App blocked to protect your device" (Play Protect):** tap *More details > Install anyway*. If that option is missing, temporarily turn off *Scan apps with Play Protect* in the Play Store, install, then turn it back on.
- **"App not installed" when updating:** each CI build is signed with a different debug key. Uninstall the old version first. Your settings and stats will reset. To avoid this, sign releases with your own keystore.

---

## Usage tips

- **Strict mode** can only be switched while nothing is blocking. Turn it on before you start.
- **Emergency break** is the one allowed exit in strict mode. The session timer keeps running during a break, and blocking resumes automatically when it ends.
- **Focus time** counts the whole session, with the screen on or off, minus emergency breaks. Completed sessions count only timer sessions that ran to zero.
- **Scheduled study time is always locked:** while a schedule window is running you can't disable, edit or delete it, and you can add blocked apps but not remove them. The emergency break is the only way out. A manual timer session is locked only when strict mode is on.
- Schedules and sessions can overlap. A running timer session takes priority on screen, but a running schedule keeps blocking even if you stop the timer.

---

## Known limitations

- Blocking is polled about once a second, so a blocked app can be visible for up to roughly a second before the screen covers it.
- FocusLock cannot stop you from **force-stopping** or **uninstalling** it, or from revoking its permissions in Settings. Force stop also cancels its alarms until you open the app again.
- Tapping *Stop* in Android's "Active apps" list kills the app. The watchdog restarts blocking within about 15 seconds.
- Aggressive battery managers on some phones can stop background work. Set battery to Unrestricted and enable Autostart where available.
- Changing the system clock can interfere with timers and schedules.
- Only tested on one personal phone so far.

---

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- `UsageStatsManager`, foreground service, `AlarmManager`
- `SharedPreferences` for local storage (no database, no network)
- Min SDK 26 (Android 8.0), target SDK 36
- Gradle 8.11, Android Gradle Plugin 8.9, Kotlin 2.0

## Project structure

```
app/src/main/java/com/focuslock/
├── MainActivity.kt         # navigation between screens
├── HomeScreen.kt           # timer ring, start/stop, strict mode, apps row
├── AppsScreen.kt           # app picker with search
├── ScheduleScreen.kt       # study schedules
├── StatsScreen.kt          # statistics, 7-day chart, daily goal
├── BlockActivity.kt        # the "Stay Focused" screen
├── BreakControls.kt        # emergency break UI
├── BlockerService.kt       # foreground service that detects blocked apps
├── Rules.kt                # schedules, what is blocking right now, alarms, reminders
├── FocusStore.kt           # persistence, statistics, break and strict state
├── BootReceiver.kt         # restores state after reboot
├── Permissions.kt          # permission checks
└── Theme.kt                # colours and theme
.github/workflows/build.yml # builds the APK and AAB on GitHub Actions
docs/privacy-policy.md      # privacy policy (publish with GitHub Pages)
docs/PLAY_STORE_CHECKLIST.md # steps and copy for a Google Play release
```

## Contributing

Pull requests and issues are welcome. Ideas: a launcher widget, per-schedule app lists, a weekly summary, and a hold-to-stop option for non-strict mode.
