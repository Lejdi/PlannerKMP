# Gym plan — business logic

`:feature:gym`. A weekly training plan: seven pages, one per weekday, each holding
the exercises planned for it. Today's page can be ticked off.

Status: living document. This feature has no predecessor in the legacy app; it is
new to the KMP version.

## Domain model

An exercise is an id, a name, an optional comment, a weekday, a series count, reps
per series, an optional weight, and the completion pair.

**An exercise belongs to a `DayOfWeek`, not to a date.** The seven pages repeat
every week; there is no row per date and no history table.

- `weight` is **optional, and null means bodyweight** — not a missing value. It is
  rendered as such rather than as a blank number.
- `completedSets` is how many series were ticked, and `completedOn` is the date
  they were ticked on (null when nothing is done). Neither is a boolean and
  neither is a row per day: "done" is a question about today, and today is not a
  property of the row. `DayExercise` is where the question gets answered, against
  a live clock.

### Validation

`GymExerciseDraft.create(...)` is the only way to build a valid one, and it
reports **every** offending field at once (`GymField`):

- `Name` — required, non-blank, saved trimmed.
- `Sets` — required, `1..MAX_SETS`.
- `Reps` — required, `1..MAX_REPS`.
- `Weight` — optional; when present, `0.0..MAX_WEIGHT`.

`MAX_SETS` is **20 because the list draws one checkbox per series**. That is a UI
constraint expressed as a domain rule on purpose: a fat-fingered 500 is not merely
odd data, it is a screen that cannot be used. The screen reads the same constant
rather than keeping a second copy of it.

The weight range does the work of a finiteness check too — NaN compares false
against everything and neither infinity falls inside the bounds — so a non-finite
weight is rejected before it reaches storage, where it would be a row nothing can
render and a column no comparison orders.

The counts and the weight arrive at the validator as nullable, because the form
holds them as text for as long as the user is typing: "not a number" and "out of
range" are the same failure to the user, and so the same field.

## The daily reset is derived, not stored

`doneSets` is `completedSets` when `completedOn` is today, and zero otherwise —
recomputed on every write to the table *and* at every local midnight.

So, exactly as in `:feature:routines`, **there is no cleanup job and nothing to
run overnight**. The date stops matching by itself.

The midnight half is not decoration: a query flow re-emits only when its table is
written, so a phone left on this screen overnight would go on showing yesterday's
ticks until something else happened to write.

`doneSets` is **clamped into `setsCount`**. The form may shrink the series count
under a row that is already ticked, and it deliberately does not touch the
completion columns when it does — so "five of three series done" is a state
storage can legitimately hold, and this is where it is resolved.

## Ticking a series

Completion is a **count**, not a set of series ids, so a tap means "this many are
done":

- tapping series *k* that is **not** done counts up to *k*;
- tapping series *k* that **is** done counts down to *k − 1*, the only direction a
  count can be un-ticked in.

Ticking the third box while none is ticked therefore marks three done. That is the
deliberate consequence of the count model, and a test pins it so it reads as
intended rather than as a bug.

The date is taken at **write** time, because a tick belongs to the day it was made
on and not to the day the screen was opened. Reaching zero clears the date: a row
with nothing done carries no date to compare against.

### Only today's page has checkboxes

A tick is stamped with today's date, so one made on another weekday's page would
be recorded as done today and would appear on the wrong page.

Other days show the same cards **as a plan** — still weight-editable, still
long-pressable to edit — just without the series checkboxes. The pager always
opens on today, so the state no layout hints at (a page showing a plan *without*
checkboxes) is never the first thing a user meets.

A done exercise **keeps its place at reduced alpha** rather than moving or
vanishing: the plan is an ordered routine, and reordering it as you work through
it would lose your place.

## The week

Seven days, always, Monday first as ISO has it. A weekday with nothing planned is
an **empty page saying so**, not a missing one — a missing page would be a hole in
the week.

The screen is a seven-page pager where the **page index is the weekday ordinal**,
with a peek row of chips above it. The chips are the one-tap path from Sunday back
to Monday, since the pager does not wrap. A chip marks which day is on screen,
which is today, and which days hold anything.

A full-screen "nothing here" message is reserved for an **empty week**; a single
empty day gets a line on its own page.

## Weight can be edited from the list

Tapping the weight on a card opens an **inline editor** in place, without going to
the form — changing the load is the thing you do at the gym, between sets, and
sending it through the whole edit screen would be disproportionate.

One editor for the whole screen rather than a flag per row: only one field can
hold focus, so a set of open editors would be a state the UI cannot represent.

Committing an out-of-range or unparseable value is rejected with `WeightInvalid`
and the field stays open, marked, until the text changes.

## Three shapes of write

Three writers touch an exercise and **none has read what the others wrote**:

| Writer | Owns |
| --- | --- |
| `updateDetails` | everything the edit form holds — name, comment, weekday, counts, weight |
| `updateWeight` | the weight only, for the inline editor on the list row |
| `updateCompletedSets` | the completion pair only, for the checkboxes |

Each works from a copy read at a different moment, so a whole-row update from any
of them would push its stale version of the others' columns — a rename un-ticking
the day, a tick reverting a weight the user had just typed. SQLite reports both as
success.

`:feature:tasks` splits `editTask` from `rescheduleTask` and `:feature:routines`
splits `updateDetails` from `updateCompletedOn` for exactly this reason.

## The edit form

Reached by long press **or** the edit icon — two affordances, one intent. Adding
from a page creates the exercise on **that page's weekday**.

The form observes its row rather than reading it once, seeds itself from the first
emission only, and uses the subscription to notice the row being deleted
underneath it. A form that never loaded counts as empty, so the screen cannot save
blank fields over a row it failed to read.

## Note on the tab icon

`material-icons-core` has no dumbbell, and it is the only icon set on the
classpath, so the tab icon is a **vendored `ImageVector`**.
