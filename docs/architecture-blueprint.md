# KMP + Compose architecture blueprint

A build order for a new Kotlin Multiplatform + Compose Multiplatform application, distilled from
PlannerKMP. It assumes the shape that project settled on: shared Kotlin UI *and* logic, thin platform
entry points, local-first storage, one Gradle module per feature, pure MVI.

**This document is prescriptive about structure, and about libraries only where the structure depends
on them.** Swap SQLDelight for Room and most of Phase 1 still holds; swap MVI for something else and
Phase 2 does not.

**Every entry carries the failure it prevents.** That is the point of the document rather than
decoration. A rule stated without its failure gets dropped by the next project the first time it is
inconvenient — and most of the rules below exist because a specific bug shipped. If you are going to
skip an entry, skip it having read *why*, not because it read as ceremony.

## How to read an entry

Each decision is `D<n>` so it can be cited in review.

- **Rule** — what to do.
- **Why** — the failure mode it prevents. Where a bug is named, it happened.
- **Enforce** — how the build catches a violation. `review only` is a real answer and is marked as
  such, because it tells you which rules will decay.

You can port Phase 1's code from the **Rule** lines alone and come back for the rationale. Do come
back before deleting any of it.

## Read this before copying versions

The toolchain PlannerKMP pinned, as of this writing: Gradle 9.1 (wrapper), Kotlin 2.4.10, AGP 9.0.1,
JVM target 11 for Kotlin/Android compilation, Java toolchain 21 for the Gradle daemon,
`compileSdk`/`targetSdk` 36, `minSdk` 24, Compose Multiplatform 1.11.1, Koin 4.1.1 (pinned per
artifact, no BOM), SQLDelight 2.3.2, Navigation 3 1.1.1, kotlinx.coroutines 1.11.0,
kotlinx.serialization 1.11.0, kotlinx-datetime 0.6.1, detekt 1.23.8.

Three of those move fast enough that you must check them rather than copy them:

- **Navigation 3** (`1.1.1`) is the youngest dependency here and the one most of `core:navigation` is
  written against. Entries marked *(nav3-specific)* are the ones to re-derive.
- **`compose-material3` on Multiplatform** was on an alpha (`1.11.0-alpha07`) while Compose itself was
  stable. Expect the two to need separate version refs.
- **The AGP KMP library plugin** (`com.android.kotlin.multiplatform.library`) is what replaced
  per-module `androidLibrary` config. Its defaults shifted inside the 9.x line — notably Android
  resource processing being off by default, which D23 covers.

Everything else here is about shape and survives a version bump.

---

# Phase 0 — Repo shape

Do this first and completely. Every later phase assumes the module graph exists and is enforced.

## D1 — Platform entry points contain no logic

**Rule.** `androidApp` and `iosApp/` are launchers. Android's `Application` calls
`initKoin { androidContext(this) }`; iOS calls `initKoin()` from `MainViewController()`. Between them
that is single digits of Kotlin and Swift. All UI and all logic live in shared modules.

**Why.** Anything in a platform entry point exists once per platform: written twice, tested once,
drifts. The two things that legitimately live there — the Android `Context` and the iOS view
controller — are exactly the two that cannot be shared.

**Enforce.** `architectureCheck` (D4) allows `:androidApp` exactly one dependency, `:shared`.

## D2 — Three module tiers: `core:*`, `feature:*`, `:shared`

**Rule.**

```
androidApp ─┐
            ├─→ :shared ──→ feature:a, feature:b ──→ core:*
iosApp ─────┘          └──→ core:*
```

- `core:*` — infrastructure with no product knowledge. Start with `common`, `database`, `mvi`, `ui`,
  `navigation`, `testing`. Add `network` when there is a backend.
- `feature:*` — one module per user-facing area, each owning its own domain model, storage schema,
  screens, DI module and navigation contributions.
- `:shared` — the composition root. Aggregates Koin modules, hosts the Compose root. The only module
  that may name both a feature and a core module.

**Why.** Feature-per-module is what makes D3 expressible at all — inside one module, "the domain layer
must not see the database" is a naming convention and nothing more. It also bounds the blast radius of
a change and keeps each module small enough to reason about, which matters both for people and for a
coding agent that has to hold it in context.

**Enforce.** `settings.gradle.kts` plus D4's allowlist.

## D3 — The dependency rules, stated once

**Rule.**

| Module | May depend on |
| --- | --- |
| `core:common` | nothing |
| `core:ui` | nothing |
| `core:navigation` | nothing |
| `core:database` | `core:common` |
| `core:mvi` | `core:common` |
| `core:network` | `core:common` |
| `core:testing` | `core:common`, `core:database` — **test-only everywhere** |
| `feature:*` | every `core:*` except `network` / `testing`; **never another feature** |
| `:shared` | every `core:*` + every feature |
| `androidApp` | `:shared` |

Three sub-rules that are easy to miss:

- **`core:database`, `core:mvi` and `core:network` never see each other.** This is the rule that stops
  a domain layer acquiring a ViewModel or a driver by accident.
- **`core:ui` and `core:navigation` see nothing at all**, not even `core:common` (see D33).
- A type appearing in a module's *public* signature is declared `api(...)`, not `implementation(...)`.

**Why.** `feature → feature` is the edge that kills a modular build. It is always one small reuse that
looks harmless, and it ends with two features that cannot be built, tested or deleted independently.
`core:testing` reaching several modules is the deliberate exception — it provides their fakes — and is
exactly why it must never be a production dependency, or the fakes ship in the release binary.

**Enforce.** D4.

## D4 — Enforce the dependency rules with a task, not a document

**Rule.** Write a `verifyModuleDependencies` Gradle task holding the D3 table as an allowlist, wire it
into an `architectureCheck` task, and run it in CI *before* the linter. **A module with no allowlist
entry fails the task** rather than being silently unconstrained — that is the failure mode an
allowlist usually has.

Read the build *scripts as text*, not the Gradle project model:

```kotlin
val DEPENDENCY_BLOCK = Regex("""\b(\w+)\.dependencies\s*\{""")
val PROJECT_DEPENDENCY = Regex("""project\("(:[^"]+)"\)""")
```

Track whether the current `*.dependencies { }` block is a test one, so `core:testing` is permitted
there and rejected everywhere else.

**Why.** Two separate reasons, both load-bearing:

- **A rule with no check has a half-life.** In PlannerKMP these rules were a paragraph of
  documentation and a comment in nine build scripts, checked by nobody — and the codebase had already
  drifted around other rules held that same way (a datasource missing its `flowOn`, two datasources
  disagreeing about the same row check). The rules that *were* checked had not drifted.
- **Reading text rather than the project model is what keeps it compatible with Gradle's
  isolated-projects mode.** Asking another project for its configuration is precisely what that mode
  forbids; reading a file is not. As a bonus, what is checked is what a reviewer reads.

**Enforce.** Itself. If you implement one thing from Phase 0, implement this — it is what makes the
rest durable.

## D5 — Duplicate the module build scripts on purpose

**Rule.** Each module applies its own KMP + Android + iOS config and its own `detekt { }` block.
**Copy an existing `core:*` module's `build.gradle.kts` as the starting point for a new one.** Do not
factor the duplication out.

**Why.** Three ways of removing it were tried and reverted. Record them so nobody spends the day
again:

1. **A convention plugin in an included build** breaks the generated `libs.*` version-catalog
   accessors in every consuming script.
2. **The same plugin in `buildSrc`** puts `kotlin-dsl`'s own Kotlin Gradle Plugin on every build
   script's classpath, where it shadows the build's own KGP and `KotlinMultiplatformExtension` stops
   resolving. Excluding transitives does not help — `kotlin-dsl` itself contributes it.
3. **`apply(from = ...)`** compiles the applied script without the applying project's plugin
   classpath, so it cannot so much as name `DetektExtension`.

Revisit on a newer Gradle. Until then the duplication is the cheapest correct option, and it is the
same isolated-projects constraint as D4 that keeps detekt configured per module rather than from
`allprojects { }`.

**Enforce.** Review only. An unqualified `./gradlew detekt` still runs every module's task, because
Gradle matches an unqualified task name across all projects — so a module that forgot its block shows
up as one that never reports findings.

## D6 — `iosArm64` and `iosSimulatorArm64`, no `iosX64`

**Rule.** Declare exactly those two iOS targets in every module.

**Why.** Compose Multiplatform and the JetBrains lifecycle/navigation artifacts publish those two
only. Declaring `iosX64` does not produce an Intel build — it fails dependency resolution. Upstream's
choice, not a preference, so re-check it rather than assume it.

**Enforce.** The build fails. Worth a comment in one build script so nobody adds it back.

## D7 — The app version lives in one file both platforms read

**Rule.** Put `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` in `version.properties` at the root.
`androidApp/build.gradle.kts` reads it as a properties file; `iosApp/Configuration/Config.xcconfig`
`#include`s it. **Comment that file with `//`** — a comment in both formats, where `#` is not: an
xcconfig tries to read a `#` line as a preprocessor directive.

**Why.** The two numbers otherwise live once per platform build file — two places to remember, and
nothing that notices when they disagree.

**Enforce.** Review only, but disagreement becomes impossible rather than unnoticed.

