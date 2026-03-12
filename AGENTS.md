# Repository Guidelines

## Project Structure & Module Organization
- `app/`: Android application module with Compose UI, activities, and resources under `app/src/main`.
- `sdk/bili/`: Bilibili SDK module (networking, auth, protobuf/grpc) under `sdk/bili/src/main`.
- `docs/`: Engineering notes and product/SDK references (e.g., `docs/engineering.md`, vendor PDFs).
- Shared build config lives in `build.gradle.kts`, `settings.gradle.kts`, and `gradle/libs.versions.toml`.

## Build, Test, and Development Commands
- `./gradlew assembleDebug`: Build a debug APK for local installs.
- `./gradlew :app:installDebug`: Build and install the debug APK on a connected device/emulator.
- `./gradlew test`: Run JVM unit tests (see `app/src/test`).
- `./gradlew connectedAndroidTest`: Run instrumentation tests on a device (see `app/src/androidTest`).
- `./gradlew lint`: Run Android Lint for common issues.
- 在任何交付前请用 `gradlew` 与 `adb` 做开发和验证：优先用 `./gradlew :app:installDebug` 构建并安装调试 APK，安装完成后用 `adb shell am start -n com.lightningstudio.watchrss/.MainActivity` 打开应用，最后通过 `adb logcat` 检查日志并定位问题直到应用不报错。
- 当 `adb` 未连接或设备不可用时，必须执行 `./gradlew assembleDebug` 并确保编译通过后再继续其他工作。

## Coding Style & Naming Conventions
- Kotlin/Java with 4-space indentation and standard Android Studio formatting.
- Package naming follows `com.lightningstudio.watchrss` (feature code under subpackages like `ui`, `sdk`).
- Compose UI files live in `app/src/main/java/.../ui` and should keep composables small and previewable.
- Prefer descriptive class names for screens (`*Screen`), activities (`*Activity`), and SDK components (`Bili*`).

## Testing Guidelines
- Unit tests: JUnit (see `app/src/test/java/...`).
- Instrumented tests: AndroidX test runner (see `app/src/androidTest/java/...`).
- Name tests after behavior (e.g., `BiliAuthTest`, `MainActivityTest`), and keep device tests focused on UI or integration flows.

## Commit & Pull Request Guidelines
- Commit history favors short, descriptive messages (often in Chinese), e.g., `新增...`, `修正...`, `Update README.md`.
- Keep commits focused; include module or feature context when helpful.
- PRs should include a clear description, testing notes, and screenshots or screen recordings for UI changes.
- Link related issues or docs (e.g., `docs/features.md`, `docs/engineering.md`) when changing behavior.

## Security & Configuration Tips
- `local.properties` is machine-specific (Android SDK path). Do not commit secrets.
- Vendor binaries live under `app/libs/` (e.g., vendor widget AAR); document any updates in PRs.
