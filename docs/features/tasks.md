# Tasks — business logic

`:feature:tasks`. The dashboard of things to do over a rolling window of days,
plus the form that creates and edits them.

Status: living document — it describes the app as it is, not a proposal. The
previous, pure-Android implementation of the same feature is described in
[`../specs/2026-09-08-legacy-todo-grocery-business-logic.md`](../specs/2026-09-08-legacy-todo-grocery-business-logic.md);
where the two disagree, this file wins and the difference is called out below.

## Domain model

A task is a name, an optional description, and **a schedule that says when it is
due**. The schedule is a sealed type (`TaskSchedule`), not a set of flags:

| Kind | Fields | Means |
| --- | --- | --- |
| `Asap(createdOn, hour?)` | the day it was raised | due today, every day, until completed |
| `OneTime(date, hour?)` | one date | due once |
| `Periodic(startDate, daysInterval, endDate?, hour?)` | an anchor and a step | due every `daysInterval` days from `startDate`, up to and including `endDate`, forever when `endDate` is null |

`createdOn` on an ASAP task is **not a due date**. An ASAP task is due today
whatever today is; the date exists only so the row has something to store and
order by.

Each kind carries exactly the fields it has. The legacy app kept `asap`,
`daysInterval`, `startDate` and `endDate` on every task and derived the kind from
them, which made `asap = true, daysInterval = 5, endDate = someDay` a
constructible, storable contradiction that no rule honoured.

`TaskType` (`Asap` / `OneTime` / `Periodic`) exists only for the places that have
to *name* the kind — the edit form's three radio buttons.

### Validation

`TaskDraft.create(...)` is the only way to build a valid task, and it reports
**every** offending field at once (`DomainError.Validation` carries a set of
`TaskField`):

- `Name` — required, non-blank. Saved trimmed.
- `DaysInterval` — required and `> 0` **when the type is Periodic**; ignored
  otherwise. This is a deliberate change from the legacy app, where a blank
  interval on a Periodic task silently saved `0` and the task then behaved like a
  one-off despite what the user had picked.
- `EndDate` — rejected when it falls before the start date. Only considered when
  the type is Periodic; on the other two types whatever is in the field is
  discarded rather than validated.

The description is trimmed, and an empty one is stored as null rather than `""`.

A task's start date is the date the user picked, **except for ASAP**, which is
always anchored at today at save time — so re-saving an ASAP task snoozes it back
to today.

## The recurrence rules

Every rule in the feature is expressed through these, which live on
`TaskSchedule`, rather than re-derived per caller. They used to be re-derived in
three places, in three different orders, none exhaustive.

- **`occursOn(date, today)`** — ASAP: `date == today`. One-time: `date` equals its
  own date. Periodic: on or after `startDate`, a whole number of intervals from
  it, and not past `endDate`.
- **`occurrenceOnOrAfter(date)`** — where the schedule moves to. Null for ASAP and
  one-time, which have no later anchor; for periodic, the smallest whole number of
  intervals that lands on or after `date` (integer arithmetic, not `ceil` over
  doubles), and null if that would land past `endDate`.
- **`nextOccurrenceAfter(date)`** — `occurrenceOnOrAfter(date + 1 day)`.
- **`hasExpiredBy(today)`** — ASAP never expires; a one-time task has expired once
  its date is in the past; a periodic task once its `endDate` is.
- **`startingFrom(date)`** — re-anchors a periodic task; returns the other two
  unchanged.

**Null from `occurrenceOnOrAfter` means finished**, and both callers act on it by
deleting. That is why it is an exhaustive `when` over the schedule kinds rather
than a cast: a new repeating kind must not silently fall through to "delete on
completion".

## The dashboard

A rolling window of **8 days starting today** (today + 0 … today + 7). Every one
of the eight is rendered, including days with nothing on them.

A task appears on a day when its schedule says it occurs there — so a periodic
task appears on **several** pages of the window at once, and an ASAP task appears
only on today's.

Within a day, tasks are ordered by:

1. tasks with a specific hour, in clock order;
2. ASAP tasks without an hour;
3. one-time tasks without an hour;
4. periodic tasks without an hour;

and finally by id, which is what makes the order total — without it two untimed
tasks in the same bucket compared equal and swapped places whenever storage
returned them in a different order.

The window is recomputed on every write to the tasks table **and** at every local
midnight, because a query flow re-emits only when its table is written: a phone
left on this screen overnight would otherwise go on labelling yesterday's column
"today".

## Completing a task

Reached by tapping a card to reveal its actions, then Complete.

