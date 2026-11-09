# i18n plan: English, Traditional Chinese, Vietnamese

The app's UI text is hardcoded Traditional Chinese today. This program moves it into moko-resources so the app ships in English, Traditional Chinese, and Vietnamese, with English as the fallback for every other phone language. Users follow their phone language by default and can override it in Settings.

The scope is UI text: labels, messages, dates, and counts. Course names, teachers, and syllabi stay as NCU provides them. Siri intents and `AppShortcuts.xcstrings` come later.

The order is one foundation PR (I0), then the feature extraction PRs (I1 to I7) and the language picker (IL) in parallel, then the SwiftUI pass (IS) and the lock-down (IZ).

## How to work in this program

- One PR is one row in the table below. Take a row by putting your name on it in the tracking issue before you start.
- Each feature PR owns its own `strings_<feature>.xml` and `plurals_<feature>.xml` in each locale folder. You never edit another feature's file, so parallel PRs do not conflict on strings.
- Keys start with the feature name, for example `settings_backend_syncing`. Keys are `snake_case` English and never change once merged.
- Every PR adds all three languages at once. The key-parity check (I0) fails the build when a key is missing from any locale.
- Write the English and Traditional Chinese text yourself. Vietnamese may be machine-drafted. Mark those PRs `needs-vi-review` so a Vietnamese speaker checks them before release, not before merge.
- Mechanical extraction is a good job to hand to Codex. Give it this file, the row, and the conventions above, and review its diff like any other PR.

## Program table

| PR | Task | Depends on | Surfaces | Needs a Mac |
|---|---|---|---|---|
| I0 | Set up moko and the guards | None | shared, androidApp, iosApp | Yes |
| I1 | Settings and backend endpoint section | I0 | shared Compose | No |
| I2 | Course catalog and filters | I0 | shared Compose, androidApp | No |
| I3 | Course details | I0 | shared Compose | No |
| I4 | Course planning and timetable | I0 | shared Compose, androidApp | No |
| I5 | eeclass | I0 | shared Compose, androidApp | No |
| I6 | Portal | I0 | shared Compose | No |
| I7 | Shared components, navigation, app shell | I0 | shared Compose, androidApp | No |
| IL | Language picker in Settings | I0 | shared, androidApp, iosApp | Yes |
| IS | SwiftUI screens | I1 to I7 | iosApp | Yes |
| IZ | Lock the guard | All above | shared | No |

## Set up moko and the guards (I0)

This PR is the only one that touches Gradle and Xcode configuration. Everyone else waits for it, so keep it small and merge it first.

**Build.**

- [ ] Add moko-resources 0.27.1 to `app/gradle/libs.versions.toml` (library `resources`, `resources-compose`, plugin `dev.icerock.mobile.multiplatform-resources`). Do not inline versions the way the prototype did.
- [ ] In `app/shared/build.gradle.kts`, apply the plugin, add `api(resources)` and `implementation(resources-compose)` to `commonMain`, `export(resources)` from the iOS framework, set `resourcesPackage` to `org.mpc.shared`, and register `src/commonMain/moko-resources` with `resourcesSourceSets.getByName("commonMain").srcDir(...)`. Without that explicit registration the prototype generated an empty `MR.strings`.
- [ ] Add `resources-compose` to `app/androidApp/build.gradle.kts`.
- [ ] Create `app/shared/src/commonMain/moko-resources/base/` (English), `vi/`, and the Traditional Chinese folder. Resolve its name first (see the checks below).
- [ ] Add `app/androidApp/src/main/res/xml/locales_config.xml` and `android:localeConfig` so Android 13+ lists the three languages in system per-app language settings.
- [ ] Add `CFBundleLocalizations` (en, zh-Hant, vi) to `app/iosApp/iosApp/Info.plist`.
- [ ] Add a Gradle task `checkI18nKeys` next to `checkDesignTokens` and wire it into `check`. It fails when a key or plural exists in one locale and not another, or when the `%s`/`%d` placeholders differ between locales.
- [ ] Add a Gradle task `checkHardcodedUiText`. It fails on a Chinese string literal in `shared/src/commonMain/kotlin/org/mpc/presentation/**` and `androidApp/src/main/kotlin/**`. It reads a checked-in allowlist of files that still have text, and fails if a listed file no longer has any, so the list only shrinks. `domain/`, `data/`, and tests are out of scope because their Chinese is NCU data and search tables.

**Checks to run before merging, because the prototype did not cover them.**

- [x] Use `zh-rTW` for Traditional Chinese. moko generates Android `values-zh-rTW` and iOS `zh-TW.lproj`. Foundation selects `zh-TW` for Taiwan and Hong Kong preferences and English for `zh-Hans-CN`. `zh-Hant` generates Android `values-zh`, which also matches Simplified Chinese.
- [x] The iOS simulator Release build succeeded with static `Shared`. Its app bundle contains moko `en.lproj`, `vi.lproj`, and `zh-TW.lproj` without an additional copy-resources phase.
- [ ] Detekt reported 18 compiler errors during analysis in the prototype while still passing. Confirm `:shared:detektMainAndroid` still reports findings after moko is added.

**Done when.** `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug ktlintCheck :shared:detektMainAndroid :shared:iosSimulatorArm64Test check` passes. The Debug and Release iOS builds pass. One sample string shows in English on an English emulator and in Vietnamese on a Vietnamese one.

## Extract one feature's text (I1 to I7)

Every feature PR follows the same steps. The row's scope is the list below.