## D8 — One version catalog, and annotate it

**Rule.** All coordinates in `gradle/libs.versions.toml`, referenced as `libs.*` / `libs.plugins.*`.
Never hardcode a coordinate in a module script.

Two entries worth copying verbatim, both about binary size and both easy to get wrong:

- `material-icons-core`, **not** `material-icons-extended` — the extended artifact is frozen and links
  thousands of vectors into every binary. An icon outside the core set gets vendored by hand.
- `detekt-formatting` (the ktlint-backed ruleset) as a `detektPlugins` dependency. Without it nothing
  enforces import order, indentation or trailing commas, and that drift starts immediately.

**Why.** The catalog itself is standard; the annotation is not. It is the one place a "why this
artifact and not the obvious one" note gets read *before* somebody swaps it.

**Enforce.** Review only.

---

# Phase 1 — Core primitives

This is the phase worth porting close to verbatim. Each module below is small — a handful of files —
and together they are what makes a feature module short.

## `core:common`

Pure Kotlin. No Compose, no database, no ViewModel.

### D9 — Carry failure as a value, not an exception

**Rule.**

```kotlin
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: DomainError) : AppResult<Nothing>
}
```

with `map` / `flatMap` / `fold` / `onFailure` as `inline` extensions. Inline matters: it lets
`transform` suspend when the caller is a suspend function.

Every datasource, cache and network operation returns `AppResult`. Nothing below the UI throws.

**Why.** An exception crossing a module boundary is an untyped, undeclared return value. Two concrete
consequences: a caller cannot see in the signature that an operation can fail, and there is no single
place where a driver exception becomes something the UI can render. D17's funnels depend on this type
existing.

**Enforce.** Review only, but D17 makes the correct path the shortest one.

### D10 — `DomainError` is an open interface, and `NotFound` is its own case

**Rule.**

```kotlin
interface DomainError {
    val message: String
    val cause: Throwable?

    data class Network(...) : DomainError
    data class Database(...) : DomainError
    data class NotFound(...) : DomainError
    data class Validation(val fields: Set<ValidationField>, ...) : DomainError
    data class Unknown(...) : DomainError
}
```

`message` is **for logs and developers**. It routinely carries raw driver text
(`UNIQUE constraint failed: ...`), so the UI must map the error to its own copy rather than render
this (D51).

**Why.** Two decisions here.

*Open, not sealed:* `core:common` cannot know every error a feature needs, and squeezing
feature-specific failures into `Unknown` destroys the ability to react to them. Features declare their
own implementations.

*`NotFound` separate from `Database`:* it is the one storage failure that is not a malfunction and that
a retry cannot fix — the record is gone and will stay gone. Folded into `Database`, a screen editing a
deleted record showed "save failed" on every attempt, with no way to tell the user why and no way out.

**Enforce.** Review only.

### D11 — A validation failure names *every* bad field, and carries no message

**Rule.** `DomainError.Validation` holds a `Set<ValidationField>`, never empty (`require` it in
`init`). `ValidationField` is likewise an **open interface**; each feature declares its own enum
(`TaskField`, `GroceryField`). Screens ask:

```kotlin
inline fun <reified F : ValidationField> DomainError.validationFields(): Set<F>
```

which filters to the caller's own field type and silently drops fields belonging to another feature —
a caller receiving one has nothing to attach it to and should fall back to a screen-level message.

**Why.** Three failures, one entry:

- **A validator that stops at the first bad field** makes the user fix the name, submit, learn the
  interval is also wrong, and submit again. One round trip per bad input, when the domain knew about
  all of them at once.
- **A per-field message written in the domain layer is never read.** It existed in English where it
  could not be localized, while both screens took their wording from their own resources anyway — so
  every rule's copy existed twice with nothing keeping the two in step. *The field is the message.* A
  screen needing to distinguish two reasons for one input declares two fields.
- **The reified accessor** replaces `(error as? DomainError.Validation)?.field as? TaskField` — two
  casts to ask one question, with accumulated failures unreachable behind the second.

**Enforce.** The `init` block enforces non-emptiness. The rest is review only.

### D12 — `UseCase` and `FlowUseCase` are separate, and live in `core:common`

**Rule.**

```kotlin
fun interface UseCase<in P, out R> { suspend operator fun invoke(params: P): R }
fun interface FlowUseCase<in P, out R> { operator fun invoke(params: P): Flow<R> }
```

Plus `invoke()` overloads that drop a meaningless `Unit` at the call site.

**Why.** *Separate*, because folding them together forces `suspend fun invoke(): Flow<T>` — a signature
advertising that obtaining the stream can suspend or be cancelled, when building a flow does neither.
Collecting suspends; handing it over does not.

*In `core:common`, not `core:mvi`*, so a feature's domain layer never has to depend on the presentation
module to describe its own use cases. This is a one-line decision that keeps D3's layering honest.

**Enforce.** D4 — `core:mvi` is not on a feature's domain path by accident, it is that `core:common`
holds the types both need.

### D13 — Inject dispatchers

**Rule.** A `CoroutineDispatchers` interface exposing `main` / `io` / `default`, with an `expect`/
`actual` production implementation and a test implementation in `core:testing` (D40).

**Why.** `Dispatchers.IO` does not exist on Kotlin/Native, so this is partly forced. The payoff is
that every suspending unit can be driven on a test dispatcher, which is what makes the timing-sensitive
rules (D18, D27, D30) testable at all.

**Enforce.** Review only.

### D14 — One `Logger`, `expect`/`actual`

**Rule.** A `Logger` interface (`debug`/`warn`/`error`, each taking a tag) with per-platform actuals.
Bind it in DI; never call a platform log API directly from shared code.

**Why.** D17 requires a logger to exist at the point where an exception becomes a value. Without an
injectable one, that funnel either cannot log or cannot be tested — and `RecordingLogger` (D40) is how
you assert that a swallowed exception was in fact recorded.

**Enforce.** Review only.

### D15 — App-start work is contributed, not hung off a screen

**Rule.**

```kotlin
fun interface AppInitializer { suspend fun initialize() }

class AppCoroutineScope(dispatchers: CoroutineDispatchers) : CoroutineScope {
    override val coroutineContext = SupervisorJob() + dispatchers.default
}
```

Features Koin-multibind their initializers; `:shared` runs them all (D55). `initialize()` may return,
or may collect for the lifetime of the app. **An initializer must not throw** — it reports its own
failures through `Logger`, because by design nothing is waiting for its return value.

**Why.** The alternative — hanging app-wide work off whichever screen happens to open first — ties an
application concern to one ViewModel's lifetime. The job then never runs for a user who does not visit
that screen, and runs again every time that screen is rebuilt.

`SupervisorJob` so one initializer failing does not cancel the others; `default` rather than `main` so
startup work never lands on the UI thread.

**Enforce.** A graph test asserts the expected number of initializers is bound (D56) — this one was
bound-twice-and-silently-overridden once already.

### D16 — "Today" is a dependency, and mostly a `Flow`

**Rule.**

```kotlin
interface TodayProvider {
    fun today(): LocalDate          // one-shot: a save, a validation, a single decision
    fun todayFlow(): Flow<LocalDate> // re-emits at each local midnight
}
```

**Anything that renders or schedules against a date must use the flow.** Implementation notes that are
each a bug avoided:

- Recompute the delay from the clock on **every** pass rather than sleeping a fixed 24h — a timezone
  change, a DST boundary or a device that slept across midnight then resolves on the next emission
  instead of accumulating drift.
- `distinctUntilChanged()` covers waking a hair early; `coerceAtLeast(1.seconds)` stops a backwards
  clock jump spinning the loop.
- `shareIn` on the app scope, `replay = 1`, **`replayExpirationMillis = 0`**. The sharing is so one
  timer serves every collector instead of each parking a coroutine for 24 hours to compute the same
  date. The expiration is the correctness half: with an ordinary `replay = 1` cache, a collector
  arriving after the last one left is handed the date from whenever the timer last ran — which on a
  device that slept overnight is *yesterday*.
- Inject the `Clock`. Reading `Clock.System` directly leaves the loop, the backwards-clock guard and
  the `distinctUntilChanged` unreachable from any test, because virtual time cannot move the real
  clock the delay was computed from.

**Why.** A reactive data layer does not rescue a date read once. A query flow re-emits when its table
changes, so on a quiet night nothing re-emits at all — and the dashboard kept drawing yesterday's
window under today's heading until the user happened to write something.

**Enforce.** Review only, and it is the single easiest rule here to violate. Make `today()` the one
that looks unusual at a call site.

## `core:database`

### D17 — Two funnels: reads get a deadline, writes do not

**Rule.**

```kotlin
suspend fun <T> safeQuery(scope, dispatchers, logger, operation, timeout = 5.seconds, block): AppResult<T>
suspend fun <T> safeMutation(scope, dispatchers, logger, operation, block): AppResult<T>
    = safeQuery(..., timeout = null, block)
```

Reads go through the first, writes — including whole transactions — through the second.
**`logger` and `operation` are required parameters, not optional.**

**Why.** This is the most expensive lesson in the document.

*Why reads get a deadline:* a read that never returns is worse than one that fails, and a caller told a
read failed loses nothing by asking again — the answer is the same and no state moved.

