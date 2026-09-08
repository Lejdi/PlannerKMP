# Tasks + grocery feature implementation design

Date: 2026-09-08
Status: approved

## Goal

Implement the app's first two real features — a TODO/task list and a grocery
list — as full vertical slices (domain, data, ViewModel, Compose UI, DI, nav)
on top of the base architecture from
`docs/superpowers/specs/2026-09-07-base-architecture-design.md`, reproducing
the business logic transcribed in
`docs/specs/2026-09-08-legacy-todo-grocery-business-logic.md`, with these
open decisions from that document resolved as follows:

1. Cache-cleanup throttle: **once per calendar day** (the shipped behavior),
   not the Gherkin-asserted 7-day interval.
2. A Periodic task with a blank/zero days-interval: **no new validation** —
   it silently behaves as one-time, same as the legacy app.
3. Grocery item name: **required**, validated non-empty like task name.
4. Grocery items: **support real editing** of name/description.
5. A task's stray `endDate`: **cleared** (set to `null`) whenever the saved
   type isn't Periodic, instead of left as inert dead data.

## Module graph

```
:feature:tasks    -- Task domain/data/ui, depends on core:common, core:mvi,
                     core:database, core:navigation, core:ui
:feature:grocery  -- GroceryItem domain/data/ui, same core:* dependencies

:core:common      -- + TodayProvider (new)
:core:database    -- + generic SQLDelight-backed KeyValueCache impl (new)
:shared           -- + tasksModule/groceryModule in initKoin(), bottom-nav
                     Scaffold + NavDisplay in App()
```

Dependency rule unchanged: `feature -> core`, never the reverse. `:feature:tasks`
and `:feature:grocery` do not depend on each other.

Each feature module applies `kotlinMultiplatform` + `androidMultiplatformLibrary`
(as every `core:*` module already does) plus `composeMultiplatform` /
`composeCompiler` (for its screens) and `sqldelight` (for its own `.sq`
schema) — copying the plugin set from `shared/build.gradle.kts` and an
existing `core:*` module respectively, per `CLAUDE.md`'s stated convention of
duplicating this block rather than a shared convention plugin.

Internal package layout, both modules:

```
pl.lejdi.plannerkmp.feature.<name>/
  domain/   model + usecases (implement core:mvi's UseCase<P, R>)
  data/     Datasource interface + SQLDelight-backed impl + .sq schema + mappers
  ui/       Compose screens + ViewModel + State/Event/Effect
  di/       Koin module
  <Feature>NavKeys.kt, <Feature>NavEntryProviderContributor.kt
```

## `core:common` addition: `TodayProvider`

"Today" drives the dashboard window, ASAP visibility, and cleanup, so it must
be injectable rather than read from the system clock directly:

```kotlin
interface TodayProvider {
    fun today(): LocalDate
}

class SystemTodayProvider : TodayProvider {
    override fun today(): LocalDate =
        Clock.System.todayIn(TimeZone.currentSystemDefault())
}
```

Registered as a Koin singleton in `commonModule`. Tests use a fake that
returns a fixed `LocalDate`.

## `core:database` addition: persisted `KeyValueCache`

`core:database` already defines a generic `KeyValueCache<K, V>` contract with
only an in-memory fake (`InMemoryKeyValueCache`, used in tests). The cleanup
throttle needs one real persisted value (last-cleanup date), so this adds the
first real implementation:

```
core/database: keyvalue.sq
  CREATE TABLE keyValueEntry (id TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL);
  -- get/put/remove/clear queries keyed by id

class SqlDelightKeyValueCache<K, V>(
    private val queries: KeyValueEntryQueries,
    private val encodeKey: (K) -> String,
    private val serialize: (V) -> String,
    private val deserialize: (String) -> V,
) : KeyValueCache<K, V>
```

