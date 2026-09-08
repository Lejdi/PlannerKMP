# Legacy app business logic (source of truth for KMP reimplementation)

Date: 2026-09-08
Status: reference
Source: `~/Downloads/Planner-master` — a previous, pure-Android (Room + Hilt +
Compose, MVI) implementation of this same app. Read for its business logic
only; nothing about its module layout, DI, or persistence tech should carry
over — those are superseded by this repo's `:core:*` architecture (see
`docs/superpowers/specs/2026-09-07-base-architecture-design.md`). This
document exists so the two features it implements — a TODO/task list and a
grocery list — can be rebuilt with identical behavior on top of the new
architecture.

Every rule below is transcribed directly from the legacy app's use cases,
mappers, and its Cucumber `.feature` files (which double as executable
acceptance tests), not paraphrased from memory — file references are given so
behavior can be double-checked against the original source if needed.

## Feature 1 — TODO / task list

### Domain model

A task (legacy `Task`, `business/data/model/Task.kt`) has:

- `id: Int`
- `name: String` — required, non-empty (the only validated field on the edit
  form; `NotEmptyValidation` blocks Save and shows an inline error otherwise)
- `description: String?` — free text, optional
- `startDate: Date` — always normalized to local noon (`setNoon()`) whenever
  read from storage or from the display/control string format, to avoid
  midnight/DST edge cases in day-difference math
- `endDate: Date?` — only meaningful for periodic tasks; noon-normalized like
  `startDate`
- `hour: Time?` — optional `{ hour, minute }`, a specific time-of-day
- `daysInterval: Int` — `0` means "not periodic"; `> 0` is the repeat interval
  in days
- `asap: Boolean`

There is no separate "task type" enum in storage — a task's type is always
derived from `asap` + `daysInterval`:

| Type | `asap` | `daysInterval` |
|---|---|---|
| ASAP | `true` | `0` |
| Specific day (one-time) | `false` | `0` |
| Periodic | `false` | `> 0` |

### Dashboard: which tasks show on which day

The dashboard always shows a rolling window of **8 days** starting today
(`AppConfiguration.NUMBER_OF_VISIBLE_DAYS`, `today + 0` … `today + 7`), even
on days with zero tasks — every day column always renders.

For each of those 8 days, a task is included if (first matching rule wins,
`GetTasksForDashboard.filterTasksForDate`):

1. **ASAP**: shown only on the day that is "today" — never on the other 7
   days in the window. (So an ASAP task effectively disappears from view,
   without being deleted, on days that aren't "today" — it reappears once
   that day becomes "today" again, e.g. after being skipped.)
2. **One-time** (`daysInterval == 0`, not asap): shown only on the exact day
   equal to its `startDate`.
3. **Periodic** (`daysInterval > 0`): shown on day `d` iff `d >= startDate`,
   `(d - startDate) % daysInterval == 0`, and (`endDate == null` **or**
   `d <= endDate`) — i.e. it recurs every `daysInterval` days starting at
   `startDate`, forever if `endDate` is null, otherwise up to and including
   `endDate`.

Within a day, tasks are sorted by `priority` then by `hour` string
(`TaskDisplayableMapper.calculatePriority`):

1. has a specific `hour` set (regardless of asap/periodic/one-time)
2. ASAP (no hour)
3. one-time (no hour, not asap)
4. periodic (no hour, not asap) — lowest priority, listed last

### Completing a task (`MarkTaskComplete`)

Only reachable from the dashboard by tapping a task card once (reveals
Edit/Complete buttons — a pure UI toggle, not persisted) then tapping
Complete.

- `daysInterval == 0` (covers both ASAP and one-time): **delete** the task
  outright.
