# Base architecture design

Date: 2026-09-07
Status: approved

## Goal

Replace the JetBrains wizard boilerplate with the base structure for a Kotlin
Multiplatform + Compose Multiplatform app: `androidApp`/`iosApp` as thin entry
points, all logic and UI shared in Kotlin, pure MVI per screen, Koin for DI,
Ktor for networking, SQLDelight for caching, each feature its own Gradle
module, JUnit-based unit tests throughout.

This pass builds the skeleton only — base/core modules and wiring, no real
feature module yet.

## Module graph

```
:androidApp, :iosApp (Xcode)          -- entry points, depend only on :shared
:shared                                -- composition root: initKoin(), App() + NavDisplay host
  depends on all :core:* (and, later, all :feature:*)

:core:common     -- pure Kotlin: Result/DomainError, CoroutineDispatchers
:core:network    -- Ktor wrapper (HttpClient factory, safe-request helper -> core:common Result)
:core:database   -- SQLDelight wrapper (DatabaseDriverFactory expect/actual, base cache abstraction)
:core:mvi        -- BaseViewModel<S,E,F>, MviState/MviEvent/MviEffect, UseCase base class
:core:ui         -- design-system building blocks (theme, LoadingView/ErrorView, etc.)
:core:navigation -- Navigation 3 route/key registry that features plug into

:feature:*       -- none yet; each future one depends on whichever core:* modules
                    it needs and contributes a Koin module + NavKeys
```

Dependency rule: `feature -> core`, never the reverse. `core:network`,
`core:database`, and `core:mvi` depend on `core:common` only, never on each
other. `:shared` depends on all `core:*` modules to wire Koin and navigation.
`:androidApp`/`:iosApp` depend only on `:shared`.

## Data flow (MVI)

Screen composable collects `StateFlow<State>` and calls
`viewModel.onEvent(Event)` -> `BaseViewModel` (handler-style: functions update
state via `setState { }` and emit one-off `Effect`s via a `Channel`) invokes a
`UseCase` -> `UseCase` calls a feature-owned `DataSource` -> `DataSource` uses
`core:network`'s `HttpClient` wrapper and/or `core:database`'s SQLDelight
queries, returning `core:common`'s `Result`/`DomainError` -> the ViewModel maps
errors to an `Effect` (e.g. `ShowError`).

Handler-style was chosen over a single reducer function for simplicity per
feature while still keeping strict unidirectional flow (State/Event/Effect).

## Dependency injection (Koin)

Each core/feature module exposes its own `val xModule = module { ... }`.
`:shared` has `fun initKoin(appDeclaration: KoinAppDeclaration = {})`
aggregating every module's Koin module. `androidApp` gets a new `Application`
subclass calling `initKoin { androidContext(this) }` (registered in
`AndroidManifest.xml`). The iOS entry point calls `initKoin()` once at
startup.

## Navigation

Using **Navigation 3** (`org.jetbrains.androidx.navigation3`) rather than the
older NavHost-based Navigation — it is the JetBrains-recommended multiplatform
approach as of Compose Multiplatform 1.10+, and this project is on 1.11.1.
Each feature defines its own `NavKey` entries and composable registration;
`core:navigation` holds the shared registry contract; `:shared` hosts the
single `NavDisplay`.

## Testing

Same pattern the template already uses: `commonTest` (kotlin-test, which runs
as JUnit via the `androidHostTest` target) in every core module, testing base
classes in isolation — e.g. `BaseViewModel` state/effect emission, `Result`
mapping helpers — with no Android/iOS device required.

## Build infra

**Deviation from the approved design:** a `build-logic` included build with
convention plugins (`plannerkmp.kmp.library`, `plannerkmp.kmp.compose`) was
implemented first, to avoid every core module repeating the same KMP +
Android + iOS target boilerplate `shared/build.gradle.kts` already had.
During verification, applying a plugin sourced from that included build broke
Gradle's generated `libs.*` version-catalog accessors in the *consuming*
module's script body (confirmed empirically: identical code compiled with
plugins applied directly, failed with the same plugins applied via the
included-build convention plugin) — a real limitation of this Gradle 9.1 /
Kotlin 2.4.10 / AGP 9.0.1 combination, not a configuration mistake. Given the
choice between an elegant but broken build and a working one, `build-logic`
was removed and every `core:*` module applies its plugins directly instead,
matching `shared/build.gradle.kts`'s existing pattern. Revisit build-logic if
a newer Gradle version resolves this.

## Pinned versions

Verified current-stable as of 2026-09-07, alongside the existing Kotlin
2.4.10 / Compose Multiplatform 1.11.1 / AGP 9.0.1:

| Library | Version |
|---|---|
| Ktor | 3.5.2 |
| Koin | 4.1.1 (pinned directly per artifact — Koin's BOM can't be applied inside a Kotlin Multiplatform source-set `dependencies { }` block, only in a regular project `dependencies { }` block) |
| SQLDelight | 2.3.2 |
| Navigation 3 (`org.jetbrains.androidx.navigation3`) | 1.1.1 |
| kotlinx.serialization | 1.11.0 |
| kotlinx.coroutines | 1.11.0 |

## Out of scope (deferred)

- Any real feature module — this pass is the skeleton only.
- Material 3 Adaptive Navigation 3 support (`adaptive-navigation3`), only
  needed once a feature wants adaptive/multi-pane layouts.
- ktlint/detekt or other linting — not present in the repo today.