- I1 Settings. `SettingsScreen.kt`, `BackendEndpointSection.kt`, `presentation/state/BackendEndpointUiState.kt`.
- I2 Catalog and filters. `views/courseFilters/`, `formatting/CourseFilterLabel.kt`, `formatting/CourseFilterKey.kt`, and in `androidApp` `CourseCatalogScreen.kt`, `CourseFilterSheet.kt`, `CourseFilterPicker.kt`, `CourseFilterChips.kt`.
- I3 Course details. `views/courseDetails/`, `formatting/CoursePresentationFormatters.kt`.
- I4 Planning. `views/coursePlanning/`, `formatting/ScheduleFormatters.kt`, `presentation/model/CourseTimetableBlock.kt`, and in `androidApp` `CoursePlanningScreen.kt` and `calendar/`.
- I5 eeclass. `EeclassHomeScreen.kt`, `EeclassCourseScreen.kt`, `EeclassPageContent.kt`, `formatting/EeclassFormatters.kt`, `presentation/model/Eeclass*.kt`, and `androidApp` `EeclassBulletinScreen.kt`.
- I6 Portal. `PortalScreen.kt`, `PortalWebScreen.kt`, `PortalAccountCard.kt`, `presentation/model/PortalAccount.kt`.
- I7 Shell. `presentation/components/`, `presentation/navigation/`, `presentation/preview/`, `androidApp` `AndroidShell.kt`, `navigation/`, and `res/values/strings.xml` (`app_name`).

**Steps.**

- [ ] Move each UI string into `strings_<feature>.xml` in all three locales. Counts go into `plurals_<feature>.xml`, because moko reads plurals only from `plurals*.xml` files.
- [ ] Compose code calls `stringResource(MR.strings.x)`. Code that builds text outside a composable, such as a formatter or a UI state, returns a `StringDesc` or `StringResource` instead of a `String`, and the composable resolves it. `BackendSyncState.message()` is the first example.
- [ ] Rewrite dates and periods as format strings with placeholders, not Chinese concatenation. `"${month}月${day}日"`, `"星期五"`, and `"第 3–4 節"` each become one key, since word order differs between the three languages.
- [ ] Update tests that assert Chinese UI text to assert the `StringResource` or the resolved `StringDesc` instead. Leave tests of NCU data and search alone.
- [ ] Remove the PR's files from the `checkHardcodedUiText` allowlist.

**Done when.** The `I0` command passes, the allowlist shrank, and screenshots of the feature in English and Vietnamese are in the PR.

## Add the language picker (IL)

- [ ] Store the choice (System, English, 繁體中文, Tiếng Việt) in the existing DataStore, with a repository like `BackendEndpointRepository`.
- [ ] Android. Apply it with `AppCompatDelegate.setApplicationLocales`, which recreates the activity and keeps the system per-app setting in sync. The prototype found that moko's Android Compose `stringResource` reads `LocalConfiguration`, so setting `StringDesc.localeType` alone probably won't change Compose text. Confirm this on an emulator before building on it.
- [ ] iOS. Set `StringDesc.localeType = StringDesc.LocaleType.Custom(...)` at startup and on change, then rebuild the root Compose controllers and refresh SwiftUI, since the property is not observable state.
- [ ] Prove on both platforms that switching language updates an open screen, and that the choice survives a restart.

## Translate the SwiftUI screens (IS)

Do this last, and only for SwiftUI screens that are not about to move to Compose. Swift reads the same keys through `MR.strings().x.desc().localized()`. `iosApp/iosApp/intents/` stays out of scope with the rest of the Siri work.

## Lock the guard (IZ)

- [ ] Empty the `checkHardcodedUiText` allowlist, so any new Chinese literal in UI code fails CI.
- [ ] Clear the `needs-vi-review` labels, or list the strings still waiting for review.

## Appendix A. Prototype evidence

Branch `prototype/moko-i18n`, commits `15aa889` and `848dd99`, worktree `.claude/worktrees/moko-proto`. The full report is `PROTOTYPE-REPORT.md` on that branch.

- moko-resources 0.27.1, the latest release, builds with Kotlin 2.4.20, AGP 9.4.1, the `androidLibrary` plugin, Compose 1.12.0, and SKIE. Android build, tests, ktlint, and detekt pass. The iOS Debug simulator build passes.
- Several `strings_*.xml` and `plurals_*.xml` files per locale merge natively into `MR`.
- Shared Compose, Android-only Compose, and SwiftUI each resolved a moko string.
- The APK contains the en and vi strings and plurals. The iOS app contains `en.lproj` and `vi.lproj`.
- A Vietnamese plural resolved on iOS with a custom locale (`Đã đồng bộ 2 khóa học`).
- I0 selected `zh-rTW`. moko generates Android `values-zh-rTW` and iOS `zh-TW.lproj`. `zh-Hant` generates Android `values-zh`. Foundation selects `zh-TW` for Taiwan and Hong Kong and English for `zh-Hans-CN`.
- The I0 arm64 simulator Release build succeeded with static `Shared` and packaged moko `en.lproj`, `vi.lproj`, and `zh-TW.lproj`.
- Not proven yet. Runtime language switching on either platform and Android plural selection at runtime.

## Appendix B. Alternatives rejected

- Compose Multiplatform resources (`Res.string`). They are already a dependency, but SwiftUI cannot read them, and three surfaces need the same keys. moko serves all three.
- Traditional Chinese as the base. Extraction would have been a pure move, but phones set to an unsupported language would show Chinese.
- One `strings.xml` per locale. Five people editing one file means a conflict on almost every merge.

## Appendix C. Risks

- Release iOS builds miss resources if the static framework needs a build phase. Checked in I0.
- Many test files assert Chinese text, so extraction PRs touch tests. I1 to I7 switch those assertions to resource ids.
- Vietnamese quality depends on a native-speaker review before release.
- The language picker on Android depends on the per-app locale API. If moko and AppCompat disagree, IL has to key the Compose root on the locale.
