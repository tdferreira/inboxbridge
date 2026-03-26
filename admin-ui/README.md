# Admin UI

The admin UI is a separate React + Vite application served by Nginx in its own container. It talks to the Quarkus backend through proxied REST endpoints under `/api/...`.

## Structure

```text
admin-ui/src
├── App.jsx
├── components
│   ├── account
│   ├── admin
│   ├── auth
│   ├── bridges
│   ├── common
│   ├── gmail
│   └── layout
├── lib
└── test
```

Key design choices:

- `App.jsx` owns data fetching, session orchestration, and submit handlers.
- Reusable presentational components live under `src/components/...`.
- `src/components/layout/SetupGuidePanel.jsx` gives users a first-run checklist inside the app itself.
- the setup guide entries are clickable links that focus the corresponding working section
- the setup guide uses neutral / green / red state styling to reflect pending, complete, and error conditions
- the setup guide auto-collapses once every tracked step is complete
- users can opt into per-account persisted collapse state for the major admin-ui sections
- password changes are exposed from the hero/header controls instead of being buried inside Gmail setup
- Each component imports its own CSS file for local structure and appearance.
- Shared visual tokens and generic form/layout helpers live in `src/styles.css`.
- Formatting and API helpers live in `src/lib/...`.

## Tests

Frontend unit tests use `Vitest` and `@testing-library/react`.

Run them with:

```bash
cd admin-ui
npm test -- --run
```

Current unit coverage focuses on:

- formatter utilities
- reusable error banners with copy-to-clipboard actions
- reusable loading-button behavior
- authentication screen interactions
- Gmail destination guidance and shared-client behavior
- first-run setup guide rendering
- reusable bridge card actions

The Google and Microsoft OAuth callback pages include a direct return path back to the admin UI after in-browser token exchange.
They also include:

- a one-click code copy button
- a confirmation dialog if the user tries to leave before exchanging the code
- a 10-second auto-return countdown after a successful in-browser exchange
- guidance that leaving early means the code or token must be handled manually later

API-facing error surfaces in the admin UI now include one-click clipboard actions so users can copy diagnostic payloads without manually selecting text.

Buttons that trigger backend calls now show inline loading spinners so the user can see when authentication, saves, bridge actions, polling, refresh, or OAuth start requests are in progress.
