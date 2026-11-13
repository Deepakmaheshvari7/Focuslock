# FocusLock testing checklist

For you (the developer) before the closed test, and to share with your testers.
Tick each box. For anything that fails, note the phone **brand, model and Android version**, what you did, what you expected, and what happened (a screenshot or screen recording helps).

---

## Part 1: Developer smoke test (about 15 minutes, do this first)

Use a real phone on Android 14 or newer, with FocusLock freshly installed.

- [ ] First launch asks to allow notifications (Android 13+). Allow.
- [ ] Tap **Grant access**: the disclosure dialog appears; **Agree** opens Usage access, then (after returning and tapping again) Display over other apps.
- [ ] Pick one app (for example a browser), set a **Custom 1 minute** timer, tap **Start Focus**.
- [ ] Open the blocked app: the **Stay Focused** screen covers it within about a second.
- [ ] **Back to studying** and the Back button both go to the home screen.
- [ ] After the minute ends, the blocked app opens normally and no "Focus mode is on" notification remains.
- [ ] Today's Stats shows 1 session completed and about 1 minute of focus.

If any of these fail, fix them before sending the app to testers.

---

## Part 2: Full test cases

### A. Setup and permissions
| # | Steps | Expected | Pass |
|---|---|---|---|
| A1 | Fresh install, open app | Notification permission prompt (Android 13+) | [ ] |
| A2 | Tap Grant access, Agree, enable Usage access, return | Tapping the button again goes on to Display over other apps | [ ] |
| A3 | Grant both, return to the app | Button changes to Start Focus (or Select apps if none chosen) | [ ] |
| A4 | Turn Usage access off in Settings, reopen the app | Button shows Grant access again | [ ] |

### B. Timer and blocking
| # | Steps | Expected | Pass |
|---|---|---|---|
| B1 | Choose each preset (25, 45, 60, 90, 120) | Ring shows the right time (for example 1:30:00) | [ ] |
| B2 | Custom: enter 7 | Chip shows 7m; ring shows 07:00 | [ ] |
| B3 | Start a session, open a blocked app | Stay Focused screen with the remaining time | [ ] |
| B4 | Open a non-blocked app | Opens normally | [ ] |
| B5 | Blocked app is already open, then start a session and switch to it | Stay Focused appears | [ ] |
| B6 | Open blocked app from Recents | Stay Focused appears | [ ] |
| B7 | Wait for the timer to reach 0 | Apps unblock by themselves; Stay Focused (if showing) closes | [ ] |
| B8 | Stop the timer early (strict mode off) | Session ends; apps unblock; not counted as a completed session | [ ] |
| B9 | Change blocked apps during a session (strict off) | Allowed and takes effect within a second or two | [ ] |

### C. Strict mode
| # | Steps | Expected | Pass |
|---|---|---|---|
| C1 | Turn strict mode on, start a session | Button shows STRICT MODE and is not tappable | [ ] |
| C2 | Try to turn strict mode off during the session | Switch is disabled | [ ] |
| C3 | Open Blocked apps during the session | You can add apps but not remove them | [ ] |
| C4 | Try to edit a schedule during the session | Everything is locked | [ ] |
| C5 | Session ends | Controls unlock again | [ ] |

### D. Schedules and reminders
| # | Steps | Expected | Pass |
|---|---|---|---|
| D1 | Add schedule | It appears **switched off** with 9:00 AM to 12:00 PM | [ ] |
| D2 | Change start and end | AM/PM picker; times show like 6:30 PM | [ ] |
| D3 | Set a window starting 3 minutes from now, turn it on | Blocking begins at the start time; ring shows SCHEDULED | [ ] |
| D4 | Set a window starting 12 minutes from now | Reminder notification about 10 minutes before the start | [ ] |
| D5 | While a schedule is running, try to turn it off or delete it | Refused with a message | [ ] |
| D6 | While it is running, move the end time earlier | Refused | [ ] |
| D7 | While it is running, move the end time later | Allowed | [ ] |
| D8 | Overnight window (for example 10:00 PM to 2:00 AM) | Shows "Overnight, ends next day"; blocks across midnight | [ ] |
| D9 | Window ends | Blocking stops by itself | [ ] |
| D10 | Android 12+: if "Allow exact timing" shows, tap it and allow | Link disappears after returning | [ ] |
| D11 | Phone is idle with the screen off at the start time | Schedule still starts on time (or within a minute or two) | [ ] |

