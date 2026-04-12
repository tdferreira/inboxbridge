# Browser Extension Store Releases

InboxBridge now ships browser-extension release automation through
[.github/workflows/release.yml](/Users/tdferreira/Developer/inboxbridge/.github/workflows/release.yml).

The release workflow always:

- builds and packages the Chromium extension as a `.zip`
- builds and packages the Firefox extension as an `.xpi`
- uploads those artifacts to the GitHub release alongside the backend and admin UI bundle

When the corresponding GitHub secrets are configured, the same workflow also
submits extension updates to:

- Chrome Web Store
- Microsoft Edge Add-ons
- Firefox AMO

Store submission is intentionally optional. If a store is not configured yet,
the workflow still succeeds and simply reports that the submission step was
skipped.

## What you must do first

Yes, each store needs an initial manual setup before GitHub Actions can submit
updates automatically.

### Chrome Web Store

You must do the first listing setup manually:

1. Create a Chrome Web Store developer account.
2. Create the `InboxBridge` item in the Chrome Web Store dashboard.
3. Fill in the listing metadata manually at least once:
   - title
   - descriptions
   - screenshots
   - icons
   - privacy/data-use answers
   - support links
4. Record the extension item ID and the publisher ID for the listing.
5. Create OAuth credentials that can call the Chrome Web Store API.

After that, GitHub Actions can upload and publish new packages.

GitHub secrets required:

- `CHROME_WEBSTORE_EXTENSION_ID`
- `CHROME_WEBSTORE_PUBLISHER_ID`
- `CHROME_WEBSTORE_CLIENT_ID`
- `CHROME_WEBSTORE_CLIENT_SECRET`
- `CHROME_WEBSTORE_REFRESH_TOKEN`

### Microsoft Edge Add-ons

You must do the first listing setup manually:

1. Create a Microsoft Partner Center developer account with Edge Add-ons access.
2. Create the `InboxBridge` add-on listing manually.
3. Fill in the listing metadata manually at least once:
   - descriptions
   - screenshots
   - categories
   - privacy/data-use disclosures
   - support links
4. Record the Edge product ID.
5. Create an API client and API key for the Edge Add-ons submission API.

After that, GitHub Actions can upload a new draft package and submit it for
publication.

GitHub secrets required:

- `EDGE_ADDONS_PRODUCT_ID`
- `EDGE_ADDONS_CLIENT_ID`
- `EDGE_ADDONS_API_KEY`

### Firefox AMO

You must do the first AMO setup manually:

1. Create an AMO developer account.
2. Decide whether the add-on is:
   - listed on AMO
   - or unlisted/self-distributed but still Mozilla-signed
3. Create the initial add-on entry in AMO.
4. Keep the Firefox add-on ID stable. InboxBridge already uses:
   - `inboxbridge@extensions.inboxbridge.dev`
5. Create AMO API credentials.

After that, GitHub Actions can submit new Firefox versions automatically
through `web-ext sign`.

GitHub secrets required:

- `FIREFOX_AMO_API_KEY`
- `FIREFOX_AMO_API_SECRET`

## How the workflow behaves

On a release tag like `v0.5.0`, or on manual dispatch with a tag name, the
release workflow:

1. Builds backend, admin UI, Chromium extension, and Firefox extension.
2. Uploads the packaged artifacts to the GitHub release.
3. If Chrome secrets are present, uploads and publishes the Chromium package to Chrome Web Store.
4. If Edge secrets are present, uploads the Chromium package to Edge Add-ons and submits the draft.
5. If Firefox secrets are present, submits the Firefox package to AMO with `web-ext sign`.
6. Writes a short store-submission summary to the GitHub Actions job summary.

## Current package mapping

- Chrome Web Store submission uses the packaged Chromium `.zip`.
- Edge Add-ons submission uses the same packaged Chromium `.zip`.
- Firefox AMO submission signs and submits the Firefox build from `browser-extensions/firefox/dist/firefox`.

## What still remains manual

Even with automation enabled, these parts are still store-managed/manual:

- the first store listing creation
- screenshots and marketing copy
- privacy questionnaire / data-use declarations
- support and homepage links
- any review or moderation steps required by the store
- Safari release setup, which is a separate Apple packaging and distribution flow

## Recommended rollout

Recommended enablement order:

1. GitHub Releases only
2. Firefox AMO automation
3. Edge Add-ons automation
4. Chrome Web Store automation
5. Safari packaging later

That order keeps the easiest signing/submission path first while the Chrome and
Edge listing metadata is still being polished.
