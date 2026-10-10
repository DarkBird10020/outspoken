# Contributing to Outspoken

Thank you for helping. Outspoken is used by people who cannot speak for themselves, so we care most about two things: that every choice the person makes is the one they meant, and that nothing they say ever leaves the phone.

## Ways to help

- **Report a bug.** Open a [bug report](https://github.com/DarkBird10020/outspoken/issues/new?template=bug_report.yml). Run logs make most bugs quick to fix: "Share logs" on the Model screen, or `adb logcat -s Outspoken`. Read them before attaching: they hold what people said near the phone.
- **Suggest a feature.** Open a [feature request](https://github.com/DarkBird10020/outspoken/issues/new?template=feature_request.yml) and say who it helps and how.
- **Test on a phone.** Results from real phones, glasses, lighting and eye conditions are some of the most useful things you can send.
- **Send a pull request.** For anything larger than a small fix, open an issue first so we can agree on the approach.

## Setting up

You need JDK 17 or newer and the Android SDK (API 36).

```bash
git clone https://github.com/DarkBird10020/outspoken.git
cd outspoken
./gradlew testDebugUnitTest
./gradlew installDebug
```

[PRD.md](PRD.md) describes the product, and [PROGRESS.md](PROGRESS.md) records what is built, how to test each part on a phone, and every phone test so far.

## Rules the project keeps

These are checked in review, and some by CI.

- **No internet.** Never add the INTERNET permission or any cloud feature, account or sync. CI fails an APK that asks for INTERNET.
- **No secrets.** No keys, tokens, keystores or passwords in the repository.
- **No logs in the repository.** Run logs hold what people said. `phone-logs/` is ignored, and CI fails if a log file is committed.
- **Original work.** Do not copy code from other blink, eye-tracking or AAC apps. Reading them for ideas is fine.
- **Libraries are listed.** Any library you add goes into the Acknowledgements section of the README in the same commit, with its license.
- **The model stays swappable.** The model is reached only through the `SuggestionEngine` interface.
- **Design comes from the design files.** Colours, fonts, surfaces and shadows live in `ui/theme/`, and icons are `res/drawable/ic_*.xml`. Use these rather than new ones. A state the design does not cover uses the closest existing style and is listed under "Design gaps" in PROGRESS.md.

## Making a change

1. Branch from `main`. Code reaches `main` only through a pull request.
2. Keep logic out of Android classes. Blink detection, gaze, cards, reply parsing and calibration are plain Kotlin with no Android imports, so they can be unit tested.
3. Ship tests with the change, in `app/src/test`. Never delete or weaken a test to make a change pass. If behaviour changes on purpose, change the test and say why in PROGRESS.md.
4. Log what matters through `AppLog` (Android classes) or an `EventLog` parameter (pure logic), in plain words: what happened and why. Summarise rather than logging every frame.
5. Update PROGRESS.md in the same commit: what it does, why, where the code is, and how to test it on a phone. Mark an item `[x]` only after it has passed on a phone.
6. Run the checks before you push:

   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```

7. Open a pull request and fill in the template. All three CI checks (Unit tests, Android lint, Build APK) must pass before it can merge.

## Commits

Small commits, one working step each. Write the message as a short imperative line, for example `Add blink state machine`, with an optional short body when the reason is not obvious.

## Code style

Kotlin official style (`kotlin.code.style=official`). Comment only where the reason is not obvious, and point to the phone log or measurement behind a number when there is one.

## Questions

Open a [discussion issue](https://github.com/DarkBird10020/outspoken/issues/new) and we will help.

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).
