# Demo video script

A shot list for a 2–3 minute walkthrough of the core app actions on the iOS simulator. Each step names the control to tap, so the same list drives a manual recording or an automated one (XcodeBuildMCP / `simctl`).

Labels are written as `English (正體中文)`. The app language is switchable in Settings, so record in one language and switch on screen in scene 7.

## Before recording

- [ ] Boot a clean simulator: iPhone 17, iOS 27.3. Erase it first so no earlier plan, favorites, or sign-in shows up.
- [ ] Freeze the status bar:
  `xcrun simctl status_bar booted override --time 9:41 --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3`
- [ ] Set the simulator to light mode, and the app language to English (or 繁體中文, if the audience is local).
- [ ] Start the backend with seeded data (see the root README). Clubs, the international pages, and Ask need it. In the app, open **Settings** > **Course data source (課程資料來源)**, enter `http://localhost:8000`, and select **Connect and sync (連線並同步)**. Do this before you start recording.
- [ ] Pick the demo data during a dry run, and write it here:
  - Course search keyword: `____` (for example 微積分). It needs several sections, with one full section.
  - Second course with no time conflict: `____`
  - Department filter suggestion to type: `____`
  - Ask questions: use the built-in examples (`Where is E6-A203?`, `When is the next 132 bus?`).
- [ ] Optional: a Portal test account for scene 6b. Without one, skip 6b. Never record a real student's account.

## Scenes

Hold times are how long the shot stays still after the action, so a viewer can read it. Automated taps are instant, so add these as waits.

### 1. Open the app — Home (首頁) · ~8 s

| # | Action | Target | Hold |
|---|--------|--------|------|
| 1.1 | Launch the app; it opens on Home. | — | 2 s |
| 1.2 | Scroll slowly to show **For international students (國際生專區)** below the ee-class section. | Home list | 2 s |

Caption: *One app for NCU students: courses, campus services, and help for international students.*

### 2. Search the course catalog — Courses (選課) · ~25 s

| # | Action | Target | Hold |
|---|--------|--------|------|
| 2.1 | Tap the **Courses (選課)** tab. The **Search (搜尋)** page shows the full catalog with a course count. | tab bar | 2 s |
| 2.2 | Tap the search field (`Search courses` / `搜尋課程`) and type the keyword slowly. | search field | 2 s |
| 2.3 | Type the department name and pick the suggested **filter token** that appears. | search suggestions | 2 s |
| 2.4 | Tap the **filter** button. In **Filters (篩選)**, set **Seats (名額)** to **Seats available (尚有名額)**. Tap **Done (完成)**. | `course-filters` | 2 s |
| 2.5 | Scroll the results. Swipe one card's info rail sideways to show seats and waitlist. | `course-info-<serial>` | 2 s |

Caption: *Search every course, filter by department, type, open seats, or time slot.*

### 3. Course details · ~12 s

| # | Action | Target | Hold |
|---|--------|--------|------|
| 3.1 | Tap a course card to open **Course info (課程資訊)**. | `course-details-<serial>` | 3 s |
| 3.2 | Drag the sheet to full height and scroll through the description. | sheet | 3 s |
| 3.3 | Close it (✕). | `關閉課程資訊` | 1 s |

### 4. Build a timetable · ~30 s

| # | Action | Target | Hold |
|---|--------|--------|------|
| 4.1 | Tap **Add (加入)** on the first course. The cart badge changes to 1. | `course-toggle-<serial>` | 1.5 s |
| 4.2 | Search for the second course and tap **Add (加入)**. Badge shows 2. | `course-toggle-<serial>` | 1.5 s |
| 4.3 | Switch the segmented control to **Timetable (課表)**, or swipe left. | `course-planning-section` | 3 s |
| 4.4 | Tap a course block on the timetable to show its details, then close. | timetable block | 2 s |
| 4.5 | Tap the **cart (已選課程)** button. Show the list and total credits. Swipe one row to show **Remove (移除)** without confirming. Tap **Done (完成)**. | `selected-courses-summary`, `selected-courses-done` | 3 s |
| 4.6 | Tap **Save (儲存)**. Scene 5 exports the saved plan, so do not skip this. | toolbar | 2 s |

Caption: *Pick courses and see them on a timetable, with conflicts visible before you register.*

### 5. Add classes to Calendar · ~8 s

| # | Action | Target | Hold |
|---|--------|--------|------|
| 5.1 | On the timetable, tap **Add to calendar (加入行事曆)**. Allow calendar access. Show the confirmation. | toolbar | 2 s |
| 5.2 | Optional: open the Calendar app to show the class events. Return to the app. | Calendar | 3 s |