*Why writes must not:* the blocking driver call **cannot be cancelled**. It runs to completion and its
result is discarded. So on timeout the caller was handed a definite `DomainError.Database("timed
out")` while the insert it describes went on to commit a moment later. The screen showed "save failed"
over a record that had in fact been saved — and the obvious thing for the user to do about that,
pressing Save again, wrote a second row. Neither a re-entry guard nor a disabled button helps, because
by then the first write has already reported back. A wall-clock deadline on a write was not a safety
net; it was the one thing making the outcome unknowable.

Without it the outcome is determinate again, and that is a property of SQLite rather than of the
function: a single statement is atomic and a transaction commits or rolls back, so **a write that
throws did not happen and a write that returns did.** What is given up is the bound — which was never
this layer's to enforce, since both drivers have their own busy timeout and surface a locked database
as an ordinary exception.

*Why the logger is mandatory:* this is the single funnel where a driver exception becomes a value and
stops propagating. Logging **here**, rather than leaving each datasource to remember, is what makes
silently swallowing one structurally impossible. `operation` names the call site, because
"UNIQUE constraint failed" rarely says which query produced it.

**Enforce.** Review only, but the signature does the work — there is no way to call the funnel without
supplying a logger, and no non-deadline read path unless you pass `timeout = null` deliberately.

### D18 — The timeout needs a coroutine that is detached from the caller but still parented

**Rule.** Inside `safeQuery`, run the block as `scope.async(dispatchers.io) { block() }` where `scope`
is the **injected application scope**, then `withTimeout { work.await() }`. On timeout, log and
`work.cancel(e)`. Rethrow `CancellationException` after cancelling.

**Why.** Two mistakes, both of which look correct:

- **`withTimeout { withContext(io) { block() } }` cannot resume the caller early.** Structured
  concurrency makes `withContext` wait for its own child before returning, and a SQLDelight query is
  an ordinary blocking call with no cancellation point. So the timeout fired, changed nothing, and
  surfaced only once the query it was meant to bound had finished on its own. The test passed because
  its block was a `delay` — which *is* cancellable — so the one case the timeout could not handle was
  the only case the code ever met.
- **`CoroutineScope(dispatchers.io)` created per call** builds a fresh, unparented, unsupervised `Job`
  on every query, outside structured concurrency entirely: nothing can observe that work, nothing can
  cancel it, and at teardown in-flight writes simply carry on. A supervised app scope gives the same
  detachment *from the caller* — all the timeout needs — while keeping the work in a hierarchy
  something owns.

Cancelling on timeout rather than abandoning matters too: a block that *does* have a suspension point
(a transaction mid-flight) then stops at the next one instead of running on.

**Enforce.** Write the test with a genuinely blocking block, not a `delay`.

### D19 — Wrap query flows, and treat a failed flow as terminated

**Rule.**

```kotlin
fun <T> Flow<T>.asAppResult(logger: Logger, operation: String): Flow<AppResult<T>>
```

`map` to `Success`, `catch` to a logged `Failure` (rethrowing `CancellationException`).

**Why.** A failed flow is **terminated** — `catch` cannot resume its upstream — so the emitted failure
is the last thing an observer sees until it resubscribes. That is precisely why a screen's "retry"
cannot just clear the message; it has to re-subscribe, which is what D26's keyed `observe` is for.
Write this down, because the two entries are only correct together.

**Enforce.** Review only. The fake in D59 must reproduce the termination or the retry path is untested.

### D20 — Assert the row count, and map zero rows to `NotFound`

**Rule.** A `checkSingleRowAffected(rowsAffected, operation)` helper that throws a `RowNotFoundException`
on zero — caught by D17's funnel and mapped to `DomainError.NotFound`, logged at *warn* rather than
error (the row being gone is a legitimate race with another writer, not a malfunction).

**Use it on updates and deletes. Do not use it on a single-row insert.** An `INSERT ... VALUES` either
throws or inserts exactly that row, so there is no "matched nothing" case — and reporting one as
`NotFound` was actively harmful: the ViewModel reads that error as "the row was deleted elsewhere",
closes the editor and discards what the user had typed, about a row that was never supposed to exist
yet.

**Why.** Without the check, a delete of an already-deleted record reports success, and the UI cannot
tell "deleted" from "broken" — which is the distinction D10 exists to preserve.

**Enforce.** Review only. Two datasources disagreeing about this is one of the drifts that motivated
D4.

### D21 — `DatabaseDriverFactory` is an interface, not an `expect class`

**Rule.**

```kotlin
interface DatabaseDriverFactory {
    fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>, databaseName: String): SqlDriver
}
```

**Why.** Two things an `expect class` cannot do: let platforms take different constructor parameters
(Android needs a `Context`, iOS needs nothing), and let a test substitute an in-memory driver. The
second is what makes D41 and D56 possible — the whole-graph test overrides exactly this one binding.

**Enforce.** Review only, but D56 fails the moment it is not substitutable.

### D22 — One driver binding per database, closed with the graph

**Rule.** Each feature owns its own database. In the feature's Koin module:

```kotlin
single<SqlDriver>(named(FEATURE_DRIVER)) {
    get<DatabaseDriverFactory>().createDriver(FeatureDatabase.Schema, "feature.db")
} onClose { it?.close() }
single { FeatureDatabase(get(named(FEATURE_DRIVER))) }
single { get<FeatureDatabase>().someEntityQueries }
```

Export every schema to `src/commonMain/sqldelight/databases/` and set `verifyMigrations = true`.

**Why.** Binding the driver *separately and by name* is what gives it an `onClose` — otherwise the
connection is owned by the database object and released by nothing. Schema export plus migration
verification is what turns "someone changed a column" from a runtime crash on upgrade into a build
failure.

**Enforce.** A graph test asserts that closing the Koin application closes every driver (D56). Note
that the test must track drivers created *through the substituted factory* and assert `onClose` ran —
a test that closes the drivers it recorded itself would pass with every `onClose` deleted.

### D23 — Three mechanical rules for every query

**Rule.**

1. **Every `selectAll` carries an explicit `ORDER BY`.** Nothing downstream sorts it, and SQL does not
   guarantee a stable order without one.
2. **Every observe chain ends in `flowOn(dispatchers.io)`.** `mapToList(io)` confines only the query
   *execution*; every operator after it runs in the collector's context — which for a ViewModel is
   `viewModelScope`, i.e. the main thread. So turning the whole table into domain objects, on every
   emission, was happening on the UI thread.
3. **A module owning a `composeResources/` tree must enable Android resource processing**
   (`android { androidResources { enable = true } }`). The AGP KMP library plugin keeps it off by
   default; without it the generated resource payload is assembled for the iOS targets only and never
   reaches the APK, so every `stringResource()` on Android throws at first composition. The build
   succeeds and the tests do not render — see D61 for the CI check that catches it.

**Why.** All three are invisible in review and each produced a real defect. (2) is the one that had
been written down and not applied — which is the argument for D4.

**Enforce.** (3) by a CI assertion over the release APK (D61). (1) and (2) are review only; consider a
detekt rule if you can express it.

### D24 — Namespace a shared key-value table

**Rule.** A `KeyValueCache<K, V>` interface (`get`/`put`, both returning `AppResult`) over a
single-table schema in `core:database`. **The implementation takes a namespace prefixed onto every row
id.**

**Why.** The table is shared. Two features independently picking the same key string would overwrite
each other, with no error and no way to notice.

**Enforce.** The constructor requires the namespace.

## `core:mvi`

### D25 — Markers, and a handler-style ViewModel

**Rule.** `MviState` / `MviEvent` / `MviEffect` as empty marker interfaces, plus
`BaseViewModel<S, E, F>` where `onEvent` calls `setState { }` and `sendEffect()` **directly** — no
separate reducer class.

Details that are not incidental:

- `_state` is `by lazy`, so `createInitialState()` runs after the subclass constructor rather than
  during the super call.
- `state` is a single `asStateFlow()` instance, **not** `get() = _state.asStateFlow()` — a getter
  allocates a new wrapper per read, so anything keyed on flow identity (`remember`,
  `LaunchedEffect`) re-runs on every recomposition.
- Effects go through a `Channel(UNLIMITED)` with `trySend`, so sending never suspends and effects are
  delivered in the order raised. Launching a coroutine per send leaves ordering to the dispatcher.

**Why.** A separate reducer buys indirection at this scale and nothing else. The three details above
are each a bug that is hard to attribute after the fact — especially the `state` getter, which
presents as unexplained recomposition.

**Enforce.** Review only.

### D26 — `observe(key, source, onData, onError, ...)` — keyed subscriptions

**Rule.**

```kotlin
protected fun <T> observe(
    key: Any = DEFAULT_OBSERVE_KEY,
    source: Flow<AppResult<T>>,
    onData: S.(T) -> S,
    onError: S.(DomainError) -> S,
    onStart: S.() -> S = { this },
    onEmission: (T) -> Unit = {},
)
```

Re-calling with the same `key` cancels and replaces that subscription. Add `observeValues` for a source
that cannot fail (`todayFlow()`).

**Why.**

