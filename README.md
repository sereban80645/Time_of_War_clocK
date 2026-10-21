# Time_of_War_clocK

A separate smartwatch project for Time of War, targeting Wear OS watches from Samsung and other manufacturers. The original `time_of_war_new` project is not modified.

## Platform strategy

- **Primary target:** Wear OS (Samsung Galaxy Watch running Wear OS, Google Pixel Watch, and other compatible Wear OS devices).
- **Not a universal watch APK:** Garmin/watchOS and Huawei models running non-Wear-OS systems require separate platform apps and cannot install this Android package.
- **Phone companion integration:** The current phone app's settings are documented as the source of truth. A subsequent integration step must add Wear OS Data Layer synchronization to the phone project and this watch app. Until that is integrated and device-tested, settings on the watch are stored locally and are not automatically synchronized.

## Preserved setting contract

The watch app is designed to support the same options as the phone app: show the 24 Feb 2022 counter, show the 20 Feb 2014 counter, days-only or years/months/days, show/hide hours, font size, outline width, background opacity, RGB text/background/outline colors, and a locally stored background image.

## Build

Open this project in Android Studio with JDK 17+ and Android SDK / Wear OS SDK installed, or run `./gradlew :app:assembleDebug`. GitHub Actions builds the watch APK on each push and manual run.

## Acceptance checks

1. App installs on a Wear OS emulator/device and starts without crashing.
2. Both counters use the exact event start times: 2022-02-24 02:40 and 2014-02-20 12:00 (device local time, matching the phone implementation).
3. Settings persist after closing/reopening the app.
4. The phone project remains unchanged.
5. Data Layer sync, Tile, and complication are separate follow-up deliverables and must not be called complete until implemented and tested.
