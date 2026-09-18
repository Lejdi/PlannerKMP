# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

**This file is how to build a feature here — conventions, structure, commands, and the rules
that keep them.** It deliberately describes no product behaviour. What each feature *does*,
and why, lives in [`docs/features/`](docs/features/README.md), one document per module. When a
change alters behaviour, update the feature document in the same commit; when it alters how
modules are wired or built, update this file.

## Documentation map

| Where | What it is |
| --- | --- |
| `CLAUDE.md` (this file) | How to build in this repo: module graph, conventions, rules, commands, build infra |
| [`docs/features/`](docs/features/README.md) | What each feature does and why — the domain models, rules and observable behaviour |
| [`docs/architecture-blueprint.md`](docs/architecture-blueprint.md) | The prescriptive build order for starting a *new* KMP + Compose app, distilled from this one |
| [`docs/specs/`](docs/specs/) | Dated, historical reference — notably the legacy pure-Android app's business logic |

## Project status

A Kotlin Multiplatform + Compose Multiplatform app. `androidApp` and `iosApp` are
thin entry points; all logic and UI live in shared Kotlin modules. Pure MVI (one
`BaseViewModel` per screen calling usecases or ports directly), Koin for DI,
SQLDelight for storage, one Gradle module per feature.

Local-first: there is no backend. `:core:network` is built and tested against a
future one, but nothing calls it and `:shared` does not depend on it.

The data layer is reactive — datasources expose `Flow<AppResult<T>>` over
SQLDelight query flows, so a write re-emits to every observer and no screen
re-queries on entry.

Four features: `:feature:tasks` (tasks/TODOs), `:feature:grocery` (grocery list),
`:feature:routines` (daily routines) and `:feature:gym` (a weekly gym plan). What each one
does is in [`docs/features/`](docs/features/README.md).

## Commands

Use the Gradle wrapper from the repo root (`./gradlew`).

| Task | Command |
| --- | --- |
| Android debug APK | `./gradlew :androidApp:assembleDebug` |
| Android release APK (the only build that runs R8) | `./gradlew :androidApp:assembleRelease` |
| All JVM unit tests | `./gradlew testAndroidHostTest` (scope with `:core:mvi:testAndroidHostTest`) |
| iOS tests for a module | `./gradlew :core:mvi:iosSimulatorArm64Test` |
| A single test class | append `--tests "pl.lejdi.plannerkmp.core.mvi.BaseViewModelTest"` |
| Static analysis | `./gradlew detekt` (add `--auto-correct` to fix formatting) |
| Architecture rules | `./gradlew architectureCheck` |
| Export a SQLDelight schema after editing a `.sq` | `./gradlew generateCommonMainTasksDatabaseSchema` (and the `...GroceryDatabaseSchema` / `...RoutinesDatabaseSchema` / `...GymDatabaseSchema` / `...KeyValueDatabaseSchema` equivalents) |

Run the iOS app from Xcode (`iosApp/iosApp.xcodeproj`), not via Gradle.

Notes:

- Run the **release** build before trusting a change to `proguard-rules.pro` or to
  anything reflective — R8 runs in no other configuration.
- `iosSimulatorArm64Test` needs an installed simulator *runtime*, not just the SDK.
  Where `xcrun simctl list runtimes` is empty it fails with "Xcode does not support
  simulator tests" regardless of the code; `./gradlew linkDebugTestIosSimulatorArm64`
  still checks that those tests compile and link. This matters because the
  datasource, cache and DI-graph tests live in `commonTest`, so it is the only task
  exercising `NativeSqliteDriver` and the iOS half of the Koin graph.
- **Anything that links an iOS binary needs the iOS 26 SDK, so Xcode 26.** Compose
  Multiplatform 1.11.1 ships a prebuilt `libCMPUIKitUtils.a` referencing
  `UIViewLayoutRegion`, UIKit `API_AVAILABLE(ios(26.0))`, and auto-linking
  `UIUtilities.framework`, which exists in no earlier SDK. Under Xcode 16 the link dies
  with "Undefined symbols for architecture arm64: `_OBJC_CLASS_$_UIViewLayoutRegion`" —
  the *debug test* binaries first, because their compiler cache links that archive whole
  while the release framework link drops the object nothing reaches. Compose 1.12.0
  resolves the symbol at runtime, so this ends with that bump.
