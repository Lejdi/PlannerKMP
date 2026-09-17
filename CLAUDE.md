# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

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

Two features: `:feature:tasks` (tasks/TODOs) and `:feature:grocery` (grocery list).

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
| Export a SQLDelight schema after editing a `.sq` | `./gradlew generateCommonMainTasksDatabaseSchema` (and the `...GroceryDatabaseSchema` / `...KeyValueDatabaseSchema` equivalents) |

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
- detekt has no baseline and the build fails on any issue. Type resolution is
  deliberately not configured (KMP false positives around `expect`/`actual`), so the
  `coroutines` ruleset does not run.

CI (`.github/workflows/ci.yml`) is two jobs. `static-analysis` runs
`architectureCheck` then `detekt`. `build` runs the JVM tests, the Android debug
*and release* builds, an assertion that the Compose resource bundles are in the
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
- **`:feature:tasks`** — `Task`/`TaskDraft`/`TaskSchedule`/`TaskType`/`TaskField`.
  `TaskDraft.create(...)` is the only way to build a valid task and returns an `AppResult`
  naming *every* offending field. **When a task is due is a sealed `TaskSchedule`** —
  `Asap(createdOn)` | `OneTime(date)` | `Periodic(startDate, daysInterval, endDate)`, each
  with an optional `hour`, each carrying only the fields it has; the flat columns stay in
  the `.sq` schema (a schema is a storage format, not a domain model) with
  `SqlDelightTasksDatasource` owning the translation. The **recurrence rules live on the
  schedule** — `occursOn`, `occurrenceOnOrAfter`, `nextOccurrenceAfter`, `hasExpiredBy`,
  `startingFrom` — each a `when (this)`, so adding a kind breaks the build at every rule
  that must account for it. On top: `ObserveTasksForDashboard` (the 8-day window, a
  `FlowUseCase` combining the task flow with `todayFlow()`), `MarkTaskComplete` (takes a
  `Params(task, completedOn)` — a periodic task appears once per occurrence, so advancing
  from its own `startDate` moved the wrong one) and `UpdateTasksDates` (the daily cleanup:
  the pure `planCleanup` decides the day's changes and `TasksDatasource.runCleanup` reads,
  plans and applies them **inside one transaction**). The cleanup runs from
  `TasksCleanupInitializer`, an `AppInitializer` collecting `todayFlow()` for the app's
  lifetime — not from a ViewModel, and not from startup alone. Writes come in two shapes on
  purpose: `editTask` writes the whole row and only the edit form calls it; `rescheduleTask`
  writes the schedule columns only and is what completion and cleanup use, since both act on
  a `Task` read earlier and a whole-row update would push its stale name over later edits.
  `TaskEditViewModel` **observes** its row through `observeTask(id)`, seeding the form from
  the first emission only and using the subscription to notice the row being deleted. There
  is deliberately no one-shot `getTask`.
- **`:feature:grocery`** — `GroceryItem`/`GroceryItemDraft`/`GroceryField`, the
  `GroceryDatasource` port, its own `groceryItemEntity` schema, and `GroceryListScreen`
  (inline-expand add/edit rows driven by one `GroceryEditor` in the state). Completing an
  item is a permanent delete, paired with an **Undo** offer lasting exactly as long as the
  snackbar carrying it. Editing is a visible button as well as a long press.

### Rules

**Layering inside a feature is `ui -> domain <- data`.** Datasource *interfaces* live in
`domain/`, their SQLDelight implementations in `data/`. Business rules — validation, what a
cleanup changes — belong in `domain/`, never in a ViewModel.

**Use cases hold rules, not verbs.** A use case exists where there is policy: the dashboard
window, the cleanup, what completing a task means. Plain CRUD goes straight to the port —
`TaskEditViewModel` takes `TasksDatasource` directly rather than an `AddTask` class per
operation. The domain boundary is the port in `domain/`, not a class per verb.

**Screens** are a thin `@Composable` resolving the ViewModel plus a stateless `...Content`
composable taking the state and one `onEvent: (Event) -> Unit`; child composables never take
the ViewModel. All UI state — dialog visibility, the current message — lives in the
`MviState`; effects are for genuinely one-shot events such as navigation. State that is
*user input* additionally goes through `RestorableViewModel`.

**An event carries ids, never entities** — the same rule a `NavKey` follows. A whole `Task`
in an event plans from a snapshot captured when the card was drawn, ignoring the live list in
the ViewModel receiving it.

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
text — a new module needs an entry there or the task fails. A type appearing in a module's
public signature is declared with `api(...)`, not `implementation(...)`.

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
1.11.0, kotlinx.coroutines 1.11.0, kotlinx-datetime 0.6.1, detekt 1.23.8.