- `daysInterval > 0` (periodic): compute `newStartDate = startDate +
  daysInterval` days.
  - If `endDate` is set and `newStartDate > endDate`: this was the last
    occurrence — **delete** the task.
  - Otherwise (no `endDate`, or `newStartDate <= endDate`): **update** the
    task, setting `startDate = newStartDate` (everything else unchanged) —
    i.e. advance to the next occurrence. It only ever advances by exactly one
    interval; it relies on the cleanup job below to have already fast-forwarded
    a task's `startDate` if the user let it lapse, so "complete" is always
    completing the currently-due occurrence.

### Adding / editing a task (edit screen → `AddTask` / `EditTask` / `DeleteTask`)

The use cases themselves are pure pass-throughs to the datasource — all the
business rules live in how the edit form builds the `Task` object on Save,
driven by a 3-way radio (ASAP / Specific day / Periodic):

- Selecting a type toggles which fields are visible: ASAP shows none of
  start-date/hour/end-date/interval; Specific day shows start-date + hour;
  Periodic shows start-date + hour + end-date + days-interval.
- `startDate`: for ASAP, **always reset to "today" at save time** regardless
  of what was previously stored (so re-saving an ASAP task effectively
  "snoozes" it back to today); for the other two types, the user-picked date.
- `endDate`: saved verbatim from the end-date field **regardless of the
  selected type** — the field is only shown/editable when type is Periodic,
  but if a task was Periodic (with an end date set) and is then switched back
  to Specific day before saving, the stale `endDate` is written anyway. This
  is harmless dead data because both the dashboard filter and the cleanup job
  (below) ignore `endDate` entirely whenever `daysInterval == 0` — but it's a
  quirk worth deciding on deliberately rather than reproducing by accident.
- When the start-date picker changes and the currently-selected end date
  would now fall before it, the form auto-snaps `endDate` to equal the new
  `startDate` (UI-level convenience, not enforced by any usecase).
- `hour`: optional; retains whatever value was already in form state even
  while its field is hidden (ASAP).
- `daysInterval`: only taken from the typed field when type is Periodic,
  forced to `0` otherwise. **There is no minimum-value validation** — leaving
  the interval field blank on a Periodic task silently saves `daysInterval =
  0`, which makes that task behave exactly like a one-time task (shown once,
  then pruned by cleanup) despite the user having picked "Periodic". Only the
  `name` field is validated as non-empty.
- Deleting a **new, not-yet-saved** task (opened via the "+" button) just
  navigates back without ever calling `AddTask` — nothing was persisted, so
  there's nothing to delete.
- Editing an existing task's type (e.g. one-time → periodic) is not a
  special migration path — it's just constructing a `Task` with different
  field values from the same form, using the same `EditTask` use case.

### Outdated-task cleanup (`UpdateTasksDates`) — the "cache cleanup" feature

Runs unconditionally every time the dashboard screen's ViewModel is created
(app launch / returning to the dashboard), gated by a throttle:

- **Throttle**: compares "today" to a persisted last-cleanup date
  (`LastCacheCleanupDataStoreInteractor`, key `LAST_CLEANUP_DATE_KEY`). Runs
  if there is no stored date yet (first-ever launch), or if today is a later
  calendar day than the stored date; otherwise skipped.
  - **Known discrepancy to resolve, not silently copy**: `CacheCleanup.feature`
    describes/asserts a **7-day** threshold (cleanup runs after the last
    cleanup was ≥7 days ago; a 3-day gap is asserted to be skipped). The
    actual shipped guard has no such constant anywhere in the codebase — it's
    "today is a different calendar day than last cleanup," i.e. **at most once
    per day**, not once per week. The Gherkin scenario for the "skipped"
    case would not actually pass against this implementation. Decide
    intentionally which behavior the KMP version should have (daily throttle,
    as shipped, or the originally-intended weekly one) rather than assuming
    the tests describe the real behavior.
  - When it does run and completes with no cache errors, the last-cleanup
    date is updated to today. If any delete/update in the pass below fails,
    the date is **not** advanced, so cleanup is retried next opportunity.
