# 0.8.0 Application UI language · 2026-10-07

Settings now starts with **Interface language**, with **Follow system**, **English**, **中文**, **Español**, and **Italiano**. This setting changes the application interface independently of input, target, favorite languages, translation model, speech model and offline voice choices.

## Implementation

- Android string resources cover the translation tabs, settings, camera controls, accessibility labels, app-generated status/error messages and display names. There are 187 strings and two plural resources per locale. English is the fallback for unsupported system languages.
- `AppCompatDelegate.setApplicationLocales` applies the selected UI locale and recreates the activity. The Android ViewModel retains translation content and settings navigation during recreation. `AppCompatActivity` and both default/API-31 themes use an AppCompat theme.
- Android 13+ uses system per-app locale storage and synchronizes with system App language settings through `locales_config.xml`. On older Android, AppCompat 1.6.1 is configured to store and restore the app locale through `autoStoreLocales`.
- Application-owned state messages use `UiText` resource IDs and typed arguments, resolved when shown. Existing messages therefore follow the new locale; user input, OCR text, translations, model identifiers and platform voice identifiers stay unchanged. External platform/native diagnostics remain in their original wording.
- Language choices use native names so the user can recover from selecting an unfamiliar interface language. Controls wrap/grow to accommodate longer labels. Continuous translation still uses phrase segmentation; no model, inference or TTS backend change was made.

## Verification performed

- 38 JVM tests pass, including four Robolectric Android 13 runtime tests and the language-tag mapping regression test. Existing translation, speech segmentation, cropping and state tests remain passing.
- Android runtime tests resolve all four locale titles and plural counts; cover es-MX, zh-CN, it-CH and unsupported-language fallback; re-resolve retained/nested status messages; preserve literal text; set all four application locale preferences, replace the activity and clear the override for Follow system.
- The runtime test initially exposed the Android 12+ theme still inheriting a platform theme. This would have prevented AppCompat activity startup; both theme variants were fixed, and the test passes after the fix.
- `tools/check-localizations.py` verifies matching resource inventories, format arguments, plural fallback entries and source resource references for all four locales.
- `lintDebug` reports **No issues found**. Main and instrumentation APKs compile. New local tests use Robolectric 4.14.1; only the development computer downloads this test runtime. Robolectric is a test dependency and is not shipped in the app.

## Device validation boundary

No ADB device or local Android emulator was available during this work. **0.8.0 has not been installed on vivo/iQOO and no phone screenshots, cold process restart, pre-Android-13 storage, OS Settings round trip or visual layout checks have been performed.** Robolectric checks Android resource and locale behavior; it does not establish device UI or hardware quality. Historical 0.7.1 camera tests do not count as 0.8.0 device validation.

When a phone is available:

1. Install all nine 0.8.0 APKs together, preserving existing data.
2. Enter a recognizable draft and retain current source/targets, model choices and favorites. Open Settings and switch through all four UI languages. Confirm Settings remains open and the selected chip follows the choice.
3. Return to both translation tabs and open the camera; verify labels/status messages and long-label wrapping, including larger system font settings.
4. Force-stop/reopen and verify the locale is retained. Choose Follow system and verify the override clears. On Android 13+, change the application's language through OS Settings and return to the app.
5. Confirm user text, previous translations, favorites, source/targets and model choices were not changed by UI language selection.

## Reproduction

Run `tools/build-bundled.ps1 -IncludeDeviceTests` after staging model assets. The regular build is offline and relies on previously cached development dependencies. A fresh computer must first resolve AppCompat and Robolectric from Google Maven/Maven Central and warm the Robolectric SDK cache; the test cache is configured under `.tooling/robolectric`. Run `python tools/check-localizations.py` for the resource audit.

Build logs: `.tooling/temp/build-0.8.0-release-check.log`; resource audit: `.tooling/temp/localization-audit-0.8.0.json`; packaging report: `BUILD_0.8.0_VERIFICATION.json`.

Official references: [Android per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages), [Robolectric setup](https://robolectric.org/getting-started/), [Robolectric 4.14.1](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.14.1).
