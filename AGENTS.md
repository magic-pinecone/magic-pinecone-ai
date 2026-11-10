# Local lint checks

When changing the app, run the relevant lint checks locally before committing.
Run Kotlin checks from `app`:

```sh
./gradlew ktlintCheck :shared:detektMainAndroid :shared:detektHostTestAndroid :androidApp:detektDebug --no-daemon
```

For Swift changes, install SwiftLint and run from `app`:

```sh
swiftlint lint --config .swiftlint.yml --no-cache iosApp/iosApp
```