- Every screen is this shape — observe a reactive source, show data or a typed message — and each one
  hand-rolled the same five steps: hold a job, cancel it, flag loading, collect, fold.
- **`key` is what lets one screen observe two sources.** A single job field meant the second `observe`
  call silently cancelled the first, with no error and no compiler help: the screen simply never
  populated half its state.
- **Replacement is what makes retry a one-liner.** Per D19 a failed upstream is terminated, so only a
  fresh subscription recovers — and the previous one must be cancelled first, or a recovered screen ends
  up with two collectors racing to set the same state.
- `source` is the flow itself, not a lambda producing one: re-calling `observe` already re-evaluates
  the argument and the flows are cold, so the lambda read as if it mattered and did not.
- `observeValues` exists rather than wrapping an infallible source in `map { Success(it) }`, which
  bought a fake result plus an `onError` branch commented "the clock cannot fail". A dead branch is a
  worse answer than a second entry point.

**Enforce.** Review only. *(The shape is library-independent; only `Flow` is assumed.)*

### D27 — The reducer is pure; per-emission side effects have their own hook

**Rule.** `onData` (and any `setState` lambda) must be pure. `updateAndGet` may re-run its lambda under
contention. Side effects an emission triggers — navigating away because the record is gone — go in
`onEmission`, which runs exactly once per emission. `onStateChanged(state)` is the once-per-change hook.

**Why.** A side effect written inside a reducer happens twice under contention, intermittently. This is
also the reason D28's occurrence id is minted *outside* the reducer.

**Enforce.** Review only, and it is a rule a reviewer must actively look for. Document it on the
functions themselves, as PlannerKMP does.

### D28 — A user-facing message carries the identity of *this* raising

**Rule.**

```kotlin
data class UiMessage<out M : Any>(val value: M, val occurrence: Long)
```

`setState` mints the occurrence id **before** running the reducer; `raise(message)` — called inside the
reducer — only *reads* it. Every `copy(message = ...)` goes through `raise`.

**Why.** Two layers:

- **Without the occurrence**, the snackbar host keys its effect on the value it is given, so raising the
  same message again while the first was on screen changed nothing and the host never re-showed it. On a
  list where completing an item is one tap and two taps in a row is the normal way to use it, the second
  completion produced no acknowledgement at all and quietly inherited what was left of the first one's
  undo window.
- **Minting inside the reducer would violate D27** — incrementing a counter in a lambda documented as
  re-runnable, in the very class that states the rule. Minting outside and reading inside keeps the
  reducer pure: a re-run rebuilds an identical `UiMessage` instead of burning a second id.

**Enforce.** Make the state field `UiMessage<M>?` so writing a bare value does not compile.

### D29 — `LoadableState`, and `isSubmitting` with no default

**Rule.**

```kotlin
interface LoadableState<M : Any> : MviState {
    val isLoading: Boolean
    val loadFailed: Boolean
    val isEmpty: Boolean
    val isSubmitting: Boolean          // no default, deliberately
    val message: UiMessage<M>?
    val hasTerminalLoadFailure: Boolean get() = loadFailed && isEmpty
}
```

On a list screen, derive `isSubmitting` from a **set of in-flight ids**, so one slow write does not
disable every other row.

**Why.**

- Each screen re-derived this: three copies of `hasTerminalLoadFailure`, three of the
  spinner/error/content branch, three of the snackbar plumbing — and the third copy had already drifted,
  missing the terminal branch entirely, so a failed load rendered an empty *form* that still believed it
  was editing a real record.
- **`loadFailed` is a flag rather than `message == LoadFailed`**, so the terminal-failure rule does not
  have to know which member of a feature's message enum means "the load broke".
- **`isSubmitting` has no default so a screen must answer the question.** Every mutation was
  fire-and-forget from a tap: `save()` read the state, launched, returned — so two quick taps ran two
  inserts and created two rows. The ViewModel gating re-entry on this flag and the screen disabling the
  control are two halves of one guard: without the first, a slow database still admits the second tap;
  without the second, the user gets no feedback that the first took.
- **`hasTerminalLoadFailure = loadFailed && isEmpty`** is the rule that decides full screen vs snackbar.
  A failure with content still visible stays a snackbar — announcing a failure and then leaving the user
  looking at an empty pager helps nobody.

**Enforce.** The absent default is a compile error. The rest is review only.

### D30 — Restore user input, and only user input

**Rule.** `RestorableViewModel<S, E, F, I>` taking a `SavedStateHandle`, a `KSerializer<I>` and a
`Logger`. The screen names a small `@Serializable` `I` holding **only what the user typed or chose**.
`captureInput(state): I` / `applyInput(state, input): S`.

Four implementation details, each a bug:

- **Encode to a JSON string**, not via `encodeToSavedState`. `SavedState` is a `Bundle` on Android and
  therefore unreachable from a JVM host test — so the restore path could only ever be exercised on a
  device, and a restore path with no test is a broken restore path. A string is the one representation
  every target and every test agrees on.
- **Skip the write when the captured input is unchanged.** `onStateChanged` fires on *every* reduction —
  a list re-emitting from the database, a spinner going down, a snackbar being set and cleared — and each
  used to re-serialize the whole form though none can have changed a typed field.
- **Seed `lastWrittenInput` from the restored value**, so the first reduction after a restore does not
  re-encode a byte-identical value.
- **Catch `SerializationException` on restore, log, and fall back to the initial state.** An app update
  can change the shape of `I` while old saved state is on disk. A blank form is a bad restore; a crash
  loop on first launch after an update is worse, and the user cannot clear saved state themselves. Use
  `Json { ignoreUnknownKeys = true }` so a new optional field is an ordinary change.

**Why.** Loaded data is never saved — it belongs to the database, which is still there after a restart
and re-emits by itself. Saving it pays for it twice and, on Android, risks the binder transaction limit.
Note that restored *navigation* makes the loss of in-screen state more visible, not less: the user comes
back to exactly the screen they left, with the form they had half-filled wiped.

**Enforce.** `restoreInto` / `onStateChanged` are `final override` in the base class, so a subclass
cannot partially reimplement the contract.

## `core:ui`

### D31 — Tokens and type roles, not ad-hoc values

**Rule.** A theme (`colour scheme` + type scale), `Spacing` / `Sizing` token objects, and named type
roles for recurring semantic uses (`itemTitle`, `dayHeading`). No raw `dp` or `sp` in feature code.

**Why.** Standard, and cheap only if done before the first screen.

**Enforce.** Review only; a detekt rule on magic numbers helps.

### D32 — `LoadableContent` and `MessageHost` are not optional

**Rule.** `core:ui` owns the spinner/full-screen-error/content branch and the snackbar sequence as
composables. **A screen adds them rather than re-deriving them.** Also own `CollectEffects(flow) { }`,
the lifecycle-aware effect collector every screen uses, and the shared form pieces (`FieldError`,
`PlainTextField`).

One detail worth copying: `LoadableContent`'s `modifier` must reach **all three** branches including the
content. Applied to the spinner and error view only, every caller re-applied part of it by hand around
its own content — three copies of `.padding(top = padding.calculateTopPadding())`, each silently dropping
the start, end and bottom insets the spinner in the same position honoured.

**Why.** Per D29, a screen that hand-rolls this branch will eventually omit an arm of it. The component
is where the three-way decision lives exactly once.

**Enforce.** Review only. Make it the path of least resistance by giving it sensible defaults.

### D33 — `core:ui` depends on nothing

**Rule.** Every composable here is typed on **plain values or `Flow<T>`** — `isLoading: Boolean`,
`errorMessage: String`, `hasTerminalLoadFailure: Boolean` — never on `LoadableState` or `AppResult`. So
`core:ui` needs no dependency on `core:mvi` or `core:common`.

**Why.** It is the module most likely to be lifted into another project, and the one whose components are
most likely to be needed from a context that has no ViewModel. Typing on the state interface would drag
the whole MVI layer along for a spinner.

**Enforce.** D4 — `core:ui` has an empty allowlist.

### D34 — Generic vocabulary here; feature wording is a parameter

**Rule.** `core:ui` owns a small `composeResources` set of generic actions (`retry`, `undo`, `ok`,
`cancel`, `delete`, a generic error) as **defaults a feature may override**. Any *feature* wording a
`core:ui` composable renders is a parameter.

**Why.** Without the defaults, every feature re-declares "Retry". With only defaults and no parameter,
`core:ui` ends up owning product copy — which is how a shared module acquires a reason to change per
feature.

**Enforce.** Review only; D3's "no dependency" makes the wrong version awkward to write.

## `core:navigation`

*Everything in this section is nav3-specific and the most likely part of the document to have aged.
Re-derive the mechanics; the three contracts are the transferable idea.*

### D35 — Wrap the back stack

**Rule.** A `Navigator` class wrapping the `NavBackStack`, exposed through a `LocalNavigator`
composition local. **It ignores a push of the key already on top.**

**Why.** Double-tap on a navigating control otherwise pushes the same destination twice. Wrapping also
gives you one place to add that kind of rule.

**Enforce.** Unit test on `Navigator`.

### D36 — Three contracts a feature implements, one distinct class each

**Rule.**