The calendar permission prompt is a system dialog. Grant it once before recording (`xcrun simctl privacy booted grant calendar <bundle id>`), or tap through it on camera.

### 6. Campus services — Portal · ~20 s

| # | Action | Target | Hold |
|---|--------|--------|------|
| 6.1 | Tap the **Portal** tab. Type in **Search services (搜尋服務)** to filter the list. Clear it. | search field | 2 s |
| 6.2 | Swipe a service right and tap **Add to favorites (加入常用)**. It moves into **Favorites (常用)**. | row swipe action | 2 s |
| 6.3 | Tap **Edit (編輯)**, drag a favorite to reorder it, tap **Done (完成)**. | toolbar | 2 s |
| 6.4 | Open **Clubs (社團)**, filter by a category, and open one club. Go back. | Clubs row | 3 s |
| 6.5 | Open a Portal service. It loads in the in-app browser. Go back. | service row | 2 s |

**6b (only with a test account):** sign in on the Portal page, then go back to **Home**. Pull to refresh, and the ee-class courses load. Open one course and a bulletin.

### 7. International students and Ask (AI) · ~35 s

This is the main AI feature, so give it the most time.

| # | Action | Target | Hold |
|---|--------|--------|------|
| 7.1 | Go back to **Home** and open **Graduation language**. Pick a degree and college, then step through the admission year. | intl page | 3 s |
| 7.2 | Tap **中文 / English** to switch the rule text. | toggle | 2 s |
| 7.3 | Go back and open **Government guides**. Open **Insurance**. | intl page | 2 s |
| 7.4 | Go back and open **Ask**. Tap the example `Where is E6-A203?` | example chip | 4 s |
| 7.5 | Scroll to **What this is based on** to show the sources, and tap **Walking directions**. Go back. | answer | 3 s |
| 7.6 | Type a question in Vietnamese or Chinese, for example `Xe buýt 132 tiếp theo khi nào?`, and tap **Ask**. | question field | 4 s |

Caption: *Answers come only from the app's own data, and every answer shows its sources.*

### 8. Make it yours — Settings (設定) · ~15 s

| # | Action | Target | Hold |
|---|--------|--------|------|
| 8.1 | Tap the gear (⚙) on any tab. | toolbar | 1 s |
| 8.2 | Under **App language**, select **Tiếng Việt**. The UI switches immediately. Then select **繁體中文**, then back to the recording language. | settings | 2 s each |
| 8.3 | Under **Appearance**, select **Dark**. | settings | 2 s |
| 8.4 | Under **Timetable colors**, select **Random colors**. Close Settings and show the timetable in dark mode. | settings | 3 s |

### 9. Siri and Shortcuts (optional, iOS only) · ~15 s

The app ships App Shortcuts (`下一堂課`, `今天的課`, `找課`, `近期作業`, `最新公告`, `課程資訊`, `加入行事曆`).

| # | Action | Hold |
|---|--------|------|
| 9.1 | Go to the Home Screen and open Spotlight. Type a course name and show the course result from the app. | 2 s |
| 9.2 | Open the Shortcuts app and run **Next class (下一堂課)**. Show the snippet. | 3 s |

Spoken Siri can't be triggered reliably on the simulator. Use Spotlight and Shortcuts on camera, or record Siri on a real device.

### 10. End card · ~4 s

Return to the timetable in light mode. Show the app icon and name. Add the end title in editing.

## Recording

Record each scene as its own clip. A failed take then costs one scene, not the whole video.

```sh
# iOS: start, perform the scene, then Ctrl-C to stop
xcrun simctl io booted recordVideo --codec=h264 --force scene-04.mp4

# Android emulator (same scenes; Siri scene does not apply)
adb emu screenrecord start --time-limit 180 scene-04.webm
adb emu screenrecord stop

# Join the clips once they are trimmed
printf "file '%s'\n" scene-*.mp4 > clips.txt
ffmpeg -f concat -safe 0 -i clips.txt -c copy demo.mp4
```

## Known gaps

- **Home ee-class courses** need a Portal sign-in. Without a test account, Home shows *Sign in to Portal (需要登入 Portal)*. Start scene 1 on the international section instead.
- **Clubs, international pages, and Ask** need the backend. Without it they show a *needs a backend* message.
- **Live bus times** in Ask need the bus API to be set up on the server. Without it the answer says live arrivals aren't set up. Choose another example question if so.
- The simulator records no audio. Add narration or music when editing.
