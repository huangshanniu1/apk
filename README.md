# AutoRun Studio

AutoRun Studio is a prototype Android automation recorder and test runner intended for an original game or controlled QA environment. It provides a floating control ball, gesture recording, editable action timelines, replay, and a local screen-vision pipeline.

## Features

- Floating accessibility overlay ball with recording and playback controls.
- Gesture recording using a transparent accessibility overlay.
- Action types: `tap`, `long_press`, and `swipe`.
- White touch markers during recording and draggable timeline points in the editor.
- Saved automation cases in app-local JSON storage.
- Accessibility-based gesture playback through `dispatchGesture()`.
- MediaProjection screen capture with an offline bitmap vision pipeline.
- Purple-region detection using HSV sampling.
- Search-button template detection using `assets/search_template.png`.
- Configurable throttling to avoid repeated actions.
- GitHub Actions workflow that creates the Gradle Wrapper on the runner and builds a debug APK.

## Build on GitHub

1. Create an empty GitHub repository.
2. Upload the contents of this folder.
3. Open **Actions** and run **Build APK** (or push a commit).
4. Download the `AutoRunStudio-debug-apk` artifact from the workflow run.

The workflow uses JDK 17 and Gradle 9.1, matching the Android Gradle Plugin 9.0.1 compatibility requirements.

## Local build

Install Android Studio with SDK Platform 36 and a JDK 17 installation. Then run:

```bash
gradle wrapper --gradle-version 9.1.0 --distribution-type bin
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Android permissions and services

Enable the app's Accessibility Service in Android Settings. The service is used for its overlay and gesture injection APIs.

For visual automation, use the **Screen Capture Setup** button and approve MediaProjection. The app then starts a foreground service that samples screen frames locally.

## Recording model

The recorder uses a full-screen accessibility overlay while recording. Because the overlay receives touch input in order to capture coordinates, the prototype re-dispatches the recorded gesture after the touch sequence ends. This is intentionally simple and can feel less transparent on some devices.

For a production recorder, replace `RecorderOverlayView` with a device-specific input capture strategy and add stronger state recovery.

## Vision model

The default vision engine contains two independent rules:

1. `SearchTemplateDetector` scans for a local template image named `search_template.png`.
2. `PurpleRegionDetector` samples pixels in HSV space and emits a purple-item detection when the configured minimum pixel count is reached.

Replace the template asset with a screenshot crop from your own game. For a complex game, the detector interface can be replaced by a small local TFLite object detector without changing the action engine.

## Case format

Automation cases are JSON objects with normalized 0..1 coordinates. This keeps actions portable across screen sizes.

## Safety and scope

Use this project for your own game, offline test scenes, accessibility experiments, or QA automation. Do not use it to bypass anti-cheat systems or automate a third-party multiplayer game without permission.

## UI Language

The Android app interface is Chinese. Source code, identifiers, comments, configuration keys, and automation action values remain in English.
