# Gym plan — business logic

`:feature:gym`. A weekly training plan: seven pages, one per weekday, each holding
the exercises planned for it. Today's page can be ticked off.

Status: living document. This feature has no predecessor in the legacy app; it is
new to the KMP version.

## Domain model

An exercise is an id, a name, an optional comment, a set of weekdays, a series
count, reps per series, an optional weight, and the completion pair.

**An exercise is planned on a set of `DayOfWeek`s, not on dates.** The seven pages
repeat every week; there is no row per date and no history table.

- The set is **never empty** — an exercise on no day would be on no page, and so
  out of reach of Edit and Delete alike. Taking it out of the plan is Delete.
- It is **one exercise however many days it is on**. Squats on Monday and
  Thursday are one row with one weight, so a weight changed on either page is the
  weight both show. That is the whole reason the days are a set rather than a
  copy of the exercise per day.

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
- `Days` — at least one weekday.
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

`doneSets` is `completedSets` when `completedOn` is today **and the page is
today's weekday**, and zero otherwise — recomputed on every write to the tables
*and* at every local midnight.

The weekday half is what an exercise on several days needs. Its single completion
pair is shared by every page it appears on, and one pair is enough: a date falls on
exactly one weekday, so a tick made today belongs to today's page and no other.
Without the check, ticking Monday's squats would also show Thursday's squats as
done, whenever Thursday's page is looked at on a Monday. On Thursday itself the
date no longer matches and the card starts from zero, as a new day should.

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

For the same reason a tick is planned from **today's** card of the exercise, not
from whichever of its cards comes first in the week: every other card reports zero
done, and planning from one would count up from nothing. An exercise not planned
today has no today's card, and so nothing to tick.

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
hold focus, so a set of open editors would be a state the UI cannot represent. The
editor records the **page** it was opened on as well as the exercise — an
exercise on several days has a card on each of their pages, and the pager composes
a neighbour while it is swiped to, so an editor keyed by the exercise alone opened
on every one of its cards at once.

The weight is the exercise's, not the page's: committing it on Monday's card
changes it on every day the exercise is planned.

Committing an out-of-range or unparseable value is rejected with `WeightInvalid`
and the field stays open, marked, until the text changes.

## Three shapes of write

Three writers touch an exercise and **none has read what the others wrote**:

| Writer | Owns |
| --- | --- |
| `updateDetails` | everything the edit form holds — name, comment, weekdays, counts, weight |
| `updateWeight` | the weight only, for the inline editor on the list row |
| `updateCompletedSets` | the completion pair only, for the checkboxes |

Each works from a copy read at a different moment, so a whole-row update from any
of them would push its stale version of the others' columns — a rename un-ticking
the day, a tick reverting a weight the user had just typed. SQLite reports both as
success.

`:feature:tasks` splits `editTask` from `rescheduleTask` and `:feature:routines`
splits `updateDetails` from `updateCompletedOn` for exactly this reason.

## Storage

The weekdays live in their own table, `gymExerciseDay` — one row per (exercise,
weekday), the pair as its key — and the exercise row holds everything else. Both
queries the screens observe are one join over the two tables, so SQLDelight re-runs
them when either changes, and no observer can pair an exercise with a stale set of
days. Every write that touches the days runs in one transaction with the exercise
row: there is no foreign key to cascade a delete (neither driver enables them), and
an exercise with no day rows would vanish from the join.

Version 1 stored one `dayOfWeek` column on the exercise. Migration `1.sqm` gives
every existing exercise exactly that day as its only row in `gymExerciseDay`, and
keeps its id, its weight and today's ticks.

## The edit form

Reached by long press **or** the edit icon — two affordances, one intent. Adding
from a page starts the exercise on **that page's weekday**, and opens with the
name field focused and the keyboard up; editing an existing exercise opens with the
keyboard down, since it is as often opened to change its days or its sets.

The weekdays are seven toggles: tapping a day adds it or takes it off. Saving with
none selected marks the field rather than writing.

The form observes its row rather than reading it once, seeds itself from the first
emission only, and uses the subscription to notice the row being deleted
underneath it. A form that never loaded counts as empty, so the screen cannot save
blank fields over a row it failed to read.

## Note on the tab icon

`material-icons-core` has no dumbbell, and it is the only icon set on the
classpath, so the tab icon is a **vendored `ImageVector`**.
