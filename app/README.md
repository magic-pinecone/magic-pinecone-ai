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
