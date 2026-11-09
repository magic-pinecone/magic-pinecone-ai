# Evaluate Siri course search

Use the **Siri Integration** scheme to check that 神奇松果 accepts structured course filters and that Siri selects the course finder rather than opening a text search.

## Run the intent integration tests

1. Open `iosApp/iosApp.xcodeproj` in Xcode 27.1 or later.
2. Select the **Siri Integration** scheme and an iOS 27 or later destination.
3. Select your development team for the app and test target when using a physical device. Both must use the same team.
4. Choose **Product > Test** or press **Command-U**.

The `SiriIntegrationTests` UI testing target uses `AppIntentsTesting` to call the installed app out of process. The test plan supplies the app's evaluated bundle identifier, including your `TEAM_ID` suffix. Do not change the identifier in the tests.

The course test finds a current catalog course named 微積分, repeats the search using its title and credits, and checks entity identifier resolution. It requires the current semester catalog to be available, either cached or over the network. It fails if that prerequisite is missing. The portal test checks that the app exposes its service entities. Neither test measures Siri's language understanding.

The course test keeps a screenshot attachment in the test result. Open the test report in Xcode's Report navigator to inspect it.

## Enable the Siri AI preview

Your phone's OS and Xcode's SDK are separate requirements. The preview needs Xcode with the iOS 27.3 APIs, including `ToolIntent` and `SiriEvaluations`. Xcode 27.1 cannot compile those APIs, even with a 27.3 phone connected.

1. Install Xcode 27.3 or later and open the project with it.
2. In **Xcode > Settings > Locations > Command Line Tools**, select that Xcode.
3. From the repository root, run:

   ```sh
   python3 scripts/configure_siri_ai.py
   ```

The command checks the selected SDK before writing the ignored `iosApp/Configuration/SiriAI.local.xcconfig` file. That file enables `SIRI_AI_PREVIEW` for the app and test target and raises the local app and test deployment targets to 27.3. The preview also removes the separate system search action that opens the app and inserts plain text. Without this local file, the normal build retains that action, continues to support iOS 18.2, and does not compile or run the Siri AI evaluations.

To return to the normal build, run:

```sh
python3 scripts/configure_siri_ai.py --disable
```

## Run the Siri evaluations

1. Connect an Apple Intelligence capable device running iOS 27.3 or later, such as iPhone 18 Pro.
2. Enable **Developer Mode** and **Settings > Developer > Enable UI Automation** on the device.
3. Enable Siri AI and select the Siri language you want to evaluate. The test plan sets the app language to Traditional Chinese and region to Taiwan. It does not change Siri's system language.
4. Select **Siri Integration** and the physical device in Xcode.
5. Choose **Product > Test**.

With the preview enabled, `SiriCourseSearchTests` runs four scripted scenarios. They check keyword search, combined weekday/time/credit/seat filters, a paraphrase, and a follow-up that narrows the previous search. The assertions check that Siri calls the preview tool `FindCoursesToolIntent`, read its arguments from the transient entity in its `request` parameter, and disallow `SearchCoursesInAppIntent`. Builds without the preview expose the flat `FindCoursesIntent` instead, which these evaluations do not cover. Enum arguments are checked from the recorded typed intent values because this preview cannot serialize predicates over `AnyAppEnum`. A separate test verifies that the scenario expectations can be encoded before invoking Siri. A reply that sounds correct does not pass if Siri chooses the wrong action.

In the Report navigator, open the evaluation report to inspect each turn's transcript, actual calls, arguments, and failed expectations. Run the suite several times because Siri's choices can vary. Keep failures grouped by action selection, missing or incorrect arguments, and intent execution. A network failure is different from a language understanding failure.

These scenarios launch the app and name 神奇松果 explicitly to isolate structured search. They do not prove background discovery without an app name, speech recognition quality, or every supported filter. Add your actual failing phrases to `iosApp/SiriIntegrationTests/CourseSearchSiriEvaluations.swift` as you encounter them.

## Check the integration metadata

After building with Xcode 27.3 or later, open **Product > Siri Integration Advisor** to inspect suggestions for the app's intent metadata. Select the built device app rather than an older simulator build, then choose **Analyze**.

You can also run the analyzer against the actual built app:

```sh
/Applications/Xcode-beta.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/sirimetadataanalyzer /path/to/mpc-rewrite.app
```

Use the built app path from Xcode's Products group. The analyzer reports registered tools, errors, warnings, and suggestions. The current metadata uses the development localization `en` with Chinese intent text; `--language zh-Hant` is not available for that metadata. The App Shortcuts catalog separately includes Traditional Chinese phrase translations.

For a manual baseline, create a shortcut using **找課程**, fill its filters, and run it. Then ask Siri, "用神奇松果找週三下午三學分而且還有名額的課，直接告訴我結果". Compare the evaluation's recorded arguments with the filters you entered manually.

## Guide Siri's tool selection

Edit `iosApp/iosApp/Skills.skillsbundle/Skills/find-courses/SKILL.md` when changing the course-search workflow. The app skill explains when a request means the course catalog rather than the saved timetable, when to use the course finder rather than Spotlight, and how to retain filters across follow-up requests.

Build the app after editing the skill. Xcode validates the bundle and compiles it into `Metadata.appintents/Skills.skillsc`. The evaluations record skill loading as a preferred expectation and still require the correct intent and arguments. Loading the skill alone does not pass the search checks.

## Apple references

- [Bring custom actions and content to Siri](https://developer.apple.com/documentation/appintents/bringing-custom-actions-and-content-to-siri).
- [Test App Intents code](https://developer.apple.com/documentation/appintentstesting/testing-your-app-intents-code).
- [Run scenarios with SiriService](https://developer.apple.com/documentation/sirievaluations/siriservice).
- [Assert intent calls and reject unwanted calls](https://developer.apple.com/documentation/sirievaluations/calls).
- [Guide Siri with App Skills](https://developer.apple.com/documentation/appintents/orchestrating-complex-actions-with-app-skills).

The preview registration and evaluation code follows Apple's documented API. It must be compiled and run with the preview SDK before being treated as verified Siri behavior.