```kotlin
fun interface NavEntryProviderContributor { fun EntryProviderScope<NavKey>.contribute() }
interface FeatureTab { val id: String; val order: Int; val title: StringResource; val icon: ImageVector; val rootKey: NavKey }
fun interface NavKeySerializersContributor { /* registers its NavKey subtypes */ }
```

Each feature implements all three and Koin-multibinds them. **Each must be a distinct class per
feature.**

**Why.** This is what lets `:shared`'s root name no feature (D54). Two specifics:

- **Koin keys definitions by type**, so two `single<FeatureTab>` declarations *silently override each
  other* and the app comes up with one tab. A distinct class per feature is the fix, and it is not
  optional.
- **`FeatureTab.id` is a stable string, and selection is remembered by id, not by bar position.**
  Position is derived from `order` over whichever features are installed, so saving an index meant a
  restored app could come back on a different tab than it left on. An id that no longer resolves falls
  back to the first tab.

**Enforce.** D56 — assert one tab, one entry contributor and one serializer contributor per feature
module, and that no two tabs claim the same `order`.

### D37 — Every `NavKey` is `@Serializable` and carries ids, never entities

**Rule.** Register the subtypes explicitly in a `polymorphic(NavKey::class)` serializers module built
from the contributors, and pass it to `rememberNavBackStack`.

**Why.** Reflection-based polymorphism is Android-only, and the back stack has to restore on iOS too —
so explicit registration is forced, which is what `NavKeySerializersContributor` exists for. Carrying ids
rather than entities is the same rule as D46, for the same reason.

**Enforce.** A back-stack restore test, plus D56 on the contributor count.

### D38 — Hoist the decorators and the transition scope above the tab switch

**Rule.** In the Compose root: one `SharedTransitionLayout`, one set of `entryDecorators` — including
`rememberViewModelStoreNavEntryDecorator()` and `rememberSaveableStateHolderNavEntryDecorator()` — and a
`rememberNavBackStack` per tab, all above the tab selection.

**Why.**

- **Without the ViewModel-store decorator every nav entry shares the Activity's `ViewModelStore`**, so
  per-screen ViewModels are neither scoped nor cleared.
- **Hoisted rather than per tab**, so each entry's `ViewModelStore` and saved state survive a tab change
  instead of being rebuilt.
- **One `SharedTransitionLayout` for every tab.** Providing the scope around only one tab leaves a trap:
  the `entryProvider` is shared, so any feature screen reachable from another tab throws on reading the
  local.

**Enforce.** Review only. Comment it in the root, which is where someone will "simplify" it.

### D39 — One animation duration constant

**Rule.** `NavAnimation.DURATION_MILLIS`, shared by the scene transition and any shared-element bounds
transform, with explicit `transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec`.

**Why.** Nav3's own default differs per platform, which left a half-faded outgoing screen hanging over
the FAB on Android while iOS looked right.

**Enforce.** Review only.

## `core:testing`

### D40 — Fixtures only, and never a production dependency

**Rule.** This module contains `TestCoroutineDispatchers`, `RecordingLogger` / `NoOpLogger`, a fake
`TodayProvider`, an in-memory `KeyValueCache`, a `CloseTrackingSqlDriver`, and `inMemorySqlDriver`
(D41) — nothing else. It is a `commonTest` dependency **everywhere** and a production dependency
**nowhere**.

**Why.** Fakes over `core:*` types have to live somewhere both features can reach, and that place must be
one the release binary cannot see.

**Enforce.** D4's task treats it as test-only and rejects it outside a test source set. This is the
allowlist's single most valuable check.

### D41 — `inMemorySqlDriver` as `expect`/`actual`

**Rule.** An `expect fun inMemorySqlDriver(schema): SqlDriver`, actualized over the JDBC driver on the
JVM host and `NativeSqliteDriver` on iOS.

**Why.** This is what lets the datasource, key-value-cache and DI-graph tests live in `commonTest` and
run **against the driver that actually ships** on each platform. Without it those tests are JVM-only, and
the iOS half of the storage layer is exercised by nothing.

**Enforce.** The tests run on both targets in CI (D61).

---

# Phase 2 — Feature anatomy

Everything here is per feature module. The goal is that a new feature is a new module plus one line in
`:shared`, and nothing else.

## D42 — Layering inside a feature is `ui → domain ← data`

**Rule.**

```
feature:x/
  domain/   Task.kt, TaskSchedule.kt, TasksDatasource.kt (the port), use cases
  data/     SqlDelightTasksDatasource.kt, ColumnAdapters.kt, the .sq schema
  ui/       XScreen.kt, XViewModel.kt, XContract.kt
  di/       XModule.kt
  XNavKey.kt, XNavContributions.kt
```

Datasource **interfaces** live in `domain/`; their implementations in `data/`. Business rules —
validation, what a cleanup changes — belong in `domain/`, never in a ViewModel.

**Why.** The dependency runs from the implementation towards the domain, so the domain layer is testable
with a fake and has no idea a database exists.

**Enforce.** Review only (D4 works at module granularity). A package-cycle detekt rule can help.

## D43 — Use cases hold policy, not verbs

**Rule.** A use case exists **where there is policy**: a dashboard's date window, a daily cleanup, what
"completing" something means. Plain CRUD goes straight to the port — an edit ViewModel takes
`TasksDatasource` directly rather than an `AddTask` class per operation.

**Why.** The domain boundary is the port in `domain/`, not a class per verb. A `GetXUseCase` that forwards
one call to one port method adds a file, a Koin binding and a test, and prevents nothing.

**Enforce.** Review only. This is the rule most likely to be violated by habit.

## D44 — A screen is a thin resolver plus a stateless `...Content`

**Rule.** Two composables. The first resolves the ViewModel, collects state, wires `CollectEffects`. The
second — `...Content` — is stateless, takes the state and **one** `onEvent: (Event) -> Unit`. Child
composables never take the ViewModel.

**Why.** The stateless half is what makes previews (D65) and any UI test possible, and it is what stops a
child composable acquiring its own reason to know about the ViewModel.

**Enforce.** Review only; D65's previews make a non-stateless `Content` impossible to preview, which
surfaces it early.

## D45 — All UI state lives in the state; effects are genuinely one-shot

**Rule.** Dialog visibility, the current message, editor expansion — all in the `MviState`. Effects are
for things that happen once and cannot be re-derived: navigation, mostly.

**Why.** State in `remember` inside a composable is state the ViewModel cannot restore, test or reason
about, and it is invisible in the one place a reader looks for the screen's state.

**Enforce.** Review only.

## D46 — An event carries ids, never entities

**Rule.** `onEvent(CompleteTask(id))`, not `onEvent(CompleteTask(task))`. Same rule as D37 for `NavKey`s.

**Why.** A whole entity in an event plans from a snapshot captured when the card was drawn, ignoring the
live list in the ViewModel that receives it. With a reactive data layer (D19) that snapshot can already be
stale by the time the tap lands.

**Enforce.** Review only, and worth stating in the project's own CLAUDE.md — see the appendix.

## D47 — The domain model owns its invariants

**Rule.** A `Draft` factory is the only way to build a valid entity, and it returns
`AppResult<XDraft>` naming **every** offending field (D11):

```kotlin
TaskDraft.create(...): AppResult<TaskDraft>   // Failure(Validation(setOf(TaskField.Name, TaskField.Interval)))
```

**Why.** Validation in a ViewModel is validation that exists once per screen that writes the entity, and
is absent from every other writer — including a future import, sync or migration.

**Enforce.** A private constructor plus a factory returning `AppResult` makes the invalid path
unrepresentable.

## D48 — Model "which kind" as a sealed type, and put the rules on it

**Rule.** Where an entity has variants, make them a sealed hierarchy carrying **only the fields that
variant has**:

```kotlin
sealed interface TaskSchedule {
    data class Asap(val createdOn: LocalDate) : TaskSchedule
    data class OneTime(val date: LocalDate) : TaskSchedule
    data class Periodic(val startDate: LocalDate, val daysInterval: Int, val endDate: LocalDate?) : TaskSchedule
}
```

Put the rules **on the type** — `occursOn`, `nextOccurrenceAfter`, `hasExpiredBy` — each written as a
`when (this)`.

**Why.** Adding a variant then breaks the build at every rule that must account for it. The alternative —
nullable flat fields plus a type enum — makes every rule a defensive read of fields that may or may not
apply, and adding a variant is silent.

**Enforce.** Exhaustive `when` over a sealed type is a compile error when incomplete. This is the single
highest-leverage typing decision in a feature.

## D49 — A schema is a storage format, not a domain model

**Rule.** Keep the flat columns in the `.sq` file and let the datasource own the translation to and from
the sealed domain type.

**Why.** The two have different jobs and change for different reasons. Forcing the domain model into the
table's shape is how you get D48's nullable-fields-plus-enum anti-pattern back in through the data layer.

**Enforce.** Review only. The datasource's `toDomain()` / column adapters are where it lives.

## D50 — Two write shapes: whole row, and partial

**Rule.** Expose both deliberately, and document which callers use which:

- `editX(entity)` writes the **whole row** — only the edit form calls it.
- `rescheduleX(id, schedule)` writes **specific columns** — completion, cleanup and any other
  derived-state write use this.

