# Magic Pinecone

<p align="center">
  English | <a href="README.zh-TW.md">正體中文</a>
</p>

<p align="center">
  <img src="docs/assets/app_icon.png" alt="Magic Pinecone app icon" width="256" />
</p>

Magic Pinecone（神奇松果）is an all-in-one campus app for National Central University students.

This repository is the Kotlin Multiplatform rewrite of the original Flutter app. It is under active development.

> Want to try Magic Pinecone in a browser? Check out the [Lite version](https://magic-pinecone.github.io/magic-pinecone-lite/).

## Features

- **Course search**: Browse and search the NCU course catalog.
- **Timetable planning**: Select courses, preview them on a timetable, and explicitly save a plan.
- **Offline persistence**: Store course data and saved plans in a local SQLite database with Room.
- **Native platform shells**: Use Jetpack Compose on Android and SwiftUI on iOS while reusing core logic, data, and Compose UI through KMP.
- **Portal shortcuts**: Search campus services and open Portal pages in the app or direct services in the browser.
- **Clubs**: Browse NCU student clubs by category from the Portal tab. Needs a backend; club text comes from the NCU club site in Chinese, and the backend machine-translates and caches it in the app's language (`GET /api/v1/clubs?lang=`).

Portal pages support manual sign-in in the embedded web view. Automatic sign-in, secure credential storage, and session restoration are not connected to the app yet.

## Getting started

### Prerequisites

- Amazon Corretto 21
- Android Studio with an Android SDK for API 37
- Xcode for the iOS app

### Clone the repository

```bash
git clone https://github.com/magic-pinecone/mpc-compose.git
cd mpc-compose
```

### Run Android

Open the project in Android Studio and run the `androidApp` configuration, or build a debug APK from the command line:

```bash
./gradlew :androidApp:assembleDebug
```

### Run iOS

1. Set your Apple development team in [`iosApp/Configuration/Config.xcconfig`](iosApp/Configuration/Config.xcconfig).
2. Open [`iosApp/iosApp.xcodeproj`](iosApp/iosApp.xcodeproj) in Xcode.
3. Select a target device and run the app.

### Preview shared UI in a browser

The `wasmJs` target renders the shared Compose preview scenarios in a browser, so you can check a UI change without a device. It has no persistence or campus sign-in, and it shows a navigation rail on wide windows and a bottom bar on narrow ones. Start the development server:

```bash
./gradlew :shared:wasmJsBrowserDevelopmentRun
```

Open a scenario directly by its name in the URL hash, for example `http://localhost:8080/#TIMETABLE_EMPTY`. Chinese text needs network access the first time, because Compose for Web downloads its CJK fallback font.

## Use a backend for course data

The app reads the published static course catalog by default. To read from [`backend/`](../backend/) instead, start the backend as described in the [root README](../README.md), then open **Settings** > **課程資料來源** in the app, enter the backend address (`http://10.0.2.2:8000` on the Android emulator, `http://localhost:8000` on the iOS simulator), and select **連線並同步**. See [`docs/api-contract.md`](../docs/api-contract.md) for the API contract.

## Tests and checks

Run the shared Android-hosted and iOS simulator tests:

```bash
./gradlew :shared:testAndroidHostTest
./gradlew :shared:iosSimulatorArm64Test
```

Run all configured project checks:

```bash
./gradlew check
```

The full check also requires [SwiftLint](https://github.com/realm/SwiftLint) to be installed.

## Acknowledgements

- **App icon**: Designed by **Jui-Ting Wu**.
- **Course data**: [NCU-Course-Finder-DataFetcher-v2](https://github.com/zetaraku/NCU-Course-Finder-DataFetcher-v2).
