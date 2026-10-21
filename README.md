# Time_of_War_clocK

A separate, offline-first Wear OS smartwatch project. The original `time_of_war_new` repository is not modified, and this watch app does not require that phone app to be installed.

## Target devices

- Wear OS watches, including Samsung Galaxy Watch models running Wear OS, Google Pixel Watch, and other compatible Wear OS devices.
- Garmin, Apple Watch, and Huawei devices running non-Wear-OS platforms cannot install this APK; they would need separate platform-specific apps.

## Current implementation

- Standalone Wear OS app with local settings stored on the watch.
- Offline Wear OS Tile that reads settings on the watch and opens the watch app when tapped.
- Two event toggles for 24 February 2022 at 02:40 and 20 February 2014 at 12:00.
- Days-only or years/months/days display, optional hours, font size, outline width, background opacity, RGB text/outline/background colors, and a locally copied custom background image.
- GitHub Actions builds a debug APK on pushes to `main`.

## Important status

This is still an early prototype, not a feature-complete release. Tile UI and counter formatting require validation against the source app's exact arithmetic and on physical watches. Built-in background presets, a watch-face/complication implementation, comprehensive automated date tests, and testing across device manufacturers remain follow-up work. A Tile is a Wear OS system surface, not a universal home-screen widget on every watch platform.

## Build

Open in Android Studio with JDK 17 and Android SDK / Wear OS SDK installed, or run:

```bash
./gradlew :app:assembleDebug
```

GitHub Actions publishes `app-debug.apk` as a workflow artifact.