- detekt has no baseline and the build fails on any issue. Type resolution is
  deliberately not configured (KMP false positives around `expect`/`actual`), so the
  `coroutines` ruleset does not run.

CI (`.github/workflows/ci.yml`) is two jobs. `static-analysis` runs
`architectureCheck` then `detekt` on `ubuntu-latest`. `build` runs on **`macos-26`**,
whose default Xcode is 26.x, for the SDK reason above — `macos-15` carries Xcode 26 as
well, but only alongside the 16.4 it actually selects — and covers the JVM tests, the
Android debug *and release* builds, an assertion that the Compose resource bundles are in the
release APK, compilation for **both** iOS targets, the `iosArm64` framework link,
the iOS tests and an `xcodebuild` of the Xcode project. detekt is a separate job so
a style finding does not hide whether the tests pass.

## Architecture

Modules, declared in `settings.gradle.kts`:

- **`:androidApp`**, **`iosApp/`** — thin entry points. Android's
  `PlannerApplication` calls `initKoin { androidContext(this) }`; iOS calls
  `initKoin()` from `MainViewController()`. No business logic in either.
- **`:shared`** — composition root. `Koin.kt` aggregates every module's Koin module;
  `App.kt` is the Compose root and **names no feature**: bottom bar, nav entries and
  `NavKey` serializers all arrive through Koin multibindings, so `Koin.kt`'s
  `featureModules` list is the only place a feature is named. `AppDependencyGraphTest`
  builds the whole graph against an in-memory database.
- **`:core:common`** — pure Kotlin. `AppResult` with `map`/`flatMap`/`fold`/`onFailure`;
  `DomainError` (an open interface: `Network`, `Database`, `NotFound`, `Validation`,
  `Unknown`) and `ValidationField`, also an interface — each feature declares its own
  enum (`TaskField`, `GroceryField`). A `Validation` error carries a *set* of fields
  and no message, so one submit reports every bad input; `DomainError.validationFields<F>()`
  is how a screen asks which of its own fields are to blame. `NotFound` is separate from
  `Database` because it is the one storage outcome a retry cannot fix. `UseCase<P, R>`
  and `FlowUseCase<P, R>` are separate functional interfaces (here, not in `:core:mvi`,
  so a domain layer never depends on presentation). Also `CoroutineDispatchers`,
  `Logger`, `AppInitializer` + `AppCoroutineScope`, and `TodayProvider` — the single
  source of "today", with `today()` for a one-shot read and `todayFlow()`, which
  re-emits at each local midnight and is shared on the app scope. **Anything that
  renders or schedules against a date must use the flow**: a query flow re-emits only
  when its table is written, so a quiet night left the dashboard drawing yesterday's
  window.
- **`:core:network`** — Ktor wrapper: `createHttpClient()` (OkHttp / Darwin via
  `expect fun httpClientEngine()`), `NetworkConfig`, and
  `HttpClient.safeRequest<T>(logger, operation) { }` mapping responses and exceptions
  to `AppResult<T>`. Unused — to wire it back, add the `:core:network` dependency in
  `shared/build.gradle.kts`, the `networkModule()` call in `Koin.kt`, and the INTERNET
  permission, together.