**Why.** Completion and cleanup both act on an entity read earlier. A whole-row update from one of them
pushes its stale name over a later edit. This is D46's stale-snapshot problem surfacing in the data layer,
and the fix is the same: write only what you actually decided.

**Enforce.** Review only, but the port's method names make the wrong choice visible at the call site.

## D51 — Typed messages, resources per feature

**Rule.**

- A screen's user-facing messages are a **typed enum** in its contract; the UI resolves them to strings.
  A `DomainError`'s `message` is log text (D10) and is never rendered.
- Every user-visible string is a Compose resource in the **feature's** `composeResources/values/strings.xml`
  — including any string a `core:ui` composable renders, which it therefore takes as a parameter (D34).
  The only exception is `core:ui`'s generic action vocabulary.
- A control that opens a picker rather than a keyboard carries its own `contentDescription` +
  `Role.Button`, and clears the disabled text field's.

**Why.** Typed messages are what let a ViewModel test assert *which* message was raised without matching
on English. Per-feature resources are what keep a feature deletable.

**Enforce.** Review only. A ViewModel that cannot reach a string resource makes the wrong version hard to
write.

## D52 — Observe the record you are editing; no one-shot `get`

**Rule.** An edit screen **observes** its row (`observeX(id)`), seeding the form from the **first emission
only** and using the continuing subscription to notice the row being deleted. Do not add a one-shot
`getX(id)`.

**Why.** With a reactive data layer the subscription is how a screen learns its record disappeared from
under it — which is the `NotFound` case D10 and D20 exist to make legible. A one-shot read cannot, and
having both invites the wrong one.

**Enforce.** Review only: don't declare the one-shot method on the port.

---

# Phase 3 — Composition root

## D53 — `koinApplication { }`, not `startKoin { }`

**Rule.**

```kotlin
fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication =
    koinApplication { appDeclaration(); modules(appModules()) }
        .also { it.koin.runStartupWork() }
```

The platform entry point owns the returned application for the life of the process and hands it to
`App(koinApplication)`. In Compose, publish it with `KoinIsolatedContext` — not the deprecated
`KoinContext`, and not `KoinApplication { }`, which would build the graph inside composition.

**Why.** `startKoin` installs the graph into Koin's *global mutable context*, which makes the one piece of
state the app cannot see or test implicit: the root reached for it with `getKoin()`, and the iOS entry
point needed a hand-rolled `koinStarted` boolean because a second `startKoin` throws. Returning it makes
the graph an ordinary value — which is exactly what lets the whole-graph test (D56) build the real thing.

**Building it twice builds a second set of database drivers**, so the "owned for the life of the process"
part is a real constraint.

**Enforce.** D56 builds the graph as a value; a global context would not permit that.

## D54 — One list names every feature. Nothing else does.

**Rule.**

```kotlin
internal val featureModules = listOf(tasksModule, groceryModule)
internal fun appModules() = listOf(commonModule, databaseModule, keyValueCacheModule) + featureModules
```

The Compose root names **no** feature: tabs, nav entries and `NavKey` serializers all arrive through Koin
multibindings (D36). Adding a feature is a new module, a Gradle dependency, and one entry in that list.

**Why.** Kotlin/Native has no classpath scanning, so this one list cannot be discovered at runtime the way
its contents are — but it should be the *only* place a feature is named. PlannerKMP had the entry providers
contributed and the bottom bar still a hardcoded list of every feature, which is the half-done version of
this and gives none of the benefit.

**Enforce.** D56 asserts the contributed counts match `featureModules.size`.

## D55 — Startup work: one supervised coroutine each, failures logged

**Rule.**

```kotlin
internal fun Koin.runStartupWork() {
    val logger = get<Logger>(); val scope = get<AppCoroutineScope>()
    getAll<AppInitializer>().forEach { initializer ->
        scope.launch {
            try { initializer.initialize() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { logger.error(TAG, "${initializer::class.simpleName} failed", e) }
        }
    }
}
```

**Why.**

- **One coroutine each, not one for all.** They are independent, so serializing them means a slow one
  delays every initializer behind it — and once an initializer may collect for the app's lifetime (a daily
  cleanup does), a single shared coroutine never reaches the second one at all.
- **Fire-and-forget on purpose:** nothing on screen is waiting, and the data layer is reactive, so whatever
  an initializer changes re-emits to whoever is observing.
- **Caught here** rather than taking the app down during `Application.onCreate`.
- `CancellationException` rethrown, always.

**Enforce.** D56 asserts the initializers are bound at all — they were bound twice and silently overridden
once, and the build said nothing while the daily cleanup stopped running for everyone.

## D56 — Resolve the whole graph in a test, in `commonTest`

**Rule.** One test class that builds `appModules()` with the `DatabaseDriverFactory` overridden to an
in-memory driver (D21, D41) and asserts:

1. Every screen's ViewModel constructs — passing a `SavedStateHandle` the way `koinViewModel()` would, via
   `parametersOf`, including each distinct entry point (add *and* edit).
2. Every feature contributes exactly one tab, one entry provider, one serializer contributor.
3. No two tabs claim the same `order`.
4. Every initializer is contributed.
5. Every port, use case and infrastructure binding resolves — naming them explicitly, so a missing binding
   points at the definition rather than at whichever screen needed it.
6. Closing the application closes **every** database driver.

**Close the application in `@AfterTest`.** Koin's single instances are held by the `Module` objects, which
are shared top-level vals — so an application left open leaves its singletons, and their open drivers, in
place for the next test.

**Why.** Koin resolves at runtime: a missing or mistyped binding is a crash when the user opens a screen,
not a compile error. And **`commonTest`, not `androidHostTest`**, because Kotlin/Native's lack of classpath
scanning is the very thing `appModules()` is written around — the graph is exactly where a platform can
differ, and resolving it only on the JVM checks the half that was never in doubt.

**Enforce.** It *is* the enforcement. Along with D4, this is the highest-value test in the repo.

## D57 — Do not register a module nothing calls

**Rule.** If a `core:*` module is built and tested against a future need but nothing calls it, leave it
**out** of `appModules()` and out of `:shared`'s dependencies. Write down the complete set of steps to wire
it back, together.

PlannerKMP's `:core:network` is the worked example: re-wiring it means adding the `:core:network`
dependency in `shared/build.gradle.kts`, the `networkModule()` call in `Koin.kt`, **and** the INTERNET
permission — all three at once.

**Why.** Registering it meant the graph could hand out an HTTP client that no manifest had requested the
INTERNET permission for, so the first request ever made would have surfaced a `SecurityException`
mislabelled as a network error. A binding that exists is a binding something will eventually resolve.

**Enforce.** D4's allowlist excludes it from `:shared`; a comment on `appModules()` records why.

---

# Phase 4 — Verification

## D58 — `commonTest` by default

**Rule.** Anything not genuinely platform-specific goes in `commonTest`, so it runs on the JVM host **and**
on iOS — including datasource, cache and DI-graph tests. Use `androidHostTest` only for things that truly
need the JVM (pinning `Locale.getDefault()` to assert real weekday names, for instance).

**Why.** D41 exists to make this possible for storage tests, and D56 explains why the graph in particular
must run on both. A test in `androidHostTest` that could have been common is a test that does not cover the
platform where the code is more likely to be wrong.

**Enforce.** CI runs both (D61).

## D59 — Fakes with a per-operation failure seam

**Rule.** Fakes live in `commonTest` next to the port they fake, except fakes over `core:*` types, which
live in `core:testing` (D40). A fake must offer:

- **A per-operation, one-shot failure seam carrying its own `DomainError`:**
  `failNext(GroceryWrite.Delete, DomainError.NotFound(...))`. A single "fail the next call" flag can only
  produce `Database`, leaving the `NotFound` branches unreachable except by actually removing the row —
  which changes the state under test.
- **A live-stream failure:** `failLiveStream()` fails the stream *after* everything it has already emitted,
  as a dying driver does, and **terminates** — because the real `asAppResult` is a `catch` and cannot resume
  its upstream (D19). This is the only way to reach a screen's non-terminal error branch, and it is what
  makes "retry re-subscribes" mean anything.
- **A write gate:** `blockWrites` / `releaseWrites()`, holding writes open. A re-entry guard (D29) can only
  be tested while a write is actually in flight; with instant writes the second tap always arrives after
  the first finished and the guard is never the thing under test.
- **State-flow backing**, so a write re-emits to observers exactly as a query flow does.

**Why.** Each bullet is a screen branch that is otherwise untestable — and untested branches on the error
path are where the bugs in D20, D26 and D29 all lived.

**Enforce.** Review only, but write the fake this way first and the tests follow.

## D60 — detekt with no baseline, configured per module

**Rule.** `buildUponDefaultConfig = true`, one shared config file, **no baseline**, and the build fails on
any issue. Add `detekt-formatting` (D8). List the source sets explicitly — the KMP plugin's are not what
detekt discovers by default:

```kotlin
source.setFrom(files(
    "src/commonMain/kotlin", "src/androidMain/kotlin", "src/iosMain/kotlin",
    "src/commonTest/kotlin", "src/androidHostTest/kotlin", "src/main/kotlin",
))
```