This makes `core:database` own one small generic schema — a deliberate,
narrow exception to its current doc comment ("this module does not itself
define any schema"); `CLAUDE.md`'s `core:database` description is updated
alongside this implementation to reflect it.

`core:database` also gains a `safeQuery` helper mirroring `core:network`'s
`safeRequest<T>`: wraps a SQLDelight call with a timeout (5s) and exception
handling, mapping both plus an unexpected affected-row-count to
`AppResult.Failure(DomainError.Database(...))`. Both feature datasources use
this for every read/write, matching the legacy app's "every cache write/read
is wrapped with a timeout and treated as a hard failure" behavior.

## Feature: tasks

### Domain model

```kotlin
data class Task(
    val id: Long,
    val name: String,
    val description: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val hour: LocalTime?,
    val daysInterval: Int,
    val asap: Boolean,
)

enum class TaskType { Asap, OneTime, Periodic }

val Task.type: TaskType
    get() = when {
        asap -> TaskType.Asap
        daysInterval > 0 -> TaskType.Periodic
        else -> TaskType.OneTime
    }
```

`id` is `Long` (SQLDelight's `INTEGER PRIMARY KEY AUTOINCREMENT` default
affinity) rather than the legacy app's `Int` — a mechanical consequence of
the new schema, not a behavior change. Dates use `kotlinx-datetime`'s
`LocalDate`/`LocalTime` (added to `libs.versions.toml`); being pure calendar
values with no time-of-day/DST component, day-difference and interval math
(`(d - startDate).days`, cleanup catch-up) is exact by construction, so none
of the legacy app's noon-normalization or DST-offset-adjustment code is
needed — same external behavior, simpler internals.

Dates/times are stored via SQLDelight column adapters using
`LocalDate`/`LocalTime`'s own ISO string representation, not the legacy
app's `ddMMyyyy`/`HH:mm` persistence format — that format was an artifact of
the legacy app's own storage layer, not a user-visible behavior, so it isn't
being reproduced. The user-visible **display** format (task card:
`"dd MMMM yyyy\nEEEE"`, e.g. `"13 July 2025\nSunday"`) and the edit form's
date-picker format are reproduced, since those are visible behavior.

### `GetTasksForDashboard`

Returns the 8-day window (`AppConfiguration`-equivalent constant,
`today..today+7`) as an ordered `List<DashboardDay>` (`data class
DashboardDay(val date: LocalDate, val tasks: List<Task>)`), one entry per day
even when empty, so the UI always renders all 8 columns.

Per-day filter (first match wins, exactly as spec'd):
1. ASAP: only on the day equal to `today`.
2. One-time (`daysInterval == 0`, not asap): only on the day equal to
   `startDate`.
3. Periodic (`daysInterval > 0`): on day `d` iff `d >= startDate`,
   `(d - startDate).days % daysInterval == 0`, and (`endDate == null` or
   `d <= endDate`).

Within a day, sort by priority bucket, then by `hour`:
1. has `hour` set (any type) — ordered by `hour` ascending
2. ASAP, no hour
3. one-time, no hour
4. periodic, no hour

### `MarkTaskComplete`

- `daysInterval == 0` (ASAP or one-time): delete the task.
- `daysInterval > 0` (periodic): `newStartDate = startDate + daysInterval
  days`. If `endDate != null && newStartDate > endDate`, delete (last
  occurrence); otherwise update `startDate = newStartDate`, everything else
  unchanged.

### Add/edit (ViewModel save-mapping, not a usecase concern)

Driven by a 3-way type selector (ASAP / Specific day / Periodic) exactly as
spec'd:
- Selecting a type toggles which fields are visible/editable, same as
  legacy.
- ASAP: `startDate` always reset to `today()` at save time (via
  `TodayProvider`), regardless of prior value.
- Specific day / Periodic: `startDate` is the user-picked date.
- `endDate`: taken from the field only when the saved type is Periodic;
  **cleared to `null` for the other two types** (resolves open decision 5 —
  a deliberate change from the legacy app's stray-data quirk).
- When the start-date picker changes such that the currently-selected end
  date would fall before it, the form auto-snaps `endDate` to equal the new
  `startDate` (UI-only convenience).
- `hour`: optional; retained across type changes even while hidden.
- `daysInterval`: taken from the field only when type is Periodic, forced to
  `0` otherwise; **no minimum-value validation** (resolves open decision 2 —
  a blank/zero interval on a Periodic task silently saves as `0`, behaving
  like one-time, same as legacy).
- `name`: the only validated field, non-empty (`NotEmptyValidation`-equivalent
  inline error, blocks Save).
- Deleting a new, not-yet-saved task just navigates back without calling
  `AddTask`.

### `UpdateTasksDates` (cleanup)

Runs unconditionally from the tasks dashboard ViewModel's `init`, throttled
via the new `SqlDelightKeyValueCache<Unit, LocalDate>` (single fixed key)
holding the last-cleanup date:
- Runs if no stored date yet, or if `today() > storedDate`; otherwise
  skipped (resolves open decision 1 — once-per-calendar-day, matching the
  shipped legacy behavior, not the Gherkin-asserted 7-day one).
- Per-task, applied to every stored task:
  - ASAP: never touched.
  - One-time: deleted if `startDate < today()`.
  - Periodic: deleted if `endDate != null && endDate < today()`.
  - Survivors that are periodic and have `startDate < today()`: fast-forward
    in one jump — `intervalsElapsed = ceil((today() - startDate).days /
    daysInterval)`, `startDate += intervalsElapsed * daysInterval days` —
    preserving phase/alignment.
- Last-cleanup date advances only if the whole pass completes without a
  `DomainError` from any delete/update.

## Feature: grocery

### Domain model

```kotlin
data class GroceryItem(val id: Long, val name: String, val description: String?)
```

### Usecases

`AddGrocery`, `EditGrocery`, `DeleteGrocery` — thin pass-throughs to the
datasource, same shape as the legacy app.

### ViewModel/UI behavior

- **Add**: "+" expands an inline row (name + description); confirming
  validates `name` non-empty (resolves open decision 3 — matches task-name
  validation, deviating from the legacy app's no-validation), calls
  `AddGrocery`, collapses the row.
- **Edit**: long-pressing a card expands it inline, prefilled with its
  current `name`/`description`, editable; confirming validates `name`
  non-empty (same rule as add) and calls `EditGrocery` with the edited
  values, collapsing back to a normal card (resolves open decision 4 —
  replaces the legacy app's dead-end "arm a flag, then tap the same button"
  round-trip with an edit that actually does something, reusing the same
  inline-expand pattern as add).
- **Complete**: tapping a card's action button in its normal (non-expanded)
  state calls `DeleteGrocery` — permanent removal, no undo, unchanged from
  legacy.
- No periodic cleanup for groceries — items live until explicitly completed.

## Navigation

`shared/App.kt` gets a `Scaffold` with a bottom `NavigationBar` (two items:
Tasks, Grocery). Each tab owns its own `Navigator`/back stack (tasks starts
at the dashboard, grocery starts at the list); both tabs share one
`entryProvider` built from `getKoin().getAll<NavEntryProviderContributor>()`,
since an entry provider only needs to know how to render a given `NavKey`,
not which back stack it came from — no change needed to the existing
`NavEntryProviderContributor` contract. `:shared`'s `initKoin()` adds
`tasksModule`/`groceryModule` to its module list.

## Error handling

Both datasources wrap every SQLDelight call with `core:database`'s new
`safeQuery` helper (see above), surfacing `DomainError.Database` through the
existing `core:ui` `ErrorView` the same way `core:network`'s `safeRequest`
already surfaces `DomainError.Network`. ViewModels map a `Failure` to an
effect (e.g. `ShowError`) rather than crashing.

## Testing

TDD throughout, per `superpowers:test-driven-development`. Fakes:
`FakeTasksDatasource` / `FakeGroceryDatasource` (in-memory, implement the
same datasource interface as the real ones), a fake `TodayProvider` (fixed
`LocalDate`), and the existing `InMemoryKeyValueCache` for cleanup-throttle
tests. Coverage focus:
- Dashboard filter + sort matrix (all three task types, boundary days,
  `endDate` edge cases).
- `MarkTaskComplete` delete-vs-advance-vs-delete-at-last-occurrence.
- Cleanup: per-type delete rules, catch-up math (including multi-interval
  jumps), and the once-per-day throttle (runs/skips/advances-on-success-only).
- Edit-form save-mapping rules (ASAP reset, `endDate` clearing, `daysInterval`
  forcing, no minimum-interval validation).
- Grocery add/edit/delete plus name validation.
- ViewModel tests following the existing `BaseViewModelTest` pattern
  (`UnconfinedTestDispatcher`).

Run via `./gradlew testAndroidHostTest` (whole suite) or scoped per module,
e.g. `./gradlew :feature:tasks:testAndroidHostTest`.