- **`:core:database`** — SQLDelight wrapper. `DatabaseDriverFactory` (an interface, not
  an `expect class`, so platforms can have different constructors and a test can
  substitute an in-memory driver); `KeyValueCache<K, V>` (`get`/`put`, both returning
  `AppResult`) with a `SqlDelightKeyValueCache` over this module's own `keyValueEntry`
  schema — it takes a **namespace** prefixed onto every row id, because the table is
  shared and two features picking one key string would overwrite each other. Plus:
  - `safeQuery(dispatchers, logger, operation) { }` — **reads**: runs on
    `dispatchers.io` under a 5s timeout, maps everything to `AppResult<T>`.
  - `safeMutation` — **writes**: the same with no deadline. The blocking driver call cannot
    be cancelled, so a timeout only made the outcome unknowable — it reported failure over an
    insert that then committed. Without it, a write that throws did not happen and one that
    returns did.
  - `Flow<T>.asAppResult(logger, operation)` for query flows.
  - `checkSingleRowAffected()` — asserts a mutation's returned row count and maps *zero*
    rows to `DomainError.NotFound`, so the UI can tell "deleted" from "broken".

  Both funnels take a logger and an operation name, **not optionally** — this is where a
  driver exception becomes a value, so logging here is what makes swallowing one
  impossible. Feature modules apply the SQLDelight plugin for their own `.sq` schema and
  get a driver from here. Every database exports its schema to
  `src/commonMain/sqldelight/databases/` with `verifyMigrations = true`. Each `SqlDriver`
  is its own Koin binding with an `onClose`. Every `selectAll` carries an explicit
  `ORDER BY`. A datasource's observe chain **ends in `flowOn(dispatchers.io)`** —
  `mapToList(io)` confines only the query, leaving entity mapping on the UI thread.
- **`:core:mvi`** — `MviState`/`MviEvent`/`MviEffect` markers and
  `BaseViewModel<S, E, F>` (handler-style: `onEvent` calls `setState { }` / `sendEffect()`
  directly, no separate reducer). It owns:
  - `observe(key, source, onData, onError, onStart, onEmission)` — subscribe to a
    `Flow<AppResult<T>>` and fold it into state; `observeValues` is the variant for a
    source that cannot fail (`todayFlow()`). Re-calling with the same `key` replaces that
    subscription, which makes a retry event a one-liner. **The `key` is what lets one
    screen observe two sources.** `onData` must stay pure (`updateAndGet` may re-run it);
    per-emission side effects go in `onEmission`.
  - `LoadableState<M>` — the `isLoading`/`loadFailed`/`isEmpty`/`isSubmitting`/`message`
    set, with `hasTerminalLoadFailure` derived once. `message` is a `UiMessage<M>`: the
    value plus the identity of *this* raising, built through `BaseViewModel.raise`, so a
    message raised twice re-shows. `isSubmitting` has no default — a ViewModel refuses
    re-entry on it *and* the screen disables the control. On a list screen it is derived
    from a set of in-flight ids (`completingTaskIds` / `completingItemIds`), so one slow
    write does not disable every other row.
  - `RestorableViewModel<S, E, F, I>` — saved-state restoration. A screen names a small
    `@Serializable` `I` holding **only what the user typed or chose**; loaded data is never
    saved, since it re-emits from the database. Encoded as a JSON string rather than via
    `encodeToSavedState`, because `SavedState` is a `Bundle` on Android and so unreachable
    from a JVM host test. The write is skipped when the captured input is unchanged.
- **`:core:testing`** — fixtures only, a `commonTest` dependency everywhere, never a
  production one: `TestCoroutineDispatchers`, `RecordingLogger`/`NoOpLogger`,
  `FakeTodayProvider`, `InMemoryKeyValueCache`, `CloseTrackingSqlDriver`, and
  `inMemorySqlDriver(schema)` — an `expect`/`actual` over the JDBC driver on the JVM host
  and `NativeSqliteDriver` on iOS, which is what lets the storage tests live in
  `commonTest` and run against the driver that actually ships.
- **`:core:ui`** — `PlannerTheme` (colour scheme and type scale), `Spacing`/`Sizing`
  tokens and the `itemTitle`/`dayHeading` type roles; shared composables (`LoadingView`,
  `ErrorView`, `LoadableContent`, `MessageHost`, `FieldError`/`PlainTextField`);
  `format/` — `Month.displayName()`/`DayOfWeek.displayName()` over the platform's own
  locale data in its *format* form ("17 września", not "17 wrzesień"), plus
  `toPickerMillis`/`toPickerDate`; and `CollectEffects(flow) { }`, the lifecycle-aware
  effect collector every screen uses. It owns a small `composeResources` set of generic
  vocabulary (`core_action_retry`/`_undo`/`_ok`/`_cancel`/`_delete`, `core_error_generic`)
  as *defaults* a feature may override; *feature* wording stays a parameter. Everything is
  typed on plain values or `Flow<T>`, so `:core:ui` needs no dependency on `:core:mvi` or
  `:core:common`. **`LoadableContent` and `MessageHost` are not optional** — a screen adds
  them rather than re-deriving the spinner/error/content branch and the snackbar sequence.
