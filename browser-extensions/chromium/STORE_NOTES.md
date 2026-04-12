# Store Notes

These notes exist so the Chromium MVP stays close to future signing/submission shape even before any public store release.

## Current scope

- Chromium manual-install only
- narrow permissions: `storage`, `alarms`
- optional host permissions requested at runtime for the configured InboxBridge origin
- no remote code loading

## Assets still needed before any store submission

- final PNG icon set
- screenshots
- polished privacy/data-use statement
- public short and long descriptions
- initial manual Chrome Web Store listing
- initial manual Microsoft Edge Add-ons listing
- API credentials and repository secrets described in [docs/BROWSER_EXTENSION_STORES.md](/Users/tdferreira/Developer/inboxbridge/docs/BROWSER_EXTENSION_STORES.md)

## Packaging commands

```bash
cd browser-extensions/chromium
npm run build:chrome
npm run zip:chrome
```
