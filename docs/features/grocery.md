# Grocery list — business logic

`:feature:grocery`. A single flat shopping list: add an item, tick it off, it is
gone.

Status: living document — it describes the app as it is, not a proposal. The
previous, pure-Android implementation of the same feature is described in
[`../specs/2026-09-08-legacy-todo-grocery-business-logic.md`](../specs/2026-09-08-legacy-todo-grocery-business-logic.md);
where the two disagree, this file wins and the difference is called out below.

## Domain model

A grocery item is an id, a name and an optional description. **No dates, no
scheduling, no recurrence** — deliberately much simpler than a task, and it should
stay that way.

`GroceryItemDraft.create(name, description)` is the only way to build a valid one:

- `Name` (the single `GroceryField`) — required, non-blank, saved trimmed.
- The description is trimmed, and an empty one is stored as null rather than `""`.

**Legacy difference.** The legacy app validated nothing here and would happily
save an item with an empty name (legacy open decision 3). A name is now required,
matching tasks.

## The list

One screen, one flat list, no grouping and no sort control — items come back in
storage order (`ORDER BY id`, declared explicitly rather than relied on).

The list is observed, not queried on entry: a write re-emits to the screen by
itself.

## Adding and editing

Both go through **one inline editor** in the state, targeting either a new item or
an existing one. Adding expands a row with name and description fields under the
"+" control; editing expands the same row in place over the item it belongs to.

Modelling add and edit as two forms meant every change to either had to be made
twice, so there is one `GroceryEditor` with a `New | Existing(id)` target.

Editing is reachable from **a visible button as well as a long press**. The legacy
app had a long-press-only "edit mode" that was never wired to any editing UI at
all — a no-op round-trip write (legacy open decision 4). It is now a real editor,
and it is discoverable.

If the row is deleted elsewhere while the editor has it open, the save fails with
`ItemNoLongerExists` rather than silently re-adding it.

## Completing an item

Tapping an item's complete control **permanently deletes it**. There is no
history, no "bought" state and no confirmation dialog — a tap per item is the
whole point of a shopping list, and a dialog forty times a trip would be
intolerable.

The other half of that bargain is **Undo**, offered in the snackbar that confirms
the completion. Undo re-adds the item with its name and description; it is a new
row, so it does not recover the old id.

**The undo offer lasts exactly as long as the snackbar carrying it.** Dismissing
the message drops the undoable item with it, so there is never an Undo button with
nothing behind it.

Each in-flight completion disables only its own row. A screen-wide flag made a
second tap land while the first write was still open — the guard dropped it
silently, and the row's control was disabled for a write that had nothing to do
with it. Two taps in a row is how the list is meant to be used.

## What this feature deliberately does not have

- No cleanup job and nothing time-based at all: items live until the user
  completes them.
- No quantities, categories, or per-shop lists.
- No one-shot events. Everything the screen shows is in its state, and its effect
  type is uninhabited so the compiler enforces that.