### E. Emergency break
| # | Steps | Expected | Pass |
|---|---|---|---|
| E1 | During a session, open a blocked app, tap 2 min | Stay Focused closes and the app is usable | [ ] |
| E2 | While on break | Home screen shows ON BREAK and a countdown | [ ] |
| E3 | Break ends while you are still in the blocked app | Stay Focused returns | [ ] |
| E4 | After a break | "Next emergency break in 30:00" countdown; no break buttons | [ ] |
| E5 | End break now | Blocking resumes at once; cooldown starts | [ ] |
| E6 | Break in strict mode | Still available | [ ] |

### F. Statistics and goal
| # | Steps | Expected | Pass |
|---|---|---|---|
| F1 | Run a 5-minute session with the **screen off** most of the time | Focus time still increases by about 5 minutes | [ ] |
| F2 | Complete a session | Sessions completed +1 | [ ] |
| F3 | Stop a session early | Sessions completed does **not** increase | [ ] |
| F4 | Open a blocked app 3 times | Distractions blocked +3 | [ ] |
| F5 | Take a 3-minute break | Break time is not counted as focus | [ ] |
| F6 | Change the daily goal with + and − | Home shows "Today X of Y goal" | [ ] |
| F7 | After several days | 7-day chart shows bars for each day | [ ] |

### G. Reliability (the most important part)
| # | Steps | Expected | Pass |
|---|---|---|---|
| G1 | Restart the phone mid-session | Blocking resumes after boot | [ ] |
| G2 | Swipe FocusLock away from Recents | Blocking keeps working | [ ] |
| G3 | Pull down the notification shade, open **Active apps**, tap **Stop** on FocusLock | Blocking returns within about 15 seconds | [ ] |
| G4 | Turn on Battery saver | Blocking still works | [ ] |
| G5 | Leave a 60-minute session running with the screen off | Still blocking at the end | [ ] |
| G6 | Rotate the phone on the Stay Focused screen | Nothing breaks | [ ] |
| G7 | On Xiaomi, Realme, Oppo, Vivo or Samsung: set Battery to Unrestricted and enable Autostart | Blocking is reliable | [ ] |

### H. Appearance and everyday use
| # | Steps | Expected | Pass |
|---|---|---|---|
| H1 | Use the app in dark mode and light mode | The app is always dark and readable | [ ] |
| H2 | Large font size and large display size | Nothing is cut off | [ ] |
| H3 | Small phone | Home screen scrolls, buttons reachable | [ ] |
| H4 | Gesture navigation and 3-button navigation | Back behaves correctly everywhere | [ ] |

---

## Known limitations (do not report these as bugs)

- A blocked app can be visible for up to about a second before the Stay Focused screen appears.
- Changing the phone's clock can end a session or schedule early.
- Force stop or uninstalling FocusLock stops blocking. Force stop also cancels scheduled alarms until the app is opened again.
- Turning off Usage access or Display over other apps in Settings stops blocking.
- The watchdog restart after "Active apps > Stop" can take up to about 15 seconds.

---

## Part 3: Questions for closed testers

Send this to testers with the opt-in link, and ask them to use the app on at least 10 of the 14 days.

1. Phone brand, model and Android version?
2. Did Grant access work? Was anything confusing about the permissions?
3. Did blocking work every time? Did it ever fail to block, or block something it shouldn't?
4. Did the schedule start on time? Did you get the 10-minute reminder?
5. Did you find a way to get around the blocking?
6. Did the app ever stop working in the background (for example after the screen was off for a while)?
7. Did the statistics look right?
8. What was the most annoying thing?
9. What one feature would you add?
10. Would you pay ₹49 a month for strict mode and extra statistics? Why or why not?

## Tips for the 14-day Play closed test

- Recruit 30–40 people so at least 12 stay opted in for the full 14 days.
- Remind testers not to opt out or uninstall during the test.
- Send a short message on day 1, day 7 and day 12 asking for feedback.
- Fix reported bugs in new builds and upload them to the same closed test track.
