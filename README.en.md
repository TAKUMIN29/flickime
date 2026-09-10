<div align="center">

<img src="docs/store/icon-512.png" width="120" height="120" alt="FlickIME icon">

# FlickIME (フリックIME)

**A Japanese flick-input keyboard for Android**

[![Stars](https://img.shields.io/github/stars/TAKUMIN29/flickime?style=flat&logo=github)](https://github.com/TAKUMIN29/flickime/stargazers)
[![Release](https://img.shields.io/github/v/release/TAKUMIN29/flickime?label=release)](https://github.com/TAKUMIN29/flickime/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/TAKUMIN29/flickime/total?label=downloads)](https://github.com/TAKUMIN29/flickime/releases/latest)
[![License](https://img.shields.io/github/license/TAKUMIN29/flickime)](LICENSE)

Brings the equivalent of the Windows `Win+V` (clipboard history) and `Ctrl+Z` / `Ctrl+Y` (undo/redo) shortcuts onto the keyboard itself.
The UI follows the common Japanese flick-input convention: the key under your finger stays fixed until release, with a cross-shaped popup showing the candidate for each direction.

[Download](https://github.com/TAKUMIN29/flickime/releases/latest) ・
[Features](#features) ・
[Usage](#usage) ・
[Build](#build)

[日本語](README.md) ・ English

</div>

---

## Demo

Typing "にほん", picking "日本" from the conversion candidates, then selecting the whole text with the toolbar "Select All" button.

![Keyboard in action](docs/demo.gif)

Promo video (flick input, typo-tolerant correction candidates, full-screen candidate grid, undo / select-all / copy-all):

<video src="https://github.com/TAKUMIN29/flickime/raw/master/docs/promo.mp4" controls width="360"></video>

The file at [docs/promo.mp4](docs/promo.mp4) can also be downloaded directly and played locally.

| Keyboard | Conversion candidates | Full candidate list |
| --- | --- | --- |
| ![Keyboard](docs/keyboard.png) | ![Conversion](docs/conversion.png) | ![Candidate list](docs/candidates.png) |
| Left column: `記号`(symbols) `←` `123` `あ/A/Ａ`. Right column: `削除`(delete) `→` `空白`(space) `改行`(enter) | Candidates appear as you type. Blue entries are typo-tolerant correction candidates and user-dictionary words | Tap `▼` at the right end of the candidate strip to expand it over the whole keyboard area and pick from every candidate |

## Features

| Feature | How it is implemented |
| --- | --- |
| Flick input | A 4x5 feature-phone-style layout. The key under your finger stays fixed; the direction you move decides the character. Repeated taps also cycle through variants |
| Kana / alphabet / numbers / symbols | The bottom-left `あ/A/Ａ` key cycles hiragana to half-width alphabet to full-width alphabet. The top-left `記号` key opens the symbol palette |
| Cursor movement | `←` / `→` keys in the left and right columns. Long-press to move continuously |
| Dakuten / handakuten / small kana | The `小゛゜` key. Tap to cycle (は→ば→ぱ), flick left for ゛, flick right for ゜ |
| Katakana / half-width katakana conversion | Flick the space key upward in kana mode. Cycles the previous word through hiragana, katakana, and half-width katakana |
| **Kana-kanji conversion** | Flick the `あ/A/Ａ` key upward to toggle it on or off. While on, readings are sent to the Mozc engine, and candidates can be picked and committed from the strip |
| **Full-screen candidate list** | Tap the `▼` at the right end of the candidate strip to expand it into a grid over the whole keyboard area and choose from every candidate |
| **Typo-tolerant correction candidates** | Readings for likely mis-flicks are also converted in the background and shown as blue correction candidates in the strip (for example "てすのした" becomes "テストした") |
| **123 / カナ key** | Second key from the bottom in the left column. Normally toggles `123` and `１２３` (half-width and full-width digit layouts); while composing it becomes `カナ` (convert the pending text to katakana). Flick up to switch to another IME |
| **Segment split / move** | Flicking the `変換` key that appears while composing. Left and right move between segments, up and down grow or shrink a segment boundary |
| **User dictionary** | "Edit user dictionary" in the settings screen. Registered words (with their reading) show up at the top of the candidate list on a prefix match |
| Word-level delete | Flick the `削除` key left. Deletes the whole pending composition if one is active, otherwise the previous word |
| One-handed mode | Flick the `記号` key up. Cycles off, left-aligned, right-aligned, off, shrinking the whole key layout to one side |
| Keyboard height adjustment | Drag the handle just below the toolbar up or down. Linked to the slider in the settings screen |
| Clipboard history | "履歴" (history) on the toolbar. Detects copies and stores them on-device; tap to paste, star to pin. On Android 13+, sensitive copies (for example from password managers) are automatically excluded |
| **Select all / select all and copy** | `全選択` / `全コピー` on the toolbar. Copied text is also added to the clipboard history |
| Undo / redo | Toolbar buttons, plus `Ctrl+Z` / `Ctrl+Shift+Z` / `Ctrl+Y` on a physical keyboard |
| History panel shortcut | `Ctrl+Shift+V` on a physical keyboard |

## Build

Requirements:

- JDK 17
- Android SDK (compileSdk 35 / build-tools)
- Gradle 8.9 or later (or Android Studio)

The Gradle Wrapper jar is checked into the repo at `gradle/wrapper/gradle-wrapper.jar`, so there is no need to run `gradle wrapper` yourself.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat installDebug   # with a device connected over USB, or an emulator running
```

Point `local.properties` at your SDK (Android Studio generates this automatically; use forward slashes, for example `sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk`).

### APK size and per-ABI builds

Mozc's `libmozc.so` is roughly 13-16MB per ABI, and the dictionary data `mozc.data` is about 19MB, so a universal APK bundling every ABI comes out to 80MB+. Lightweight per-ABI APKs are also produced for sideloading (see the `splits.abi` config in `app/build.gradle.kts`).

```
app/build/outputs/apk/debug/
├── app-universal-debug.apk   # every ABI bundled (safest for compatibility)
├── app-arm64-v8a-debug.apk   # the common ABI on real devices, ~33MB
├── app-armeabi-v7a-debug.apk
├── app-x86_64-debug.apk      # for emulators, ~33MB
└── app-x86-debug.apk
```

Just `adb install` the file matching your emulator or device ABI. For Play Store distribution, build an App Bundle (`.aab`) with `bundleRelease` instead; Play then delivers only the slice matching each device ABI automatically.

## Usage

1. Launch the app and enable **FlickIME** from "Open input method settings"
2. Switch the active keyboard to FlickIME using "Choose keyboards"
3. Try it out in the test field at the bottom of the screen

## About kana-kanji conversion (Mozc)

`app/src/main/jniLibs/*/libmozc.so` and `app/src/main/assets/mozc.data` are build artifacts of [google/mozc](https://github.com/google/mozc) (BSD 3-Clause), built with WSL2 + Bazel and committed to this repo as-is. To rebuild them, follow these steps inside WSL2:

```bash
git clone https://github.com/google/mozc.git && cd mozc/src
python3 build_tools/update_deps.py        # fetches the NDK etc. automatically
bazelisk build package --config oss_android --config release_build
bazelisk build //data_manager/oss:mozc_dataset_for_oss --config release_build
bazelisk build //protocol:commands_java_proto_lite //protocol:candidate_window_java_proto_lite \
    //protocol:config_java_proto_lite //protocol:engine_builder_java_proto_lite \
    //protocol:user_dictionary_storage_java_proto_lite --config release_build
```

Windows hosts are not supported (the NDK r29-based build only works on macOS/Linux), so WSL2 is required. The JNI boundary is a single `evalCommand(byte[]) -> byte[]` call (a serialized `mozc.protocol.Command`). Using `KeyEvent.key_string` together with `input_style=AS_IS` lets this IME feed a kana character it has already committed straight in as pending text, with no romaji conversion involved. That meant the existing flick-input logic (`FlickKeyboardView` / `KanaModifier`) could be reused almost unchanged; integration just adds a branch that sends a committed character to the Mozc session instead of calling `commitText` directly.

The class and package name `com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI` is fixed by the JNI symbols compiled into `libmozc.so` and cannot be renamed. It also has to be a genuine `static` native method via a Kotlin `companion object` plus `@JvmStatic`, rather than a plain `object` (which compiles to an instance method); otherwise it crashes at runtime with `jclass has wrong type`.

## Design notes and limitations

Unlike Windows, Android does not provide **system-wide clipboard history or undo**. The implementation works around that as follows.

- **Clipboard history**: Since Android 10 (API 29), background apps cannot read the clipboard, except the **default IME**, which is exempt. Implementing this as an IME lets it accumulate history from copies made in any app. Nothing is recorded while FlickIME is not the selected IME. History is stored only on-device (`clips.json` inside the app's private directory) and is never sent anywhere. On Android 13+, copies with `ClipDescription.EXTRA_IS_SENSITIVE` set (for example from password managers) are excluded from history.

- **Undo / redo**: Since there is no OS-wide undo, only edits this IME made through `InputConnection` are pushed onto an `EditHistory` stack to undo. If the text was changed by something else in the meantime, that is detected when undo runs and the history entry is discarded instead of deleting the wrong characters. This is independent of an app's own undo (for example Google Docs). Text committed via Mozc conversion is recorded in the same `EditHistory`, so it is covered by undo/redo too.

- **Kana-kanji conversion**: While Mozc is toggled on, typed characters are no longer committed immediately; instead they are sent to the Mozc session and shown as pending text via `setComposingText`, until a candidate is picked or the space/enter key commits it. That is a different input model. Backspace, space, and enter are all forwarded straight to Mozc while composing, and state transitions are left entirely to Mozc's own session management.

- **Typo-tolerant correction candidates**: Mozc's `probable_key_event` (a probability-weighted hint about the keystroke) does not work with this IME's input style, which sends kana directly via `InputStyle.AS_IS`, and the built `mozc.data` does not include a typing-error model either. So corrections are handled independently, outside Mozc.

  For every keystroke, [`FlickAlternates`](app/src/main/java/com/example/flickime/keyboard/FlickAlternates.kt) estimates "maybe it was actually the neighboring key, or a different direction" candidates and remembers them alongside the pending text. When the candidate strip refreshes, each reading with exactly one character swapped is actually converted in a separate Mozc session, and the results are listed as correction candidates. They are ordered by **fewest resulting segments after conversion**: a reading that converts cleanly into one coherent word is more likely to be what was intended, so for example "てすのした" produces "てすとした→テストした" (1 segment), which ranks above "ねすのした→ネスの下" (2 segments).

  Lookups run on a background thread and are debounced while typing continues. Since the native session handler is not thread-safe, calls are serialized through `MozcEngine.eval`.

- **Lost conversion sessions**: The conversion engine can garbage-collect sessions internally, and a key sent to a session that is gone comes back as a response with neither a result nor pending text. Mistaking that for "nothing changed" would leave typing stuck producing nothing. This is detected via the response error code: any pending text still on screen is committed, the session is recreated, and the keystroke is resent.

  The throwaway session used for correction candidates is also reused rather than recreated per reading; creating too many triggers the engine's own cleanup, which can take out the session currently being typed into as collateral damage.

- **Password fields**: Typed characters are not passed to the conversion engine in password fields, so they are not retained in Mozc's learning/prediction data. Input falls back to a direct commit instead.

- **Katakana conversion**: The `カナ` key while composing sends Mozc's F7. `SpecialKey.KATAKANA` is the hardware katakana-key equivalent; it only switches the input mode and does not convert the pending text.

- **User dictionary**: Words are not registered into Mozc's own user dictionary; instead, prefix matches against this app's own store are prepended when building the candidate list. That makes it easy to guarantee a registered word always shows up. Selecting one commits the string directly rather than going through Mozc's candidate selection.

- **Select all / copy**: Rather than reading the whole text and replacing it locally, this calls `InputConnection.performContextMenuAction` to have the focused app perform the text-operation menu action itself. Selection and copy behavior differs across apps, so letting the app handle it is more reliable than reimplementing it here.

- **Key layout**: The left and right function keys follow the layout that is conventional for Japanese flick keyboards. Left column: `記号` / `←` / `123` / `あ/A/Ａ`. Right column: `削除` / `→` / `空白` / `改行`. Settings opens from "設定" on the toolbar, so there is no settings key on the keyboard itself.

  The full-width alphabet/number layouts are generated from the half-width layout table by swapping just the character keys' output and labels to full-width (`KeyLayouts.toFullWidth`), so the same layout table does not need to be maintained twice.

- **Full candidate list**: The candidate strip is a single horizontally scrolling row, so with many candidates the far ones are hard to reach. Tapping `▼` at the right end expands it into a grid covering the keyboard area, so every candidate is reachable. When creating the input session, `SET_REQUEST` asks Mozc for `mixed_conversion` (mobile-IME behavior) and a wide `candidate_page_size`, so there are enough candidates to make the expanded view worthwhile. The throwaway session used for correction candidates does not get the same request, since a different conversion result would throw off the segment-count-based ranking.

## Main files

```
app/src/main/java/com/example/flickime/
├── FlickImeService.kt          IME core: key handling, history, undo, Mozc integration
├── SettingsActivity.kt         Setup steps and behavior settings
├── Prefs.kt                    Settings values
├── keyboard/
│   ├── KeySpec.kt              A single key's definition and flick directions
│   ├── KeyLayouts.kt           Layout tables for kana/alphabet/number/symbol modes
│   ├── KanaModifier.kt         Dakuten/handakuten/small-kana conversion table
│   ├── KanaConverter.kt        Hiragana to katakana to half-width katakana table
│   ├── FlickKeyboardView.kt    Keyboard rendering, flick detection, one-handed mode
│   ├── FlickAlternates.kt      Estimates likely mis-flick candidates (pure logic)
│   └── FlickGuideView.kt       The candidate popup shown while flicking
├── dict/
│   ├── UserDictionary.kt       User dictionary storage and prefix search
│   └── UserDictActivity.kt     User dictionary list / add / delete screen
├── edit/EditHistory.kt         Undo / redo stack
├── clip/
│   ├── ClipboardStore.kt       History storage (JSON)
│   └── ClipAdapter.kt          History list
└── mozc/
    ├── MozcEngine.kt           Singleton owning libmozc.so init (dictionary extraction, etc.)
    ├── MozcSession.kt          Wrapper for one conversion session (CREATE_SESSION through commit)
    └── CandidateAdapter.kt     Grid for the expanded candidate list

app/src/main/java/com/google/android/apps/inputmethod/libs/mozc/session/
└── MozcJNI.kt                  Thin JNI binding to libmozc.so (class name is fixed)
```

## Testing

51 JVM unit tests (`./gradlew testDebugUnitTest`).

- `FlickAlternatesTest` — estimating mis-flick candidates (neighboring key, wrong direction)
- `KeyLayoutsTest` — input-mode cycling and full-width layout generation
- `UserDictionaryTest` — user-dictionary prefix matching and candidate ordering
- `KanaModifierTest` — dakuten/handakuten/small-kana cycling logic
- `KanaConverterTest` — katakana / half-width katakana conversion tables
- `EditHistoryTest` — undo/redo merge behavior, deleting a selection, detecting external edits, etc.
  (`InputConnection` is faked via a Mockito delegate plus a real cursor-aware buffer)

## License

The code in this repository is released under the [MIT License](LICENSE).

However, `app/src/main/jniLibs/*/libmozc.so`, `app/src/main/assets/mozc.data`, and `app/libs/*.jar` (Mozc's protobuf definitions) come from [google/mozc](https://github.com/google/mozc) and are under the BSD 3-Clause License. Redistributing these build artifacts carries Mozc's own attribution requirements separately.