**Leave type resolution unconfigured.** It produces false positives around `expect`/`actual`, which means
the `coroutines` ruleset does not run — accept that rather than a wall of noise.

**Why.** A baseline on a greenfield project is a list of things nobody will ever fix. Starting with none is
free; adding one later is not.

**Enforce.** Itself, in its own CI job (D61).

## D61 — CI: two jobs, and what the build job must actually cover

**Rule.**

**Job 1, `static-analysis`** (ubuntu): `architectureCheck`, **then** `detekt`. Upload detekt reports on
failure.

**Job 2, `build`** (macOS — half the codebase is only built by the iOS targets):

1. JVM unit tests.
2. Android **debug** build.
3. Android **release** build — the only configuration that runs R8 (D62).
4. **An assertion that the Compose resource bundles are in the release APK** (`unzip -l` | `grep` for each
   `assets/composeResources/<module>.resources/`).
5. Compilation of **both** iOS targets.
6. The `iosArm64` framework **link**.
7. `xcrun simctl list runtimes`, then the iOS tests.
8. `xcodebuild` of the Xcode project.
9. Upload test reports on failure, and the **R8 mapping file** on success.

**Why**, item by item, because each exists for a reason:

- **Two jobs, not one:** detekt used to be the first step of a single job, and everything after a failing
  step is skipped — so a style finding hid whether the tests passed. `architectureCheck` runs before detekt
  because a layering break is a worse thing to learn about late than an import order.
- **Release build:** see D62.
- **The resource assertion:** nothing else can catch D23's rule 3. The build succeeds, the tests do not
  render, and the app throws on the first `stringResource()` it reaches.
- **Both iOS targets:** compiling only the simulator one leaves the binary that actually ships —
  `iosArm64` — compiled by no configuration at all. Same argument as the release build.
- **Linking separately:** a name collision in the generated Objective-C header, or a missing export, shows
  up at link time and nowhere earlier.
- **`simctl list runtimes`:** `iosSimulatorArm64Test` needs an installed simulator *runtime*, not just the
  SDK. Where the list is empty it fails with "Xcode does not support simulator tests" regardless of the
  code. Printing the list turns that into an obvious diagnosis. Locally, `linkDebugTestIosSimulatorArm64`
  still checks those tests compile and link.
- **`xcodebuild`:** builds the Swift entry point, the framework embedding and `Config.xcconfig`, none of
  which any Gradle task touches.
- **The mapping file:** without it a release stack trace cannot be deobfuscated at all, and the build that
  produced it is gone.

Also set `permissions: contents: read` explicitly — without the block the job inherits the repository
default, which on older repositories is read/write.

**Enforce.** Itself.

## D62 — R8 runs only in release, so build release in CI

**Rule.** `isMinifyEnabled` + `isShrinkResources` on release. **Run the release build before trusting a
change to `proguard-rules.pro` or to anything reflective.**

**Why.** Building debug alone means the shrinker, the resource shrinker and every keep rule are first
exercised by whoever cuts the release — the one build where a missing rule shows up as a crash rather than
a compile error. Unsigned is fine in CI; signing is not what this is testing. (Have release signing read
its keystore from the environment and stay unsigned when unset.)

**Enforce.** D61 step 3.

## D63 — Keep what reaches a log through a class name

**Rule.** In `proguard-rules.pro`:

```
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepnames class * extends <your RestorableViewModel>
-keepnames class * implements <your AppInitializer>
-keepnames class * extends java.lang.Throwable
```

**Why.**

- **`SourceFile`/`LineNumberTable`:** with no crash reporter, the log is the only diagnostic, and an
  unreadable release trace makes a crash simply unexplainable. Verified load-bearing — neither attribute
  appears in AGP's own `proguard-android-optimize.txt`. `-renamesourcefileattribute` still hides the
  original file names.
- **The three `-keepnames`:** D30 logs `${this::class.simpleName}` on an unreadable restore, D55 logs it on
  a failed initializer, and exception class names reach logs the same way. `KClass.simpleName` resolves
  through the JVM class name, which R8 renames — so in release those lines read "discarding unreadable
  saved input for b". Keeping the *names* (not the members) costs nothing.
- **`-keepattributes Exceptions` is not this.** It keeps the `throws` clause on a method descriptor and has
  nothing to do with class names.

**Carry no kotlinx.serialization rules.** The library ships its own
(`META-INF/com.android.tools/r8/kotlinx-serialization-common.pro`), which already keeps the `Companion` and
`serializer()` members of every `@Serializable` class. Hand-written rules restating that without the
`@Serializable` guard pinned the companion of *every* class in the package, serializable or not, and
stopped R8 shrinking any of them.

Check the rest against your resolved artifacts rather than adding rules defensively. For this stack nothing
else needed a keep: Koin is constructor-reference based and keys definitions by `KClass` (registration and
lookup see the same renamed class), SQLDelight's generated queries are reached from statically-resolved
lambdas, and the reified `subclass(T::class)` nav registration resolves its serializer at compile time.

**Enforce.** D61 step 3 plus reading the release log once, deliberately.

## D64 — `ui-tooling` is debug-only

**Rule.** Only `:androidApp` depends on `compose.ui-tooling`, via `debugImplementation`. Library modules
take `ui-tooling-preview` (the annotations) and nothing more.

**Why.** `ui-tooling` is the preview *renderer* and must stay out of release. A plain
`implementation(libs.compose.uiTooling)` in a KMP module's `androidMain` lands in **every** variant.

**Enforce.** Review only; worth a comment in the catalog (D8).

## D65 — Preview the stateless composables

**Rule.** Each feature's stateless `...Content` composables get `@Preview`s in `androidMain`, covering the
states that are **awkward to reach by hand** — terminal load failure, empty, submitting, a validation error
on two fields at once.

**Why.** Those are exactly the states D29 and D32 exist for, and exactly the ones nobody checks manually.
The previews also keep D44 honest: a `Content` that is not stateless cannot be previewed.

**Enforce.** Compilation, and the debug build (D61 step 2).

---

# Phase 5 — Day one

An order that works. Each step is buildable before the next.

1. **Skeleton.** `settings.gradle.kts` with `:androidApp`, `:shared`, `:core:common`, `:core:testing`.
   Version catalog (D8), `version.properties` (D7), `gradle/wrapper`.
2. **The allowlist task** (D4) with entries for exactly those modules. It should fail if you add a module
   and forget it — check that it does, now, while the graph is small enough to see.
3. **`core:common`**: `AppResult`, `DomainError`, `ValidationField`, `UseCase`/`FlowUseCase`,
   `CoroutineDispatchers`, `Logger`, `AppInitializer`/`AppCoroutineScope` (D9–D15). `TodayProvider` only if
   the product has dates (D16).
4. **`core:testing`**: `TestCoroutineDispatchers`, `RecordingLogger`, `NoOpLogger` (D40). Small now,
   unblocks every test later.
5. **`core:database`**: the two funnels, `asAppResult`, `checkSingleRowAffected`, `DatabaseDriverFactory`,
   `inMemorySqlDriver` in `core:testing` (D17–D23, D41). **Test the funnels with a blocking block, not a
   `delay`** (D18).
6. **`core:mvi`**: markers, `BaseViewModel` with keyed `observe`, `UiMessage`, `LoadableState`
   (D25–D29). Add `RestorableViewModel` (D30) when the first form appears, not before.
7. **`core:ui`**: theme and tokens, `LoadingView`, `ErrorView`, `LoadableContent`, `MessageHost`,
   `CollectEffects` (D31–D34).
8. **`core:navigation`** *(nav3-specific)*: `Navigator`, the three contracts, `NavAnimation` (D35–D39).
9. **`:shared`**: `initKoin` + `appModules()` + the Compose root (D53–D55). At this point the app launches
   with no features.
10. **The whole-graph test** (D56). Write it with zero features — it is cheap now and it is the test that
    stops being written later.
11. **The first feature**, whole: `domain/` port and model, `.sq` schema, `data/` implementation, one
    screen, `di/` module, nav contributions (Phase 2). Follow it with its fakes (D59).
12. **CI** (D61) and `proguard-rules.pro` (D63). Run the release build once, read the log, confirm the keep
    rules do what D63 says.
13. **The second feature.** This is the real test of Phase 0: if it needs an edit anywhere in `:shared`
    other than one entry in `featureModules`, something in D36 or D54 is not done.

## What to leave out

- **`core:network` until there is a backend**, and unregistered even then until something calls it (D57).
- **`RestorableViewModel` until the first form.** It is the one `core:mvi` piece with no use on a read-only
  screen.
- **A use case per CRUD verb** (D43).
- **A detekt baseline** (D60).
- **`iosX64`** (D6).
- **A convention plugin for the build scripts** (D5) — three attempts, all reverted.
- **Anything a feature does not have two instances of.** `core:*` earns a component when the second
  feature needs it; the exceptions are the error-path components (D29, D32), which earn it on the first
  screen because the first screen is where the branch gets omitted.

---

# Appendix A — a drop-in `CLAUDE.md`

Paste into the new repo and replace `<App>` / `<pkg>` / the feature names. It is written for a coding agent
working in the repo: rules and their reasons, no history. Cross-references like `D17` point back at this
document.