- **`:core:navigation`** — Navigation 3 scaffolding: `Navigator` (wraps a `NavBackStack`,
  ignores a push of the key already on top), `LocalNavigator`,
  `LocalSharedTransitionScope`, `NavAnimation.DURATION_MILLIS` (the one duration shared by
  the scene transition and any shared-element bounds transform — nav3's own default differs
  per platform), and the three contracts each feature implements and Koin-multibinds:
  `NavEntryProviderContributor`, `FeatureTab`, `NavKeySerializersContributor`. Each **must
  be a distinct class per feature** — Koin keys definitions by type, so two
  `single<FeatureTab>` declarations silently override each other. `:shared`'s `App()`
  hoists one `SharedTransitionLayout` and one set of `entryDecorators` (including
  `rememberViewModelStoreNavEntryDecorator()`, without which every nav entry shares the
  Activity's `ViewModelStore`) above the tab switch, with a `rememberNavBackStack` per tab.
  Every `NavKey` is `@Serializable` and carries ids, never entities.
- **`:feature:*`** — one module per user-facing area, each owning its domain model and
  validation, its storage port and `.sq` schema, its screens and ViewModels, its Koin module,
  its navigation contributions and its own `composeResources`. **What each one does, and why,
  is in [`docs/features/`](docs/features/README.md) — not here.** This file is how to build a
  feature; those files are what the features do.

  | Module | Owns | Business logic |
  | --- | --- | --- |
  | `:feature:tasks` | `Task`/`TaskDraft`/`TaskSchedule`/`TaskType`/`TaskField`, `TasksDatasource`, `taskEntity`, `ObserveTasksForDashboard`, `MarkTaskComplete`, `UpdateTasksDates`, `TasksCleanupInitializer` | [tasks.md](docs/features/tasks.md) |
  | `:feature:grocery` | `GroceryItem`/`GroceryItemDraft`/`GroceryField`, `GroceryDatasource`, `groceryItemEntity` | [grocery.md](docs/features/grocery.md) |
  | `:feature:routines` | `Routine`/`RoutineDraft`/`TodayRoutine`/`RoutineField`, `RoutinesDatasource`, `routineEntity`, `ObserveRoutinesForToday`, `ToggleRoutineDone` | [routines.md](docs/features/routines.md) |
  | `:feature:gym` | `GymExercise`/`GymExerciseDraft`/`DayExercise`/`GymDay`/`GymField`, `GymDatasource`, `gymExerciseEntity`, `ObserveGymWeek`, `ToggleExerciseSet` | [gym.md](docs/features/gym.md) |

  `:feature:tasks` is the one feature with an `AppInitializer`; the other three need no
  overnight job at all, for the reason *Derive against the clock* below gives.

### Rules

**Layering inside a feature is `ui -> domain <- data`.** Datasource *interfaces* live in
`domain/`, their SQLDelight implementations in `data/`. Business rules — validation, what a
cleanup changes — belong in `domain/`, never in a ViewModel.

**Use cases hold rules, not verbs.** A use case exists where there is policy: the dashboard
window, the cleanup, what completing a task means. Plain CRUD goes straight to the port —
`TaskEditViewModel` takes `TasksDatasource` directly rather than an `AddTask` class per
operation. The domain boundary is the port in `domain/`, not a class per verb.

**A valid entity is only constructible through its draft.** Each feature has a
`<Thing>Draft` whose constructor is private and whose `create(...)` returns an `AppResult`
naming *every* offending field, plus an `internal ofStored(...)` for rows that were validated
when they were written. The stored type's constructor is `internal` for the same reason: while
it was public, "the only way to build a valid one" held for the draft and not for the type
every other layer actually handles, which took a public constructor and a public `copy`. An id
is real or the thing is a draft — nothing reads `0L` as "not saved yet".

**Model the states that exist, not the fields they need.** Where a kind of thing has
kind-dependent fields, it is a sealed type carrying only the fields that kind has
(`TaskSchedule`), and every rule over it is an exhaustive `when` — so adding a kind breaks the
build at each rule that must account for it. Flags plus derived kinds make contradictions
constructible and silent.

**A schema is a storage format, not a domain model.** Flat, legacy or denormalised columns are
fine in the `.sq` file; the datasource owns the translation. Do not reshape the domain to
match the table, or the table to match the domain.

**Derive against the clock rather than storing what something must clear.** A row stores the
date a thing was done (`completedOn`), never a boolean; "done today" is computed by combining
the query flow with `TodayProvider.todayFlow()`. The reset is then free — no cleanup job, no
history table, nothing to run overnight, and no drift after the app has been shut for a week.
`:feature:tasks` has an `AppInitializer` only because its rules genuinely *mutate* rows
(deleting expired tasks, re-anchoring periodic ones); where a derivation will do, use one.
Anything deriving from a date must combine with `todayFlow()`, not read the date off each
emission: a query flow re-emits only when its table is written, so a quiet night leaves the
screen on yesterday.

**One write per set of columns its writer owns.** When several writers touch a row without
having read each other's work, the port gets a method per group of columns — `editTask` /
`rescheduleTask`, `updateDetails` / `updateCompletedOn`, `updateDetails` / `updateWeight` /
`updateCompletedSets`. A whole-row update from a writer holding a stale copy overwrites the
other columns and SQLite reports success. Counting the writers is how you know how many
methods to declare.

**A bound that exists for a UI reason is still a domain rule.** `MAX_SETS` is 20 because the
list draws one checkbox per series; it lives in the domain and the screen reads the same
constant. A number the UI cannot render is bad data, not a layout problem.

**Screens** are a thin `@Composable` resolving the ViewModel plus a stateless `...Content`
composable taking the state and one `onEvent: (Event) -> Unit`; child composables never take
the ViewModel. All UI state — dialog visibility, the current message — lives in the
`MviState`; effects are for genuinely one-shot events such as navigation. State that is
*user input* additionally goes through `RestorableViewModel`.

**An event carries ids, never entities** — the same rule a `NavKey` follows. A whole `Task`
in an event plans from a snapshot captured when the card was drawn, ignoring the live list in
the ViewModel receiving it.

**A screen that edits a row observes it**, through `observeX(id)`, seeding its form from the
first emission only and using the subscription to notice the row being deleted underneath it.
There is deliberately no one-shot `getX`: a snapshot turns a save into a read-modify-write
over data another writer may already have moved. A form that never loaded counts as *empty*,
so a failed load cannot render a blank but live form whose Save overwrites the real row.

**Match a destructive affordance to how often the action is taken and what it costs to get
wrong.** A tap made forty times a shopping trip gets no dialog and an undo offer lasting
exactly as long as its snackbar; a rare, deliberate delete gets a confirmation. A destructive
confirmation is never restored after process death — it would come back under a thumb already
moving towards where the confirm button was.

**Strings**: messages are typed enums the UI resolves (a `DomainError`'s `message` is driver
text for logs). Every user-visible string is a Compose resource in the feature's
`composeResources/values/strings.xml`, including any string a `:core:ui` composable renders —
which it therefore takes as a parameter. The exception is the generic action vocabulary in
`:core:ui`. A control that opens a picker rather than a keyboard carries its own
`contentDescription` + `Role.Button` and clears the disabled text field's.

**Module dependencies**: `feature -> core`, never the reverse and never feature -> feature.
`:core:database`, `:core:mvi` and `:core:network` see `:core:common` only, never each other;
`:core:ui` and `:core:navigation` see nothing. `:core:testing` may reach several, because it
provides their fakes, and is test-only everywhere. **This is enforced** by the allowlist in
the root `build.gradle.kts` (`./gradlew architectureCheck`), which reads the build scripts as
text — a new module needs an entry there or the task fails. That last clause was untrue until
`:feature:routines` was added: the task took its file list from the allowlist's own keys, so an
unlisted module was never scanned and went silently unconstrained, which is the one failure an
allowlist exists to prevent. It now discovers the build scripts from disk. A type appearing in a
module's public signature is declared with `api(...)`, not `implementation(...)`.

**Tests**: anything not platform-specific lives in `commonTest`, so it runs on the JVM host
*and* on iOS — including the datasource, key-value cache and DI graph tests.
`androidHostTest` is for things that genuinely need the JVM, like pinning
`Locale.getDefault()` to assert real weekday names. Fakes live in `commonTest` next to the
port they fake, except fakes over `core:*` types, which live in `:core:testing`. A fake's
failure seam is **per operation** and carries its own `DomainError`
(`failNext(TasksWrite.Delete, NotFound(...))`); it can also fail *after* emitting
(`failLiveStreams()`), which is the only way to reach a screen's non-terminal error branch.
Each feature's stateless `...Content` composables have `@Preview`s in `androidMain` covering
the states that are awkward to reach by hand.

## Build infra

Each module applies its own KMP + Android + iOS config (`alias(libs.plugins.kotlinMultiplatform)`
+ `alias(libs.plugins.androidMultiplatformLibrary)`, then `iosArm64()` / `iosSimulatorArm64()`
and an `android { }` block) and its own `detekt { }` block. **Copy an existing `core:*`
module's `build.gradle.kts` as the starting point for a new one.**

That duplication is deliberate — three ways of removing it were tried and reverted:
a `build-logic` convention plugin breaks the generated `libs.*` accessors in consuming
scripts; the same plugin in `buildSrc` puts `kotlin-dsl`'s own KGP on every build-script
classpath, shadowing this build's 2.4.10; and `apply(from = ...)` compiles without the
applying project's plugin classpath, so it cannot name `DetektExtension`. Revisit on a newer
Gradle. For the isolated-projects reason, detekt is configured per module rather than from
`allprojects { }` — an unqualified `./gradlew detekt` still runs everywhere.

There is no `iosX64` target: Compose Multiplatform and the JetBrains lifecycle/navigation
artifacts publish `iosArm64` and `iosSimulatorArm64` only, so declaring it fails dependency
resolution rather than producing an Intel build.

Dependencies are centralized in `gradle/libs.versions.toml` and referenced as `libs.xxx` /
`libs.plugins.xxx` — add them there rather than hardcoding coordinates.

Release builds run R8 (`isMinifyEnabled` + `isShrinkResources`). `proguard-rules.pro`
deliberately keeps `SourceFile`/`LineNumberTable` (there is no crash reporter, so an
unreadable release trace is an unexplainable bug) and the *names* of `RestorableViewModel`
subclasses, `AppInitializer` implementations and every `Throwable` — all three reach the log
through a class name, which R8 renames. It carries no kotlinx.serialization rules: the library
ships its own.

`compose.ui-tooling` is the preview *renderer* and must stay out of release — only
`:androidApp` depends on it, via `debugImplementation`. Library modules take
`ui-tooling-preview` (the annotations) and nothing more; a plain
`implementation(libs.compose.uiTooling)` in a KMP module's `androidMain` lands in every
variant. Only `material-icons-core` is on the classpath — an icon outside that set has to be
vendored.

The version lives once, in `version.properties`: `androidApp` reads it as a properties file
and `iosApp/Configuration/Config.xcconfig` `#include`s it, so the platforms cannot drift.
Release signing reads `PLANNER_KEYSTORE` and friends from the environment when set, and stays
unsigned when not.

**Toolchain**: Gradle 9.1 (wrapper), Kotlin 2.4.10, AGP 9.0.1, JVM target 11 for
Kotlin/Android compilation, Java toolchain 21 (Azul) for the Gradle daemon. Android
`compileSdk`/`targetSdk` 36, `minSdk` 24. Compose Multiplatform 1.11.1, Koin 4.1.1 (pinned per
artifact, no BOM), SQLDelight 2.3.2, Navigation 3 1.1.1, Ktor 3.5.2, kotlinx.serialization
1.11.0, kotlinx.coroutines 1.11.0, kotlinx-datetime 0.7.1, detekt 1.23.8. Xcode 26 (iOS 26
SDK) for anything that links an iOS binary.