- **Per-task rules** applied to every cached task when cleanup runs:
  - ASAP tasks are **never touched** (never deleted, never date-adjusted).
  - One-time task (`daysInterval == 0`, not asap): **deleted** if its
    `startDate` is strictly before today.
  - Periodic task (`daysInterval > 0`): **deleted** if it has an `endDate`
    that is strictly before today (a periodic task with no `endDate` is never
    deleted this way).
  - Every task that survives deletion is then checked for a **date
    catch-up**: if it's periodic (`daysInterval > 0`) and its `startDate` is
    in the past, its `startDate` is fast-forwarded in one jump — computing
    how many whole intervals have elapsed (`ceil(daysSinceStart /
    daysInterval)`) and adding that many intervals at once — to the next
    occurrence that is today or later, preserving the original phase/
    alignment of the recurrence. (One-time and ASAP tasks are never
    date-adjusted this way — only deleted-or-left-alone.)
- Net effect: tasks are never silently lost except when they're truly
  finished (a past one-time task, or a periodic task past its end date);
  periodic tasks that are still "alive" but whose `startDate` fell behind
  (e.g. the app wasn't opened for a while) get their `startDate` snapped
  forward to the correct current occurrence instead of showing a backlog of
  missed occurrences.

### Formatting / persistence details worth preserving

- Dates are persisted as `ddMMyyyy` strings; display format is
  `"dd MMMM yyyy\nEEEE"` (two lines, e.g. `13 July 2025` / `Sunday`); the
  date-picker control format is `dd-MM-yyyy`.
- Time-of-day is persisted as zero-padded `HH:mm`.
- Day-difference math normalizes both dates by adding each `Calendar`'s own
  DST offset before diffing, so day counts don't shift across DST
  transitions.
- Every cache write/read is wrapped with a timeout and treated as a hard
  failure (surfaced as a generic cache error to the UI) on timeout, thrown
  exception, or — defensively — an insert/update/delete that didn't affect
  the expected row count.

## Feature 2 — grocery list

Model (`GroceryItem`): `id`, `name`, `description?` — no dates, no
scheduling, no ASAP/periodic concept. Deliberately much simpler than `Task`.

- **Add**: a "+" button expands an inline row with name + description
  fields; confirming calls `AddGrocery` with a new item and collapses the row
  again. **No validation** on either field — unlike tasks, an empty name is
  allowed to be saved. Worth deciding deliberately for the new app rather
  than treating as accidental parity to preserve.
- **Complete** (tap a card's button while not in edit mode): calls
  `DeleteGrocery` — completing an item **permanently removes it** from the
  list. There's no undo/history.
- **Edit mode**: long-pressing a card arms a purely local, unpersisted
  "edit mode" flag; the next tap on that same card's button then calls
  `EditGrocery` instead of delete. However, `EditGrocery` is only ever called
  with the *same, unmodified* `id`/`name`/`description` read back from the
  list — there is no inline text-editing UI wired up anywhere on this screen,
  so in the legacy app "long-press then tap" is a no-op round-trip write.
  This reads like a scaffolded-but-never-finished feature rather than an
  intentional no-op; decide whether the KMP version should actually implement
  editable name/description for grocery items, or drop the "edit" affordance
  entirely.
- No periodic cleanup exists for groceries — items live until explicitly
  completed by the user.

## Open decisions for the reimplementation

These are places where the legacy app's *tests* and its *actual code* disagree,
or where behavior looks unintentional — flagging them here so they're a
deliberate choice in the new app, not an accidental carry-over:

1. Cache-cleanup throttle: implement the shipped "once per calendar day"
   behavior, or the "once per 7 days" behavior the Gherkin scenarios assert?
2. Should a Periodic task with a blank/zero days-interval be rejected by
   validation (unlike today, where it silently degrades to one-time
   behavior)?
3. Should grocery item name be required (matching task-name validation),
   given the legacy app currently allows empty names?
4. Should grocery items support actually editing name/description, given the
   legacy "edit mode" is UI-only and never wired to a real edit form?
5. Should a task's stray `endDate` be cleared when its type is switched away
   from Periodic, even though it's inert dead data today?
