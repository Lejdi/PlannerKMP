# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This is a Kotlin Multiplatform (KMP) + Compose Multiplatform app. `androidApp`
and `iosApp` are thin entry points; all logic and UI live in shared Kotlin
modules. The architecture is pure MVI (one `BaseViewModel` per screen calling
usecases, which call datasources through ports the domain layer owns), with
Koin for DI, Ktor for networking, SQLDelight for caching, and one Gradle module
per feature. The data layer is reactive: datasources expose
`Flow<AppResult<T>>` built on SQLDelight's query flows, so a write re-emits to
every observer and no screen re-queries on entry. Two feature modules
exist: `:feature:tasks` (tasks/TODOs) and `:feature:grocery` (grocery list) — see
`docs/superpowers/specs/2026-09-07-base-architecture-design.md` for the full
design and rationale behind the base/core module split below — note that path
is `.gitignore`d, so it exists only in a working copy that created it; this file
is the authority for everyone else.

## Commands

Build and test via the Gradle wrapper from the repo root (`./gradlew`, not a global `gradle`).

- Build Android app (debug APK): `./gradlew :androidApp:assembleDebug`
- Build the minified release APK: `./gradlew :androidApp:assembleRelease` (this is
  the only build that runs R8; run it before trusting a change to
  `proguard-rules.pro` or to anything reflective)
