# Gmail OAuth setup

## Goal

Create OAuth credentials that let InboxBridge import mail into your Gmail account and optionally manage labels.

## High-level steps

1. Create a Google Cloud project
2. Enable the Gmail API
3. Configure the OAuth consent screen
4. Create an OAuth client ID
5. Add your redirect URI
6. Use the helper endpoints in this app to obtain a refresh token

## Suggested scopes

This starter expects:

- `https://www.googleapis.com/auth/gmail.insert`
- `https://www.googleapis.com/auth/gmail.labels`

## Redirect URI

Default local redirect URI:

```text
http://localhost:8080/api/google-oauth/callback
```

If you deploy elsewhere, update:

- your Google OAuth client redirect URI list
- `GOOGLE_REDIRECT_URI`

## Getting a refresh token

1. Start InboxBridge
2. Open `GET /api/google-oauth/url`
3. Login and consent
4. Copy the code from the callback response
5. Call `POST /api/google-oauth/exchange`
6. Save the returned `refresh_token` into `.env`

## Important note

Google usually only returns a refresh token reliably when the authorization request includes:

- `access_type=offline`
- `prompt=consent`

This starter already includes those in the authorization URL builder.