````markdown
# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project status

A Kotlin Multiplatform + Compose Multiplatform app. `androidApp` and `iosApp` are thin entry points; all
logic and UI live in shared Kotlin modules. Pure MVI (one `BaseViewModel` per screen calling usecases or
ports directly), Koin for DI, SQLDelight for storage, one Gradle module per feature.

Architecture derived from `docs/architecture-blueprint.md`; `D<n>` references point there.

## Commands

| Task | Command |
| --- | --- |
| Android debug APK | `./gradlew :androidApp:assembleDebug` |
| Android release APK (the only build that runs R8) | `./gradlew :androidApp:assembleRelease` |
| All JVM unit tests | `./gradlew testAndroidHostTest` (scope with `:core:mvi:testAndroidHostTest`) |
| iOS tests for a module | `./gradlew :core:mvi:iosSimulatorArm64Test` |
| A single test class | append `--tests "<pkg>.core.mvi.BaseViewModelTest"` |
| Static analysis | `./gradlew detekt` (add `--auto-correct` to fix formatting) |
| Architecture rules | `./gradlew architectureCheck` |
| Export a SQLDelight schema after editing a `.sq` | `./gradlew generateCommonMainAppDatabaseSchema` |

Run the iOS app from Xcode, not via Gradle.

- Run the **release** build before trusting a change to `proguard-rules.pro` or to anything reflective —
  R8 runs in no other configuration.
- `iosSimulatorArm64Test` needs an installed simulator *runtime*, not just the SDK. Where
  `xcrun simctl list runtimes` is empty it fails with "Xcode does not support simulator tests" regardless
  of the code; `./gradlew linkDebugTestIosSimulatorArm64` still checks those tests compile and link. This
  matters because the datasource, cache and DI-graph tests live in `commonTest`.
- detekt has no baseline and the build fails on any issue. Type resolution is deliberately not configured
  (KMP false positives around `expect`/`actual`), so the `coroutines` ruleset does not run.

## Module graph

`feature -> core`, never the reverse and never feature -> feature. `:core:database`, `:core:mvi` and
`:core:network` see `:core:common` only, never each other. `:core:ui` and `:core:navigation` see nothing.
`:core:testing` may reach several, because it provides their fakes, and is test-only everywhere.

**This is enforced** by the allowlist in the root `build.gradle.kts` (`./gradlew architectureCheck`), which
reads the build scripts as text — a new module needs an entry there or the task fails. A type appearing in
a module's public signature is declared with `api(...)`, not `implementation(...)`.

## Rules

**Layering inside a feature is `ui -> domain <- data`.** Datasource *interfaces* live in `domain/`, their
SQLDelight implementations in `data/`. Business rules — validation, what a cleanup changes — belong in
`domain/`, never in a ViewModel.

**Use cases hold rules, not verbs.** A use case exists where there is policy. Plain CRUD goes straight to
the port — an edit ViewModel takes the datasource directly rather than an `AddX` class per operation.

**Failure is a value.** Everything below the UI returns `AppResult<T>`; nothing throws across a module
boundary. A `DomainError`'s `message` is driver text for logs — never render it. `NotFound` is separate
from `Database` because it is the one storage outcome a retry cannot fix. A `Validation` error carries a
*set* of fields and no message, so one submit reports every bad input; `validationFields<F>()` is how a
screen asks which of its own fields are to blame.

**All database access goes through the funnels.** `safeQuery` for reads (5s deadline), `safeMutation` for
writes (**no deadline** — a timeout on a write makes its outcome unknowable; see D17). Both take a logger
and an operation name, not optionally. `checkSingleRowAffected` on updates and deletes, never on a
single-row insert. Every `selectAll` carries an explicit `ORDER BY`; every observe chain **ends in
`flowOn(dispatchers.io)`**.

**Screens** are a thin `@Composable` resolving the ViewModel plus a stateless `...Content` composable
taking the state and one `onEvent: (Event) -> Unit`; child composables never take the ViewModel. All UI
state — dialog visibility, the current message — lives in the `MviState`; effects are for genuinely
one-shot events such as navigation. State that is *user input* additionally goes through
`RestorableViewModel`, which saves **only what the user typed**, never loaded data.

**`LoadableContent` and `MessageHost` are not optional** — a screen adds them rather than re-deriving the
spinner/error/content branch and the snackbar sequence. `isSubmitting` has no default: a ViewModel refuses
re-entry on it *and* the screen disables the control. On a list screen derive it from a set of in-flight
ids, so one slow write does not disable every other row.

**`observe(key, ...)`** folds a `Flow<AppResult<T>>` into state; re-calling with the same `key` replaces
that subscription, which makes retry a one-liner. **The `key` is what lets one screen observe two
sources.** `onData` must stay pure (`updateAndGet` may re-run it); per-emission side effects go in
`onEmission`.

**An event carries ids, never entities** — the same rule a `NavKey` follows. A whole entity in an event
plans from a snapshot captured when the card was drawn, ignoring the live list in the ViewModel receiving
it.

**Model variants as sealed types** carrying only the fields they have, with the rules on the type as a
`when (this)`, so adding a kind breaks the build everywhere it must be accounted for. A schema is a
storage format, not a domain model — the datasource owns the translation.

**Anything that renders or schedules against a date uses `todayFlow()`**, not `today()`: a query flow
re-emits only when its table is written, so a quiet night leaves the UI drawing yesterday's window.

**Strings**: messages are typed enums the UI resolves. Every user-visible string is a Compose resource in
the feature's `composeResources/values/strings.xml`, including any string a `:core:ui` composable renders —
which it therefore takes as a parameter. The exception is the generic action vocabulary in `:core:ui`.

**The composition root names no feature.** Tabs, nav entries and `NavKey` serializers arrive through Koin
multibindings; `featureModules` is the only place a feature is named. Each contract needs a **distinct
class per feature** — Koin keys definitions by type, so two `single<FeatureTab>` declarations silently
override each other.

**Tests**: anything not platform-specific lives in `commonTest`, so it runs on the JVM host *and* on iOS —
including the datasource, key-value cache and DI graph tests. `androidHostTest` is for things that
genuinely need the JVM. Fakes live in `commonTest` next to the port they fake, except fakes over `core:*`
types, which live in `:core:testing`. A fake's failure seam is **per operation** and carries its own
`DomainError`; it can also fail *after* emitting, which is the only way to reach a screen's non-terminal
error branch, and gate writes open, which is the only way to test a re-entry guard. Each feature's
stateless `...Content` composables have `@Preview`s in `androidMain` covering the states that are awkward
to reach by hand.

## Build infra

Each module applies its own KMP + Android + iOS config and its own `detekt { }` block. **Copy an existing
`core:*` module's `build.gradle.kts` as the starting point for a new one.** That duplication is
deliberate — see D5 for the three de-duplication attempts that were tried and reverted. Revisit on a newer
Gradle.

There is no `iosX64` target: Compose Multiplatform and the JetBrains lifecycle/navigation artifacts
publish `iosArm64` and `iosSimulatorArm64` only, so declaring it fails dependency resolution rather than
producing an Intel build.

Dependencies are centralized in `gradle/libs.versions.toml` and referenced as `libs.xxx` /
`libs.plugins.xxx`. Only `material-icons-core` is on the classpath — an icon outside that set has to be
vendored.

Release builds run R8. `proguard-rules.pro` deliberately keeps `SourceFile`/`LineNumberTable` (there is no
crash reporter, so an unreadable release trace is an unexplainable bug) and the *names* of
`RestorableViewModel` subclasses, `AppInitializer` implementations and every `Throwable` — all three reach
the log through a class name, which R8 renames. It carries no kotlinx.serialization rules: the library
ships its own.

`compose.ui-tooling` is the preview *renderer* and must stay out of release — only `:androidApp` depends
on it, via `debugImplementation`. Library modules take `ui-tooling-preview` (the annotations) and nothing
more.

A module that owns a `composeResources/` tree must set `android { androidResources { enable = true } }`,
or its strings never reach the APK and `stringResource()` throws at first composition. CI asserts the
bundles are in the release APK.

The version lives once, in `version.properties`: `androidApp` reads it as a properties file and
`iosApp/Configuration/Config.xcconfig` `#include`s it, so the platforms cannot drift.
````

---

# Appendix B — the entries that matter most

If the next project adopts five things from this document, these are the five with the worst failure modes
and the lowest cost:

| | Entry | Cost | What it prevents |
| --- | --- | --- | --- |
| 1 | **D4** — enforce the module graph with a task | an afternoon | every other rule here decaying silently |
| 2 | **D17** — no deadline on writes | one signature | reporting failure over a write that committed, and the duplicate row the user then creates |
| 3 | **D56** — resolve the whole graph in a test | an hour | a runtime DI crash on whichever screen the user opens |
| 4 | **D29** — `isSubmitting` with no default | one interface property | double-submit duplicating every record in the app |
| 5 | **D23** — `flowOn` at the end of every observe chain | one operator | mapping an entire table on the UI thread on every write |

The unifying property: each converts a rule somebody has to remember into something the compiler, the
build or the type system remembers instead. That is the whole thesis of this document, and D4 is its
sharpest instance.
