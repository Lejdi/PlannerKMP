# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This is a Kotlin Multiplatform (KMP) + Compose Multiplatform project scaffolded from the JetBrains wizard template. Aside from the package name (`pl.lejdi.plannerkmp`) and project name (`PlannerKMP`), the codebase is currently unmodified boilerplate: `App.kt` is the wizard's "Click me!" / greeting demo, `Greeting`/`GreetingUtil`/`Platform` are the template's sample shared logic, and all tests are the placeholder `assertEquals(3, 1 + 2)`. There is no `.git` repository initialized yet. Treat existing files as a starting skeleton, not established architecture to preserve.

## Commands

Build and test via the Gradle wrapper from the repo root (`./gradlew`, not a global `gradle`).

- Build Android app (debug APK): `./gradlew :androidApp:assembleDebug`
- Run Android unit tests (JVM host tests for the `shared` module): `./gradlew :shared:testAndroidHostTest`
- Run iOS tests (simulator): `./gradlew :shared:iosSimulatorArm64Test`
- Run a single test class: append `--tests "pl.lejdi.plannerkmp.SharedCommonTest"` to the relevant test task
- Run the iOS app: open `iosApp/iosApp.xcodeproj` in Xcode and run from there (not via Gradle)
- Prefer the IDE's run/gutter test configurations when working interactively; the Gradle tasks above are the CI-equivalent commands

There is no linter/formatter configured in this repo (no ktlint/detekt/spotless plugin present as of this writing).

## Architecture

Standard KMP module layout, declared in `settings.gradle.kts`:

- **`shared/`** — Kotlin Multiplatform library module (`com.android.kotlin.multiplatform.library` + `org.jetbrains.kotlin.multiplatform` plugins) targeting `android`, `iosArm64`, and `iosSimulatorArm64`. All app UI (Compose Multiplatform) and business logic is meant to live here.
  - `src/commonMain/kotlin` — shared code and shared Compose UI (`App.kt` is the composable root).
  - `src/commonMain/composeResources` — shared resources (images, etc.) accessed via generated `Res` accessors (package `plannerkmp.shared.generated.resources`).
  - `src/androidMain/kotlin`, `src/iosMain/kotlin` — platform-specific implementations of `expect` declarations (see `Platform.kt` / `Platform.android.kt` / `Platform.ios.kt` for the pattern). `MainViewController.kt` in `iosMain` is the iOS entry point into the shared Compose UI (`ComposeUIViewController { App() }`).
  - `src/commonTest`, `src/androidHostTest`, `src/iosTest` — test source sets per target; `androidHostTest` runs on the JVM (not a device/emulator) per the `withHostTest` config in `shared/build.gradle.kts`.
- **`androidApp/`** — thin Android application module (`com.android.application` plugin). `MainActivity.kt` just calls `enableEdgeToEdge()` and renders the shared `App()` composable; there is no Android-only UI beyond this shell.
- **`iosApp/`** — native Xcode project (SwiftUI entry point `iOSApp.swift` / `ContentView.swift`) that hosts the compiled `shared` framework (`baseName = "Shared"`, static framework, built from the two iOS targets in `shared/build.gradle.kts`).

Dependency versions and plugin IDs are centralized in `gradle/libs.versions.toml` (a standard Gradle version catalog) and referenced everywhere as `libs.xxx` / `libs.plugins.xxx` — add new dependencies there rather than hardcoding coordinates in module `build.gradle.kts` files.

Toolchain: Gradle 9.1 (via wrapper), Kotlin 2.4.10, AGP 9.0.1, JVM target 11 for Kotlin/Android compilation, Java toolchain 21 (Azul) for the Gradle daemon itself (`gradle/gradle-daemon-jvm.properties`). Android `compileSdk`/`targetSdk` 36, `minSdk` 24.
