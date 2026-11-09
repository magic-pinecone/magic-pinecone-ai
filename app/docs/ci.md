# Configure required checks for main

Use `.github/rulesets/main-required-checks.json` as the review copy for a separate main-branch ruleset. The file does not change GitHub settings by itself.

## Activate the ruleset

1. Publish the `build.yml`, `lint.yml`, and `codeql.yml` workflows.
2. Open a pull request to `main`. Wait for all six checks in the JSON file to pass on the latest commit. Confirm that the check names and GitHub Actions source match the JSON. GitHub requires a required check to have passed in this repository within the past seven days. See [Troubleshooting required status checks](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks).
3. Review the JSON and the existing rulesets in **Settings** > **Rules** > **Rulesets**. Create a separate ruleset named **Main CI status checks** for `refs/heads/main`.
4. Add all six status checks from the JSON, require the latest base branch, and set enforcement to **Active** only after their names have appeared in successful runs.

Keep the existing rulesets in place. This ruleset adds CI checks without replacing the current branch protections.

## iOS toolchains

The normal iOS build, tests, and SwiftLint use Xcode 27.1. Swift CodeQL temporarily uses the ARM64 `macos-26` runner and Xcode 26.6 because [CodeQL tracing does not support macOS 27 and Xcode 27](https://github.com/github/codeql/issues/22742#issuecomment-5949040893).

The iOS 27.1 reserved-region layout branch requires Swift 6.4. Older compilers use the existing layout fallback. CodeQL does not scan that branch with Xcode 26.6. The normal Xcode 27.1 build still compiles that branch and runs the shared iOS tests.