Completion is **per occurrence**, not per task: the rule is applied to the day
whose card was ticked, not to the task's own anchor. A two-day periodic task is on
screen four times in the window, and advancing from its anchor regardless meant
ticking the card four days out left that card where it was and made today's
disappear instead.

- The task moves to its next occurrence strictly after the completed day, if it
  has one.
- If it has none — every ASAP task, every one-time task, and a periodic task whose
  next step would pass its end date — it is **deleted**.

The move writes the schedule columns only; see *Two shapes of write* below.

Each in-flight completion disables only its own card. One screen-wide flag
disabled every card on every visible day and silently dropped taps on the others,
which on a day with several tasks is the normal way to use the screen.

## The daily catch-up

`UpdateTasksDates`, driven by `TasksCleanupInitializer`, which collects the
midnight-aware "today" stream for the lifetime of the app. It is **not** tied to a
screen: hanging it off the dashboard ViewModel meant a user who stayed on another
tab never ran it and every tab switch ran it again; running it at startup alone
meant a phone that never restarts the process went days without one.

Throttle: once per calendar day, against a stored last-cleanup date. A failure to
*read* that date is not fatal — the pass is idempotent, so the safe answer to "I
cannot tell when this last ran" is to run it. Aborting instead meant one
unreadable stored value disabled the cleanup for the life of the install, because
the write that would replace it sat behind the read that kept failing.

Per task, for the day being run:

- **Expired** (a one-time task whose date has passed; a periodic task past its end
  date) — deleted. ASAP tasks never expire and are never touched.
- **A periodic task anchored in the past** — rolled forward in one jump to its
  first occurrence on or after today, preserving the phase of the recurrence, so
  the user sees the current occurrence rather than a backlog of missed ones. If it
  has no such occurrence left, it is deleted rather than rolled into a row that
  could never be shown again.

The read, the decision and the writes happen **inside one transaction**. Reading
first through a separate query meant the plan was computed from rows that could
have moved by the time it was applied, silently undoing an edit made in between.

The last-cleanup date advances only if the pass succeeded, so a failure retries at
the next opportunity. Nothing waits for the cleanup: the dashboard observes the
table, so whatever it changes re-emits by itself. Its failures go to the log, not
to a snackbar on a screen that did not ask for it.

**Legacy difference.** The legacy Gherkin asserted a 7-day throttle while the
shipped code did one per calendar day. The daily behaviour is the one kept, and it
is now driven by the date changing rather than by a screen being opened.

## The edit form

- The three types toggle which inputs are shown: ASAP shows none of start date /
  end date / interval; Specific day shows start date and hour; Periodic shows
  start date, hour, end date and interval.
- Start date defaults to **today, kept live** — a form left open across midnight
  must not default to yesterday.
- Changing the start date drags an end date that would now precede it forward with
  it, rather than leaving it behind to fail a validation whose cause is off
  screen. Only an end date that *exists*: a null end date means "repeats forever",
  and collapsing the two cases meant picking a start date on a fresh form invented
  an end date equal to it — a periodic task that ran exactly once.
- The interval is held as text while the user is typing in it, and parsed on save.
- Delete asks for confirmation. It is the one irreversible thing on the screen and
  sits next to Save.
- The screen **observes** its row rather than reading it once. It seeds the form
  from the first emission only, and uses the subscription to notice the row being
  deleted underneath it (`TaskNoLongerExists`). There is deliberately no one-shot
  read: the daily catch-up moves rows while the app is running, so a snapshot
  turns a save into a read-modify-write over stale data.
- A form that never loaded counts as empty, so the screen cannot render a blank
  but live form over a task it failed to read — a Save from that state used to
  overwrite the real row with empty fields.

## Two shapes of write

`editTask` writes the whole row and **only the edit form calls it**, because only
the form owns every field at once. `rescheduleTask` writes the schedule columns
only, and is what completion and the daily catch-up use: both act on a task read
earlier, and a whole-row update from either pushed a stale name and description
over anything edited since — reported by SQLite as success.

## Storage shape

The table keeps the flat columns the legacy app used — `startDate`, `endDate`,
`hour`, `daysInterval`, `asap` — and `SqlDelightTasksDatasource` owns the
translation to and from `TaskSchedule`. A schema is a storage format, not a domain
model.

Reading back: `asap` wins, then a positive `daysInterval` means periodic, and
anything else is a one-off.

Writing an ASAP or one-time task writes `endDate = null`, so switching a task away
from Periodic **clears** the stale end date. The legacy app wrote it verbatim
whatever the type, leaving inert dead data behind (legacy open decision 5).

`selectAll` carries an explicit `ORDER BY id`; SQLite does not promise insertion
order for a plain scan and stops giving it after a VACUUM.
