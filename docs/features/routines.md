# Daily routines — business logic

`:feature:routines`. Habits ticked off for today, and only for today. At local
midnight everything comes back un-ticked.

Status: living document. This feature has no predecessor in the legacy app; it is
new to the KMP version.

## Domain model

A routine is an id, a name, an optional description, and `completedOn` — **the
last date it was ticked**, or null if it never has been.

`completedOn` is deliberately not a boolean. "Done" is a question about *today*,
and today is not a property of the row. `TodayRoutine` is where the question gets
answered: a routine plus `isDoneToday`, computed against a live clock.

`RoutineDraft.create(name, description)` is the only way to build a valid one:

- `Name` (the single `RoutineField`) — required, non-blank, saved trimmed.
- The description is trimmed, and an empty one is stored as null rather than `""`.

## The daily reset is derived, not stored

This is the rule the whole feature turns on. `isDoneToday` means
`completedOn == today` **as evaluated right now**, recomputed on every write to
the table *and* at every local midnight.

The consequences are the point:

- There is **no cleanup job and no history table**. Nothing has to run overnight
  for the screen to be right in the morning.
- The app can be shut for a week without drifting. A stored boolean would need
  something to clear it, and whatever cleared it would have to have run.

The midnight half is not decoration. A SQLDelight query flow re-emits only when
its table is written, so a phone left on this screen overnight would cross
midnight with nothing to trigger a recompute and would go on showing yesterday's
ticks until the user happened to write something.

There is **no way to see another day**. Not a limitation to be lifted casually:
storing only the last completion date is what makes the reset free, and a history
view would require a history to read.

## Ticking a routine

A checkbox per row. Ticking writes today's date; un-ticking writes null. That
convention — the write half — lives in `ToggleRoutineDone`, in the same layer as
the read half that derives `isDoneToday`, so a test can hold the two against each
other. It is not spelled out in a ViewModel, where this app keeps no business
rules at all.

Each in-flight tick disables only its own row. Working down the list is how the
screen is meant to be used, and a screen-wide flag made the second tap land while
the first write was still open.

## A ticked routine leaves the list

The list proper shows **what is left to do today**. A routine that has been ticked
moves into a collapsed **"Done today (N)"** footer.

Collapsed, not dropped: the checkbox is the only way to *un*-tick, so a row that
vanished on a mis-tap could not be recovered until midnight.

The split is presentation grouping over the domain's own answer — the screen
derives its pending and done lists from `isDoneToday`; the domain does not know
about the footer.

**"All done" is a different state from "empty."** Both leave the list bare and
they mean opposite things, so they get different wording — telling somebody who
has just finished five routines that they have none is a plain falsehood.
"Empty" additionally means there is nothing on screen to fall back on after a
failed reload, which a finished list is not.

## Adding and editing

One inline editor targeting either a new routine or an existing one, for the
reason grocery's is one: add and edit are the same form with a different target,
and modelling them twice means every change has to be made twice.

If the row is deleted elsewhere while the editor has it open, the save fails with
`RoutineNoLongerExists`.

## Deleting

Behind a **confirmation dialog**, not grocery's undo-snackbar. Deleting a routine
is rare and deliberate here; completing a grocery item is a tap made forty times a
trip. The affordance should match how often the action is taken and how much it
costs to get wrong.

The pending confirmation is deliberately **not** restored after process death: a
destructive dialog that comes back under a thumb already moving towards where the
confirm button was is worse than one that is simply gone. The expanded state of
the done footer *is* restored — restoring that costs a user nothing if it is
wrong.

## Two shapes of write

`updateDetails` writes the name and description; `updateCompletedOn` writes that
one column. Both writers act on a routine read earlier — the editor on the one it
opened, the checkbox on the one the list drew — so a whole-row update from either
would push its stale copy of the other's columns: a rename un-ticking today, or a
tick reverting a rename, both reported by SQLite as success.

`:feature:tasks` splits `editTask` from `rescheduleTask` for exactly this reason.
