# Firefox Packaging Notes

These notes keep the Firefox target close to later signing/submission work even
before any AMO release.

## Current scope

- Firefox manual-install target
- Manifest V3 with Firefox background scripts
- narrow permissions: `storage`, `alarms`
- optional host permissions requested at runtime for the configured InboxBridge origin
- packaged `.xpi` artifact for review/signing preparation

## Assets still needed before AMO submission

- final PNG icon set
- screenshots
- polished privacy/data-use statement
- public short and long descriptions
- initial manual AMO listing or unlisted add-on registration
- AMO API credentials and repository secrets described in [docs/BROWSER_EXTENSION_STORES.md](/Users/tdferreira/Developer/inboxbridge/docs/BROWSER_EXTENSION_STORES.md)

## Packaging commands

```bash
cd browser-extensions/firefox
npm run build:firefox
npm run zip:firefox
```
