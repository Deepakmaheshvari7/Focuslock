# Google Play release checklist for FocusLock

## A. One-time setup

1. **Package name:** open `app/build.gradle.kts` and change `applicationId` (default `app.focuslock.study`) to your own unique id, for example `in.yourname.focuslock`. It can never be changed after the first upload.
2. **Privacy policy:** edit `docs/privacy-policy.md` (name, email, date). In GitHub: Settings > Pages > Deploy from a branch > `main` / `/docs`. Your policy URL becomes `https://YOUR-USERNAME.github.io/YOUR-REPO/privacy-policy`.
3. **Upload key (never commit it):** in your repo click Code > Codespaces > Create codespace, then in the terminal:
   ```bash
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 upload.jks
   ```
   - Download `upload.jks` from the file list (right-click > Download) and store it safely, with the passwords.
   - In GitHub: Settings > Secrets and variables > Actions, add `KEYSTORE_BASE64` (the long base64 text), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`upload`), `KEY_PASSWORD`.
   - Delete the codespace. Enable Play App Signing when asked in Play Console; if you ever lose the upload key, Google can reset it.

## B. Build

Push to GitHub, open Actions > Build APK, and download:
- `FocusLock-aab-for-Play`: the file you upload to Play Console (Play requires an AAB).
- `FocusLock-apk`: for installing on your own phone.

The version number increases automatically with every build.

## C. Play Console forms (App content)

- **Target API:** the project targets Android 16 (API 36), as Play currently requires.
- **Ads:** No ads.
- **App access:** all functionality available without login.
- **Target audience:** choose 13 and over (avoid selecting under-13 groups, which triggers the Families policy).
- **Content rating:** answer the questionnaire honestly (no violence, no user-generated content). Expected: Everyone.
- **Data safety:** "Does your app collect or share any of the required user data types?" > **No**. Paste your privacy policy URL.
- **Foreground service declaration** (type: Special use). Suggested text:
  > FocusLock blocks distracting apps during a focus session or scheduled study time that the user starts or configures. The service checks which app is in the foreground and shows a blocking screen when the user opens an app they chose to block. It must run while the app is in the background and stops itself when the session or schedule window ends. A persistent notification is shown while it runs.
  Video: start a session, open a blocked app, show the Stay Focused screen, show the notification.
- **Exact alarm declaration** (`SCHEDULE_EXACT_ALARM`). Suggested text:
  > Users create weekly study schedules (for example Mon-Fri 6:00-9:00 PM). The app must start app-blocking at the exact scheduled time and send a reminder 10 minutes before. Delays of several minutes would defeat the feature. Alarms are set only for schedules the user created.
  Video: create a schedule, show the reminder notification, show blocking starting on time.
  **If Play rejects it:** delete the `SCHEDULE_EXACT_ALARM` line in `AndroidManifest.xml` and resubmit. Schedules still work but may start a few minutes late.
- **Sensitive permission note:** Usage access and Display over other apps have no separate Play form. The in-app dialog before the settings screen is the required prominent disclosure. Keep that wording.

## D. Store listing (copy you can use)

- **App name (max 30):** FocusLock: Study Focus Timer
- **Short description (max 80):** Block distracting apps while you study. Timer, schedules, strict mode.
- **Full description:**

  FocusLock helps you study without distractions.

  Choose the apps that pull you away, set a timer, and tap one button. If you open a blocked app, FocusLock shows a calm Stay Focused screen until your session ends.

  • Focus timer: 25, 45, 60, 90, 120 minutes or your own duration
  • App blocking: pick any installed apps
  • Strict mode: no stopping the timer or changing apps mid-session
  • Study schedules: block automatically during your study hours, with a reminder 10 minutes before
  • Emergency break: a short 2, 3 or 5 minute break with a cooldown
  • Statistics: daily focus time, completed sessions, distractions blocked, 7-day chart
  • Daily study goal
  • Private by design: no ads, no accounts, no internet permission. Everything stays on your phone.

  Permissions: Usage access (to detect when a blocked app opens) and Display over other apps (to show the Stay Focused screen). Both are used only during focus time.

- **Do not** put other apps' brand names in the title or short description.
- **Assets:** 512x512 icon, 1024x500 feature graphic, at least 2 phone screenshots (home, stats, schedule, Stay Focused screen). Canva's free tier works.

## E. Testing and launch

1. Create the app in Play Console, upload the AAB to **Closed testing**, add 12+ testers (Google Group or email list), share the opt-in link.
2. Keep 12+ testers opted in for 14 days, then apply for production access.
3. Test on at least one Android 14+ phone: Stay Focused screen appears over a blocked app, reminders arrive, schedules start on time, emergency break works, blocking survives a restart.
4. After approval, promote to Production.
