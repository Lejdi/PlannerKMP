# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This is a Kotlin Multiplatform (KMP) + Compose Multiplatform app. `androidApp`
and `iosApp` are thin entry points; all logic and UI live in shared Kotlin
modules. The architecture is pure MVI (one `BaseViewModel` per screen calling
usecases, which call datasources), with Koin for DI, Ktor for networking,
SQLDelight for caching, and one Gradle module per feature. There is no real
feature module yet — see
`docs/superpowers/specs/2026-09-07-base-architecture-design.md` for the full
design and rationale behind the base/core module split below.

## Commands

Build and test via the Gradle wrapper from the repo root (`./gradlew`, not a global `gradle`).

- Build Android app (debug APK): `./gradlew :androidApp:assembleDebug`
- Run all JVM unit tests (every module's `androidHostTest`): `./gradlew testAndroidHostTest` (or scope to one module, e.g. `./gradlew :core:mvi:testAndroidHostTest`)
- Run iOS tests (simulator) for a module: `./gradlew :core:mvi:iosSimulatorArm64Test`
- Run a single test class: append `--tests "pl.lejdi.plannerkmp.core.mvi.BaseViewModelTest"` to the relevant test task
- Run the iOS app: open `iosApp/iosApp.xcodeproj` in Xcode and run from there (not via Gradle)
- Prefer the IDE's run/gutter test configurations when working interactively; the Gradle tasks above are the CI-equivalent commands

There is no linter/formatter configured in this repo (no ktlint/detekt/spotless plugin present as of this writing).

## Architecture

Module layout, declared in `settings.gradle.kts`:

- **`:androidApp`**, **`iosApp/`** (Xcode) — thin entry points. `androidApp`'s
  `PlannerApplication` calls `initKoin { androidContext(this) }`; iOS calls
  `initKoin()` once from `MainViewController()`. Neither contains business
  logic.
- **`:shared`** — the composition root. `Koin.kt` aggregates every module's
  Koin module via `initKoin()`; `App.kt` is the Compose root. It depends on
  every `:core:*` module (and, once one exists, every `:feature:*` module).
- **`:core:common`** — pure Kotlin: `AppResult`/`DomainError` (the result type
  every datasource/usecase returns instead of throwing), `CoroutineDispatchers`.
- **`:core:network`** — Ktor wrapper: `createHttpClient()` (OkHttp on Android,
  Darwin on iOS, via `expect fun httpClientEngine()`), and `HttpClient.safeRequest<T> { }`,
  which maps responses/exceptions to `AppResult<T>`.
- **`:core:database`** — SQLDelight wrapper: `DatabaseDriverFactory` (`expect`/`actual`,
  no common constructor since Android needs a `Context` and iOS doesn't — each
  platform's Koin module in `di/DatabaseModule.<platform>.kt` supplies the
  actual instance) plus the generic `KeyValueCache<K, V>` contract (with an
  `InMemoryKeyValueCache` usable as a test fake). Feature modules apply the
  SQLDelight Gradle plugin themselves for their own `.sq` schema and build
  their generated `Database` from a driver obtained here — this module does
  not itself define any schema.
- **`:core:mvi`** — `MviState`/`MviEvent`/`MviEffect` marker interfaces,
  `BaseViewModel<S, E, F>` (handler-style: `onEvent` calls `setState { }` /
  `sendEffect()` directly, no separate reducer function), and the generic
  `UseCase<P, R>` functional interface.
- **`:core:ui`** — `PlannerTheme` plus shared composables (`LoadingView`,
  `ErrorView`) reused across feature screens.
- **`:core:navigation`** — Navigation 3 (`org.jetbrains.androidx.navigation3`)
  scaffolding: `Navigator` (holds the `NavKey` back stack) and
  `NavEntryProviderContributor` (the contract each feature implements and
  Koin-multibinds so `:shared` can build one `entryProvider` via
  `getKoin().getAll<NavEntryProviderContributor>()` without knowing which
  features exist). **Not yet wired into `:shared`'s `App()`** — there's no
  screen to navigate to until the first feature module exists; wire
  `NavDisplay` up then.
- **`:feature:*`** — none yet. Each one depends on whichever `:core:*` modules
  it needs, owns its own MVI contract (`State`/`Event`/`Effect` extending the
  `core:mvi` marker interfaces), its own datasources, and contributes a Koin
  module + `NavEntryProviderContributor`.

Dependency rule: `feature -> core`, never the reverse. `core:network`,
`core:database`, and `core:mvi` depend on `core:common` only, never on each
other.

**Build infra**: each `core:*` module applies its KMP + Android + iOS target
config directly (`alias(libs.plugins.kotlinMultiplatform)` +
`alias(libs.plugins.androidMultiplatformLibrary)`, then `iosArm64()` /
`iosSimulatorArm64()` + an `android { }` block), the same pattern
`shared/build.gradle.kts` already used — copy an existing `core:*` module's
`build.gradle.kts` as the starting point for a new one. A `build-logic`
included-build convention plugin was tried first to remove this duplication,
but applying a plugin from an included build broke the generated `libs.*`
version-catalog accessors in every *consuming* module's script body (Gradle
9.1 / Kotlin 2.4.10 / AGP 9.0.1 — confirmed by testing, not a config mistake)
in this Gradle/Kotlin/AGP toolchain, so it was reverted; revisit if a newer
Gradle version fixes it.

Dependency versions and plugin IDs are centralized in `gradle/libs.versions.toml` (a standard Gradle version catalog) and referenced everywhere as `libs.xxx` / `libs.plugins.xxx` — add new dependencies there rather than hardcoding coordinates in module `build.gradle.kts` files.

Toolchain: Gradle 9.1 (via wrapper), Kotlin 2.4.10, AGP 9.0.1, JVM target 11 for Kotlin/Android compilation, Java toolchain 21 (Azul) for the Gradle daemon itself (`gradle/gradle-daemon-jvm.properties`). Android `compileSdk`/`targetSdk` 36, `minSdk` 24. Ktor 3.5.2, Koin 4.1.1 (BOM), SQLDelight 2.3.2, Navigation 3 1.1.1, kotlinx.serialization 1.11.0, kotlinx.coroutines 1.11.0.