- Run all JVM unit tests (every module's `androidHostTest`): `./gradlew testAndroidHostTest` (or scope to one module, e.g. `./gradlew :core:mvi:testAndroidHostTest`)
- Run iOS tests (simulator) for a module: `./gradlew :core:mvi:iosSimulatorArm64Test`
- Run a single test class: append `--tests "pl.lejdi.plannerkmp.core.mvi.BaseViewModelTest"` to the relevant test task
- Run the iOS app: open `iosApp/iosApp.xcodeproj` in Xcode and run from there (not via Gradle)
- Static analysis: `./gradlew detekt` (config in `config/detekt/detekt.yml`; each module applies
  the plugin and the same `detekt { }` block to itself — *not* `allprojects { }` from the root,
  which is cross-project configuration and so incompatible with Gradle's isolated-projects mode.
  An unqualified task name still runs in every project. There is no baseline file and the build
  fails on any issue.) The ktlint-backed `detekt-formatting` ruleset is on, so
  `./gradlew detekt --auto-correct` fixes import order, indentation and unused imports in place.
  Type resolution is deliberately **not** configured: detekt's KMP support for it is limited and
  produces false positives around `expect`/`actual`, which is why the `coroutines` ruleset — every
  rule in it needs a `BindingContext` — does not run. Worth revisiting.
- Export a SQLDelight schema after changing a `.sq` file: `./gradlew generateCommonMainTasksDatabaseSchema` (and the `...GroceryDatabaseSchema` / `...KeyValueDatabaseSchema` equivalents)
- Prefer the IDE's run/gutter test configurations when working interactively; the Gradle tasks above are the CI-equivalent commands

CI (`.github/workflows/ci.yml`) is two jobs. `static-analysis` runs detekt on
Linux and uploads its reports on failure; `build` runs the JVM tests, the Android
debug build, **the Android release build**, an assertion that the Compose
resource bundles are in that APK, the iOS compilation for **both** targets, the
framework link, the iOS tests and an `xcodebuild` of the Xcode project, and
uploads the R8 `mapping.txt`. detekt is a separate job because as the first step
of one job a style finding meant you never learned whether the tests passed.

The release build is there because R8 runs in no other configuration: without it,
the shrinker and every keep rule were first exercised by whoever cut a release —
the one build where a missing rule surfaces as a crash rather than a compile
error. The resource assertion is there for the same kind of reason: Compose
resources are packaged as assets, the modules that own them have to opt into
Android resource processing, and when they did not the build still succeeded, the
tests still passed, and the app threw on the first `stringResource` it reached.
`iosArm64` is compiled and linked because it is the target that ships and was
built by no configuration at all.

Note that `iosSimulatorArm64Test` needs an installed simulator *runtime*, not
just the iOS SDK; on a machine where `xcrun simctl list runtimes` is empty it
fails with "Xcode does not support simulator tests" regardless of the code. It
matters more than it used to: the datasource, cache and DI-graph tests live in
`commonTest`, so this task is the only thing that exercises `NativeSqliteDriver`
and the iOS half of the Koin graph. `./gradlew linkDebugTestIosSimulatorArm64`
still checks that they compile and link on such a machine.

There is no `iosX64` target: Compose Multiplatform 1.11.1 and the JetBrains
lifecycle/navigation artifacts publish `iosArm64` and `iosSimulatorArm64` only, so
declaring it fails dependency resolution rather than producing an Intel build.

## Architecture

Module layout, declared in `settings.gradle.kts`:

- **`:androidApp`**, **`iosApp/`** (Xcode) — thin entry points. `androidApp`'s
  `PlannerApplication` calls `initKoin { androidContext(this) }`; iOS calls
  `initKoin()` once from `MainViewController()`. Neither contains business
  logic.
- **`:shared`** — the composition root. `Koin.kt` aggregates every module's
  Koin module via `initKoin()`; `App.kt` is the Compose root. It depends on
  every `:core:*` and every `:feature:*` module. `App.kt` names no feature: the
  bottom bar, the nav entries and the `NavKey` serializers all come from Koin
  multibindings (`FeatureTab`, `NavEntryProviderContributor`,
  `NavKeySerializersContributor`), so `Koin.kt`'s `featureModules` list is the
  only place a feature is named. `AppDependencyGraphTest` builds the whole graph
  against an in-memory database, so a missing binding fails the build instead of
  the app.
- **`:core:common`** — pure Kotlin: `AppResult` (with `map`/`flatMap`/`fold`/
  `onFailure` combinators, so no caller hand-unwraps a result — the set is kept
  to what something calls, not what a result type could have), `DomainError`
  (an open interface — `Network`, `Database`, `NotFound`, `Validation`,
  `Unknown` — so a feature can add its own) and `ValidationField`, likewise an interface:
  each feature declares its own enum (`TaskField`, `GroceryField`), because a
  closed enum here would mean every feature that gains an input edits the shared
  module. `UseCase<P, R>` and `FlowUseCase<P, R>` are separate functional
  interfaces (both here rather than in `:core:mvi`, so a domain layer never
  depends on the presentation module) — folding them together forces
  `suspend fun invoke(): Flow<T>`, which claims that obtaining a stream can
  suspend; both have a no-arg `invoke()` extension so a `Unit` parameter never
  reaches a call site. A `DomainError.Validation` carries a *set* of `ValidationField`s, not one field:
  a form with three bad inputs reports all three from one submit rather than one
  per round trip. It carries no per-field message — each failure used to, written
  in English in the domain layer, and no caller ever read it, so every rule's copy
  existed twice with nothing keeping the two in step. The field *is* the message;
  a screen that must tell two reasons for one input apart declares two fields. `DomainError.validationFields<F>()` is how a screen asks which
  of *its* fields are to blame, replacing a `(e as? Validation)?.field as? F`
  double cast that could only ever see the first.
  `DomainError.NotFound` is its own case rather than a
  `Database` failure: it is the one storage outcome that is not a malfunction
  and that a retry cannot fix, and folding it in left a screen editing a deleted
  row showing "save failed" forever with no way out. Also
  `CoroutineDispatchers`, `TodayProvider` — the single source of "today", a
  Koin-swappable wrapper over a `Clock` (defaulting to `Clock.System`, injected so
  the midnight rollover is reachable from a test at all) with **two**
  members: `today()` for a one-shot read, and `todayFlow()`, which emits again
  at each local midnight, and is `shareIn`-ed on the app scope so one timer
  serves every collector (with `replayExpirationMillis = 0`, so a late subscriber
  is never handed yesterday's date out of a replay cache). Anything that *renders* or *schedules against* a date
  must use the flow: the reactive data layer does not rescue a date read once,
  because a SQLDelight query flow re-emits only when its table is written, so a
  quiet night left the dashboard drawing yesterday's window under today's
  heading. Also `Logger` (the sink for every `DomainError.cause`, with
  logcat/stdout `platformLogger()` actuals; the no-op and recording
  implementations live in `:core:testing`, not here), and
  `AppInitializer` + `AppCoroutineScope` — the contract a feature implements to
  get work started at app start, on a scope that outlives any screen. An
  initializer may return or may collect for the app's lifetime (the daily
  cleanup does), so `:shared` gives each one its own coroutine.
- **`:core:network`** — Ktor wrapper: `createHttpClient()` (OkHttp on Android,
  Darwin on iOS, via `expect fun httpClientEngine()`), and
  `HttpClient.safeRequest<T>(logger, operation) { }`, which maps
  responses/exceptions to `AppResult<T>`. It takes a logger and an operation
  name for exactly the reason `safeQuery` does, and is tested for it: this is
  the single funnel where a transport exception becomes a value, so a failure
  not logged here is one nobody can diagnose. **Nothing in the app calls it, and
  `:shared` does not depend on it** — this is a local-first app with no backend.
  CI still builds and tests it against a future one, but depending on it put the
  whole Ktor + OkHttp stack into the release APK for an unused module, and
  registered a client the manifest asked for no INTERNET permission for. To wire
  it back: the `:core:network` dependency in `shared/build.gradle.kts` and the
  `networkModule()` call in `Koin.kt`, together, plus the permission.
- **`:core:database`** — SQLDelight wrapper: `DatabaseDriverFactory` (an
  interface implemented by `AndroidDatabaseDriverFactory` /
  `NativeDatabaseDriverFactory` and bound in each platform's Koin module — an
  interface rather than an `expect class` because the platforms need different
  constructors, and because a test can then substitute an in-memory driver for
  the whole graph) plus the generic
  `KeyValueCache<K, V>` contract (`get`/`put` only — what a caller actually
  uses, both returning `AppResult` like every other port, so no consumer has to
  re-wrap it to restore the convention) with a `SqlDelightKeyValueCache`
  implementation backed by this module's
  own small `keyValueEntry` schema, generated as `KeyValueDatabase` — the one
  schema this module owns itself; every other schema belongs to the feature
  module that needs it. The cache takes a **namespace** that is prefixed onto
  every row id, because the table is shared by every feature and two of them
  picking the same key string would otherwise overwrite each other through
  `INSERT OR REPLACE`, silently. Also `safeQuery(dispatchers, logger, operation) { }`,
  the datasource-side counterpart to `safeRequest`: it runs the block on
  `dispatchers.io` under a 5s timeout and maps the outcome (exceptions and
  timeouts included) to `AppResult<T>`. **`safeQuery` is for reads; every write
  goes through `safeMutation`, which is the same thing with no deadline.** A
  deadline on a write is not a safety net, it is what makes the write's outcome
  unknowable: the blocking driver call cannot be cancelled (see below), so a
  timeout reported `DomainError.Database("Database operation timed out")` — a
  definite failure — about an insert that then committed anyway. The screen said
  "save failed" over a saved task, and the obvious response to that, pressing Save
  again, wrote a second row; no re-entry guard can help, because the first write
  had already reported back. Without the deadline the outcome is determinate
  again, and that is SQLite's property rather than this function's: a statement is
  atomic and a transaction commits or rolls back, so a write that throws did not
  happen and one that returns did. Both drivers have their own busy timeout and
  surface a locked database as an ordinary exception, so the bound was never this
  layer's to enforce. The block runs on a coroutine that is
  deliberately **not** a child of the caller's, which is what makes the timeout
  able to fire at all: `withTimeout { withContext(io) { … } }` cannot resume the
  caller until its child finishes, and a SQLDelight query offers no cancellation
  point, so the timeout used to change nothing for exactly the workload it guards
  — which is the other half of why a write must not be given one. It **takes a logger and an operation
  name, not optionally** — this is the single funnel where a driver exception
  stops being a throwable and becomes a value, so logging here is what makes it
  structurally impossible to swallow one silently. `Flow<T>.asAppResult(logger,
  operation)` is the streaming counterpart for SQLDelight query flows, and
  `checkSingleRowAffected()` asserts a mutation's own returned row count — and
  distinguishes *zero* rows (a `RowNotFoundException`, which `safeQuery` maps to
  `DomainError.NotFound`) from a driver malfunction, because the UI has to be
  able to tell "this record was deleted" from "saving broke".
  Feature modules apply the SQLDelight Gradle plugin themselves for their own
  `.sq` schema and build their generated database from a driver obtained here. Every database exports its schema to
  `src/commonMain/sqldelight/databases/` with `verifyMigrations = true`, so
  changing a table without adding a `.sqm` migration fails the build instead of
  crashing on upgrade. Each `SqlDriver` is a Koin binding of its own with an
  `onClose`, because a driver created inline inside the database binding is
  unreachable afterwards and so could never be closed. Every `selectAll` carries
  an explicit `ORDER BY`: a bare `SELECT *` returns rows in whatever order the
  planner finds convenient, which SQL does not promise and which changes after a
  VACUUM. A datasource's observe chain ends in `flowOn(dispatchers.io)` —
  `mapToList(io)` confines only the query, so entity-to-domain mapping otherwise
  ran on the collector's thread, which is the UI thread.
- **`:core:mvi`** — `MviState`/`MviEvent`/`MviEffect` marker interfaces and
  `BaseViewModel<S, E, F>` (handler-style: `onEvent` calls `setState { }` /
  `sendEffect()` directly, no separate reducer function; `sendEffect` uses
  `trySend` on an unlimited channel, so effects keep the order they were raised
  in). It also owns `observe(key, source, onData, onError, onStart, onEmission)`:
  every screen here subscribes to a `Flow<AppResult<T>>` and folds it into
  state, and each one used to hand-roll the same job-cancel/loading/collect/fold
  sequence. Calling it again with the same `key` replaces that subscription,
  which is what makes a retry event a one-liner — a failed upstream flow is
  terminated, so only a fresh subscription recovers. **The `key` is what lets
  one screen observe two sources**; a single job field meant the second call
  silently cancelled the first. `onData` must stay pure — `updateAndGet` may
  re-run it under contention — so a side effect an emission triggers (navigating
  away because the record is gone) goes in `onEmission`, which runs exactly
  once per emission. `LoadableState<M>` is the fourth marker: the
  `isLoading`/`loadFailed`/`isEmpty`/`isSubmitting`/`message` set every screen
  has, with `hasTerminalLoadFailure` derived once instead of re-declared per
  feature. `message` is a `UiMessage<M>` — the typed value plus the identity of
  *this* raising of it, built through `BaseViewModel.raise`. Without that
  identity the snackbar host, which keys its effect on the value it is handed,
  never re-showed a message raised twice, so a second grocery completion inside
  the first one's undo window produced no acknowledgement at all. `isSubmitting` has no default, so a screen has to answer the question:
  every mutation here was fire-and-forget from a tap, and two quick taps on Save
  ran two inserts. A ViewModel refuses re-entry on it and the screen disables the
  control — both halves, because a live control through a slow write is what
  invites the second tap. On a *list* screen it is derived rather than stored: one
  flag for the whole screen disabled every other row and silently dropped a tap on
  it, so both list states keep a set of in-flight ids
  (`completingTaskIds`/`completingItemIds`) and answer `isCompleting(id)`.
  `RestorableViewModel<S, E, F, I>` adds saved-state restoration: a screen names
  a small `@Serializable` `I` holding *only what the user typed or chose*, and
  the base class writes it through a `SavedStateHandle` on every change and puts
  it back on construction. Loaded data is never saved — it is in the database
  and re-emits by itself. The input is encoded as a JSON string rather than via
  `encodeToSavedState`, because `SavedState` is a `Bundle` on Android and so
  could never be exercised by a JVM host test, and an untested restore path is a
  broken one — the same reason rules out `setSavedStateProvider`, whose payload is
  a `SavedState` too. The write is skipped when the captured input is unchanged:
  `onStateChanged` fires on every reduction, including the many that are data
  arriving rather than the user typing. This is the one `core:*` module besides the features
  that depends on `:core:common`, for `AppResult` in that signature.
- **`:core:testing`** — fixtures, and nothing else: `TestCoroutineDispatchers`,
  `RecordingLogger`/`NoOpLogger`, `FakeTodayProvider`, `InMemoryKeyValueCache`,
  `CloseTrackingSqlDriver` and `inMemorySqlDriver(schema)` — an `expect`/`actual`
  over the JDBC driver on the JVM host and `NativeSqliteDriver` on iOS. No
  production code depends on it, so nothing it contains ships. It exists because
  the dispatcher double was written out four times and the logger three, and
  because `core:common`'s own fake clock lived inside `:feature:tasks`. The
  in-memory driver is what let the storage tests move to `commonTest`, where they
  run against the driver that actually ships on each platform rather than against
  a JVM one neither does.
- **`:core:ui`** — `PlannerTheme` (colour scheme **and** type scale) plus the
  `Spacing`/`Sizing` tokens and the `itemTitle`/`dayHeading` type roles that
  replaced per-screen `dp` literals and
  `LocalTextStyle.current.copy(fontWeight = Bold)`; shared composables
  (`LoadingView`, `ErrorView`, `LoadableContent`, `MessageHost`); `format/` —
  `Month.displayName()`/`DayOfWeek.displayName()` over the platform's own locale
  data (`java.text.DateFormatSymbols` / `NSDateFormatter`, the *format* form on
  both, so "17 wrzesnia" rather than "17 wrzesien"), which replaced nineteen
  hand-written English string resources in `:feature:tasks`; and
  `CollectEffects(flow) { }`, the lifecycle-aware effect collector every screen
  uses. It owns a small `composeResources` set of genuinely generic wording
  (`core_action_retry`, `core_action_undo`, `core_action_ok`,
  `core_action_cancel`, `core_action_delete`, `core_error_generic`) that every
  feature was otherwise re-declaring; *feature* wording is still a parameter,
  because what failed is the feature's business. `MessageHost` is generic in the
  message type rather than taking `Any?`, and its action slot is named
  `actionLabel`/`onAction` because the same slot carries Retry and Undo. All of them are typed on plain values or on
  `Flow<T>` rather than on `BaseViewModel`, so `:core:ui` needs no dependency on
  `:core:mvi`. **`LoadableContent` and `MessageHost` are not optional
  conveniences**: the spinner/full-screen-error/content branch and the
  snackbar-with-retry sequence were written out per screen, the copies drifted,
  and the screen that had lost the error branch rendered a blank but fully live
  edit form over a task it had failed to read — where a Save wrote empty fields
  over the stored row. A screen adds them rather than re-deriving them.
- **`:core:navigation`** — Navigation 3 (`org.jetbrains.androidx.navigation3`)
  scaffolding: `Navigator` (wraps a `NavBackStack` and ignores a push of the key
  already on top), plus the three contracts each feature implements and
  Koin-multibinds — `NavEntryProviderContributor`, `FeatureTab` and
  `NavKeySerializersContributor` — so `:shared` can build the entry provider,
  the bottom bar and the back stack's `SerializersModule` without knowing which
  features exist. Each **must be a distinct class per feature**: Koin keys
  definitions by type, so two `single<FeatureTab>` declarations silently
  override each other and the app comes up with one tab.
  `NavAnimation.DURATION_MILLIS` is the one duration shared by the scene
  transition and any shared-element bounds transform — nav3's own default
  differs per platform (700ms Android, 500ms iOS), so a hardcoded bounds
  transform matched one platform and left a ghost frame on the other. Wired into
  `:shared`'s `App()`: one `SharedTransitionLayout` and one set of
  `entryDecorators` (including `rememberViewModelStoreNavEntryDecorator()`,
  without which every nav entry would share the Activity's `ViewModelStore` and
  so share one ViewModel instance per class) hoisted above the tab switch, with
  a `rememberNavBackStack` per tab so the stack and the selected tab survive
  rotation and process death. Every `NavKey` is `@Serializable` and carries ids,
  never entities.
- **`:feature:tasks`** — TODO/task list: `Task`/`TaskDraft`/`TaskSchedule`/
  `TaskType`/`TaskField` domain model (`TaskDraft.create(...)` is the only way to
  build a valid task and returns an `AppResult` whose `DomainError.Validation`
  names *every* offending field). **When a task is due is a sealed
  `TaskSchedule`** — `Asap(createdOn)` | `OneTime(date)` |
  `Periodic(startDate, daysInterval, endDate)`, each carrying only the fields it
  has. It replaced four flat fields (`asap`, `daysInterval`, `startDate`,
  `endDate`) with the kind *derived* from them, which made contradictions
  constructible and silent: `Task(asap = true, daysInterval = 5)` compiled and
  reported itself as ASAP while two of its own fields described something else.
  The flat columns remain in the `.sq` schema — a schema is a storage format, not
  a domain model — with `SqlDelightTasksDatasource` owning the one translation in
  each direction. The **recurrence rules live on the schedule** —
  `occursOn`, `occurrenceOnOrAfter`, `nextOccurrenceAfter`, `hasExpiredBy`,
  `startingFrom`, each a `when (this)` — because they used to be re-derived from
  the raw fields in the dashboard filter, the cleanup and "mark complete"
  independently, in three orders the compiler could not check. Adding a schedule
  kind now breaks the build at every rule that must account for it. On top of them:
  `ObserveTasksForDashboard` (the 8-day window, a `FlowUseCase`, which
  `combine`s the task flow with `TodayProvider.todayFlow()` so the window moves
  at midnight — the task table does not change at midnight, so nothing else
  would have triggered a recompute), `MarkTaskComplete`, and `UpdateTasksDates`
  (the daily cleanup — the pure `planCleanup` decides the whole day's changes and
  `TasksDatasource.runCleanup` reads, plans and applies them **inside one
  transaction**; reading through a separate call first meant the plan was computed
  from rows that could already have moved, so a task edited in between was rolled
  forward from an anchor that no longer existed). `MarkTaskComplete` takes a
  `Params(task, completedOn)`: a periodic task is on screen once per occurrence
  in the window, so advancing from the task's own `startDate` regardless of
  which card was ticked moved the wrong occurrence. The cleanup runs from
  `TasksCleanupInitializer`, an `AppInitializer` that `:shared` starts once and
  which then collects `todayFlow()` for the app's lifetime, **not** from the
  dashboard ViewModel: it is app-wide work, so hanging
  it off one screen meant it never ran for a user who stayed on another tab and
  ran again on every tab switch — and hanging it off *startup alone* meant a
  phone that never restarts the process went days without one. Writes come in two shapes on purpose: `editTask`
  writes the whole row and only the edit form calls it, while `rescheduleTask`
  writes the schedule columns only and is what "mark complete" and the cleanup
  use — both of those act on a `Task` read earlier, and sending that snapshot
  through a whole-row update pushed its stale name and description over anything
  edited since, reporting success. Plus the
  `TasksDatasource`/`CleanupDateStore`
  ports, its own `taskEntity` SQLDelight schema, and
  `DashboardScreen`/`TaskEditScreen`. `TaskEditViewModel` **observes** its row
  through `TasksDatasource.observeTask(id)` rather than reading it once: it
  seeds the form from the first emission only (so a later one cannot clobber
  what the user is typing) and uses the subscription to notice the row being
  deleted underneath it. There is deliberately no one-shot `getTask`. Its state
  nests a `TaskForm` holding everything the user typed, so `TaskEditInput` *is*
  that object and capture/apply are one line each — the ten fields used to be
  restated in five places, every one with a default, so adding a field compiled
  cleanly while silently not surviving a restart. It also holds `today` from
  `todayFlow()` rather than a one-shot read, so a form left open across midnight
  still defaults to the right day, and Delete goes through a `ConfirmDelete`
  dialog.
- **`:feature:grocery`** — grocery list: `GroceryItem`/`GroceryItemDraft`/
  `GroceryField` domain model, the `GroceryDatasource` port, its own
  `groceryItemEntity` SQLDelight schema, `GroceryListScreen` (inline-expand
  add/edit rows driven by one `GroceryEditor` in the state). Completing an item
  is a permanent delete — right for a shopping list, where a tap per item is the
  point — so it is paired with an **Undo** offer that lasts exactly as long as
  the snackbar carrying it. Editing is a visible button as well as a long press;
  the long press alone was an affordance nothing announced and nothing showed.

**Use cases hold rules, not verbs.** A use case exists where there is policy to
hold — the dashboard window, the cleanup, what completing a task means. Plain
CRUD goes straight to the port: `TaskEditViewModel` takes `TasksDatasource` and
`GroceryListViewModel` takes `GroceryDatasource`, rather than a one-line
`AddTask`/`EditGrocery` class per operation. The domain boundary is the port
living in `domain/`, not a class per verb.

**Layering inside a feature** is `ui -> domain <- data`: the datasource
*interfaces* live in `domain/` and their SQLDelight implementations in `data/`,
so the domain layer depends on nothing below it. Business rules — validation,
what a cleanup pass changes — belong in `domain/`, never in a ViewModel, because
a ViewModel is not the only possible caller.

**Screens** are a thin `@Composable` that resolves the ViewModel plus a
stateless `...Content` composable taking the state and one
`onEvent: (Event) -> Unit`; child composables never take the ViewModel. A screen
wires its state into `MessageHost` and `LoadableContent` from `:core:ui` rather
than writing the snackbar and loading/error branches out again. All UI
state, including dialog visibility and the current user-facing message, lives in
the `MviState` — effects are for genuinely one-shot events such as navigation.
State that is *user input* (a half-filled form, an open editor) additionally
goes through `RestorableViewModel`, because a plain `ViewModel` field does not
survive process death while the restored back stack puts the user right back on
the screen that lost it.
**An event carries ids, never entities** — the same rule a `NavKey` follows, for
the same reason. `DashboardEvent.CompleteTask` took the whole `Task` and so
planned the completion from a snapshot captured when the card was drawn, while
the live list it came from sat unread in the ViewModel that received it; it now
takes `taskId` and resolves it through `DashboardState.findTask`.
Messages are typed enums that the UI layer resolves to localized strings,
because a `DomainError`'s `message` is driver text meant for logs. Every
user-visible string is a Compose resource in the feature's
`composeResources/values/strings.xml` — including any string a shared `core:ui`
composable renders, which it therefore takes as a parameter rather than
hardcoding. The exception is the generic action vocabulary (Retry, Undo, OK,
Cancel, Delete), which lives in `core:ui`'s own resources as a *default* the
feature may still override. A control that opens a picker rather than a keyboard
carries its own semantics (`contentDescription` + `Role.Button`) and clears the
disabled text field's, which is otherwise announced as unavailable and skipped.
Each feature's stateless `...Content` composables have `@Preview`s in
`androidMain` covering the states that are awkward to reach by hand — a form with
several fields already marked invalid, a save in flight, a failed load.

Dependency rule: `feature -> core`, never the reverse. `core:network`,
`core:database`, and `core:mvi` depend on `core:common` only, never on each
other. `:core:testing` is the one module allowed to depend on several of them,
because it provides their fakes — and it is a `commonTest` dependency everywhere,
never a production one. A type that appears in a module's public signature is declared with
`api(...)`, not `implementation(...)` — otherwise consumers compile only by
accident, through somebody else's transitive dependency.

**Tests**: anything that is not platform-specific lives in `commonTest`, so it
runs on the JVM host *and* on iOS. That includes the SQLDelight datasource tests,
the key-value cache and the DI graph, which all used to sit in `androidHostTest` —
where `iosSimulatorArm64Test` never runs them, so the whole data layer was
verified on `JdbcSqliteDriver`, a driver that ships on neither platform, in
exactly the area where `checkSingleRowAffected` documents a native-only
divergence. `androidHostTest` is for things that genuinely need the JVM, like
pinning `Locale.getDefault()` to assert real weekday names.

Fakes belong in `commonTest` next to the port they fake, except the ones over
`core:*` types, which live in `:core:testing`. A fake's failure seam is per
operation and carries its own `DomainError` (`failNext(TasksWrite.Delete,
NotFound(...))`): a single "fail the next call" flag landed on whichever call came
next — which is how a test named for the cleanup's *write* ended up exercising its
read — and could only produce `Database`, leaving every `NotFound` branch
unreachable. A fake's observe stream can also fail *after* emitting
(`failLiveStreams()`), because that is what a dying driver does and it is the only
way to reach a screen's non-terminal error branch.

**Build infra**: each `core:*` module applies its KMP + Android + iOS target
config directly (`alias(libs.plugins.kotlinMultiplatform)` +
`alias(libs.plugins.androidMultiplatformLibrary)`, then `iosArm64()` /
`iosSimulatorArm64()` + an `android { }` block), the same pattern
`shared/build.gradle.kts` already used — copy an existing `core:*` module's
`build.gradle.kts` as the starting point for a new one. The same duplication runs through the per-module `detekt { }` block. Three ways
of removing it have now been tried and reverted, each confirmed by testing rather
than assumed:
  - a **`build-logic` included-build convention plugin** breaks the generated
    `libs.*` version-catalog accessors in every *consuming* module's script body;
  - the same convention plugin in **`buildSrc`** puts `kotlin-dsl`'s own Kotlin
    Gradle plugin on every project's build-script classpath, where it shadows
    this build's 2.4.10 and configuration fails with
    `NoClassDefFoundError: KotlinMultiplatformExtension`. Excluding detekt's
    transitive KGP does not help — `kotlin-dsl` itself contributes it;
  - a script plugin applied with **`apply(from = ...)`** is compiled without the
    applying project's plugin classpath, so it cannot name `DetektExtension` at
    all.
(Gradle 9.1 / Kotlin 2.4.10 / AGP 9.0.1.) Revisit if a newer Gradle fixes it;
until then the duplication is the cheapest of the four options.

Toolchain versions and plugin IDs are centralized in `gradle/libs.versions.toml` (a standard Gradle version catalog) and referenced everywhere as `libs.xxx` / `libs.plugins.xxx` — add new dependencies there rather than hardcoding coordinates in module `build.gradle.kts` files.

Release builds run R8 (`isMinifyEnabled` + `isShrinkResources`), and CI builds
one on every push so the rules stay honest. `proguard-rules.pro` deliberately
keeps `SourceFile`/`LineNumberTable`: with no crash reporter in the app, an
unreadable release stack trace is an unexplainable bug. For the same reason it
keeps the *names* of `RestorableViewModel` subclasses, `AppInitializer`
implementations and every `Throwable` — all three reach the log through
`::class.simpleName` or a class name, which R8 renames. It deliberately carries no
kotlinx.serialization rules: the library ships its own consumer rules, and the
ones that used to be here restated them without the `@Serializable` guard, so they
pinned the companion of every class in the app.

The version lives once, in `version.properties` at the root: `androidApp` reads it
as a properties file and `iosApp/Configuration/Config.xcconfig` `#include`s it, so
the two platforms cannot drift. Release signing reads `PLANNER_KEYSTORE` and
friends from the environment when they are set, and stays unsigned when they are
not — CI only wants to know that R8 runs.

`compose.ui-tooling` is the preview *renderer* and must stay out of release —
only `:androidApp` depends on it, via `debugImplementation`. Library modules
take `ui-tooling-preview` (the annotations) and nothing more; a plain
`implementation(libs.compose.uiTooling)` in a KMP module's `androidMain` lands
in every variant and silently undoes that. Only
`material-icons-core` is on the classpath — an icon outside that set has to be
vendored rather than pulled from the frozen `material-icons-extended` artifact.

Toolchain: Gradle 9.1 (via wrapper), Kotlin 2.4.10, AGP 9.0.1, JVM target 11 for Kotlin/Android compilation, Java toolchain 21 (Azul) for the Gradle daemon itself (`gradle/gradle-daemon-jvm.properties`). Android `compileSdk`/`targetSdk` 36, `minSdk` 24. Ktor 3.5.2, Koin 4.1.1 (pinned per artifact, not a BOM — no `platform()` is declared anywhere), SQLDelight 2.3.2, Navigation 3 1.1.1, kotlinx.serialization 1.11.0, kotlinx.coroutines 1.11.0, kotlinx-datetime 0.6.1.
