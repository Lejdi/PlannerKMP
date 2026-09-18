# Feature business logic

One document per feature module, describing **what the app does and why** — the
domain model, the rules, and the behaviour a user can observe. These are the
source of truth for product behaviour; `CLAUDE.md` in the repo root is the source
of truth for how to *build* a feature, and deliberately describes none of this.

| Document | Module | What it covers |
| --- | --- | --- |
| [tasks.md](tasks.md) | `:feature:tasks` | Tasks and TODOs: the three schedule kinds, the 8-day dashboard, completion per occurrence, the daily catch-up |
| [grocery.md](grocery.md) | `:feature:grocery` | The shopping list: one flat list, complete-as-delete, the undo bargain |
| [routines.md](routines.md) | `:feature:routines` | Daily habits: the derived midnight reset, the done-today footer |
| [gym.md](gym.md) | `:feature:gym` | The weekly plan: exercises per weekday, series counts, ticking today only |

A rule stated here carries the failure it prevents wherever there was one. That is
what stops the next change quietly undoing it.

## Also here

- [`../architecture-blueprint.md`](../architecture-blueprint.md) — the prescriptive
  build order for a new KMP + Compose app, distilled from this one. Structure, not
  product.
- [`../specs/2026-09-08-legacy-todo-grocery-business-logic.md`](../specs/2026-09-08-legacy-todo-grocery-business-logic.md)
  — the previous, pure-Android implementation of tasks and grocery, transcribed
  from its use cases and Cucumber features. A historical reference: where it and
  `tasks.md` / `grocery.md` disagree, those files describe what ships.

## Keeping these current

A change to what the app *does* belongs in the matching file here, in the same
commit. A change to how a module is wired belongs in `CLAUDE.md`. If a rule needs
stating in both, state it once here and point at it.
