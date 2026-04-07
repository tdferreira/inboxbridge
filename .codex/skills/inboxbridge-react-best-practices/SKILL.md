---
name: inboxbridge-react-best-practices
description: Use when reorganizing or extending React code in InboxBridge. Captures the repo's preferred React structure, state, composition, and styling conventions.
---

# InboxBridge React Best Practices

Use this skill whenever work touches React architecture, component boundaries, hooks, styling layout, or frontend project structure.

## React best practices

These points reflect broadly accepted React guidance and current official React docs.

### Components

- Keep components pure and render-only where possible.
- Prefer composition over configuration-heavy mega-components.
- Keep one conceptual component per file unless the smaller helpers are truly local and not reusable elsewhere.
- Keep component props small and explicit rather than passing broad grab-bag objects when only a few values are needed.
- If a file starts coordinating multiple unrelated UI responsibilities, split it before it grows further.
- Keep visible copy routed through `i18n` keys instead of raw strings in authenticated UI.

### Hooks and State

- Keep state as close as practical to the component or feature that owns it.
- Lift state only when multiple siblings truly need the same source of truth.
- Keep state structured minimally and avoid duplicated sources of truth.
- Prefer deriving UI during render over Effects when no external synchronization is needed.
- Treat Effects as an escape hatch for syncing with external systems, not as the default way to compute UI state.
- Use custom hooks when stateful logic is reused or when separating orchestration from rendering makes the component easier to scan.
- Keep hooks focused. A hook that manages auth should not also manage unrelated layout or dashboard concerns.
- Use `useMemo` or other memoization only when it materially improves stability or avoids meaningful repeated work; do not add it by reflex.

### Styling

- Colocate styles with the component, feature, or shared primitive that owns them.
- Keep styling ownership obvious so another engineer can tell where to edit behavior or appearance without hunting across the tree.
- Prefer small, intentional style surfaces over one large global stylesheet.

### Testing

- Keep focused Vitest files near the components or hooks they cover.
- Prefer several small regression tests over one giant app-level test when behavior is feature-local.
- Test user-visible behavior and state transitions rather than implementation trivia.
- When reorganizing folders, keep or improve existing coverage instead of dropping tests during moves.

## InboxBridge conventions

These points are repo-specific structure and workflow conventions for this project.

### Components

- Keep `src/app` for application bootstrap concerns only rather than feature logic:
  - `App.jsx` and `RemoteApp.jsx`
  - app-shell composition and route entry logic
  - app-level providers or bootstrap wiring when needed
  - avoid placing feature-specific UI or business workflows there
- Keep product behavior in `src/features/<feature>/...`:
  - feature-local components
  - feature-local hooks
  - feature tests next to the feature components they cover
  - feature styles next to the feature that owns them
- Keep `src/shared/components` for reusable primitives and utility cards used by more than one feature.
- When a feature grows a small cluster of helpers, keep pure serialization/chart/data helpers beside that feature rather than bouncing them back into unrelated folders.
- Shared dashboard sections should compose through the existing shared section shells instead of introducing parallel copies of `SectionCard` or `CollapsibleSection`.

### Hooks and State

- Keep `src/shared/hooks` for reusable browser/workflow hooks shared across app shells or features.
- Keep cross-feature pure utilities and API/formatting/runtime helpers in `src/lib`.
- Use the `@/` alias for cross-feature imports instead of deep `../../..` chains.

### Styling

- Keep theme setup in `src/theme`.
- `src/theme/global.css` should stay small; if a rule only belongs to one feature or primitive, colocate it instead.
- Avoid vague folders like `next`, `misc`, `helpers/components`, or duplicate component homes that split one concern across multiple trees.

### Testing

- When reorganizing folders, keep or improve existing coverage instead of dropping tests during moves.

## Practical review checklist

Before finishing a React change, verify:

- Does the change follow React guidance on purity, minimal state, effect usage, and reusable hooks?
- Is each file clearly owned by `app`, a single `feature`, `shared`, `lib`, or `theme`?
- Is the import path using `@/` when it crosses feature/shared/lib boundaries?
- Did any component become too large to scan comfortably in one pass?
- Are styles colocated with the feature or shared primitive they belong to?
- Is there exactly one real implementation for each shared UI primitive?
- Does visible copy go through translations?
- Are tests still colocated and meaningful for the moved/refactored code?
