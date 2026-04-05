# InboxBridge

[![Build](https://github.com/tdferreira/inboxbridge/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/tdferreira/inboxbridge/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-2ea44f)](LICENSE)
[![Release](https://img.shields.io/badge/release-see%20tags-1f6feb)](https://github.com/tdferreira/inboxbridge/tags)
![Self-hosted](https://img.shields.io/badge/deployment-self--hosted-2ea44f)
![Stack](https://img.shields.io/badge/stack-Quarkus%20%2B%20React%20%2B%20PostgreSQL-1f6feb)
![AI-assisted](https://img.shields.io/badge/built%20with-AI--assisted-8A2BE2)

InboxBridge is a self-hosted mail importer. It connects to one or more source mailboxes, reads their messages, and imports those messages into one destination mailbox so you can keep a "one inbox, many accounts" workflow without relying on email forwarding.

It is designed for people who want to keep mail from several accounts together in one place while still controlling where credentials, tokens, and imported-message history are stored.

## What InboxBridge Does

InboxBridge can:

- read mail from IMAP and POP3 source accounts
- import mail into Gmail through the Gmail API
- append mail into IMAP destination mailboxes such as Outlook and other providers
- manage source accounts either from `.env` or through the web interface
- store UI-managed secrets and OAuth tokens encrypted in PostgreSQL
- support multi-user or single-user deployments
- provide a browser-based admin UI plus a lightweight `/remote` control page for phones and quick access
- show live polling progress with pause, resume, stop, retry, and queue reprioritization controls
- let IMAP sources opt into real-time IMAP IDLE watching with durable UID checkpoints and scheduler fallback if a watcher stays unhealthy

## Who It Is For

InboxBridge is a good fit if you:

- want a self-hosted alternative to mailbox forwarding rules
- want to import mail from older or secondary accounts into one main mailbox
- need one deployment that can serve one person or several users
- are comfortable running Docker-based applications, even if you are not a developer

## Supported Mailbox Flows

Source mailboxes:

- IMAP with password/app password
- POP3 with password/app password
- Microsoft OAuth2 for Outlook / Hotmail / Live source accounts
- Google OAuth for supported Google-based source flows

Destination mailboxes:

- Gmail through the Gmail API
- Outlook.com / Hotmail / Live through IMAP APPEND with Microsoft OAuth2
- Yahoo Mail through IMAP APPEND
- Proton Mail Bridge through IMAP APPEND
- Generic IMAP APPEND destinations

## How It Works

InboxBridge includes:

- a Quarkus backend
- a React admin UI
- PostgreSQL for durable storage
- Docker Compose services for the backend, frontend, database, and local TLS certificate bootstrap

By default, the local Docker setup serves the app over HTTPS with generated self-signed certificates.

## Main Features

- Unified source account list in the UI, including read-only `.env` accounts and user-managed accounts
- Per-user destination mailbox configuration
- Import deduplication in PostgreSQL
- WebAuthn passkey support for sign-in
- User sessions with browser/device hints, Geo-IP, and optional browser-reported device location
- Per-user notification history that survives refreshes and sign-in cycles
- Optional post-poll actions for IMAP sources such as mark as read, mark as forwarded, delete, or move to folder
- Per-source IMAP fetch mode: scheduled polling or real-time IMAP IDLE
- Live polling over authenticated SSE with bounded parallel workers
- A mobile-friendly `/remote` page for quick poll control without opening the full workspace

## What It Does Not Aim To Be

InboxBridge currently does not:

- encrypt secrets that you place directly in `.env`
- support every provider-specific OAuth flow
- keep POP UIDL checkpoints or full multi-folder mailbox cursor state
- include production-grade metrics, circuit breakers, or external secret-vault integration

## Quick Start

### 1. Prepare the environment

Copy the example configuration file:

```bash
cp .env.example .env
```

The example file has the minimum local bootstrap values uncommented, so
for a first run you usually only need to generate and fill the encryption key.

Generate an encryption key:

```bash
openssl rand -base64 32
```

Put that generated value into:

```dotenv
SECURITY_TOKEN_ENCRYPTION_KEY=<base64-32-byte-key>
```

For a normal first run, these minimum values are already present in
`.env.example`:

```dotenv
JDBC_URL=jdbc:postgresql://postgres:5432/inboxbridge
JDBC_USERNAME=inboxbridge
JDBC_PASSWORD=inboxbridge
PUBLIC_BASE_URL=https://localhost:3000
SECURITY_TOKEN_ENCRYPTION_KEY=<base64-32-byte-key>
SECURITY_TOKEN_ENCRYPTION_KEY_ID=v1
SECURITY_PASSKEY_RP_ID=localhost
SECURITY_PASSKEY_ORIGINS=https://localhost:3000
```

### 2. Start the stack

```bash
docker compose up --build
```

Services:

- Admin UI: `https://localhost:3000`
- Remote control page: `https://localhost:3000/remote`
- Backend HTTP: `http://localhost:8080`
- Backend HTTPS: `https://localhost:8443`
- PostgreSQL: `localhost:5432`

The first startup generates certificates in `./certs`.

### 3. Sign in

Bootstrap credentials:

- username: `admin`
- password: `nimda`

After signing in:

1. Change the bootstrap password.
2. Open `My Destination Mailbox` and connect the mailbox that should receive imported mail.
3. Open `My Source Email Accounts` and add at least one source mailbox.
4. Run a poll and confirm the messages arrive in the destination mailbox.

## Configuration Overview

### Minimum configuration to explore the app

You do not need Google or Microsoft OAuth credentials just to start the stack,
sign in, and explore the UI.

With only the minimum bootstrap values, you can:

- sign in to the admin UI
- change the bootstrap password
- create users in multi-user mode
- configure destination and source mailboxes in the browser
- review sessions and security settings
- use passkeys if your browser and hostname setup support them

### Minimum configuration to import mail

To actually import messages, you need:

- the minimum bootstrap setup above
- one configured destination mailbox
- at least one configured source mailbox
- OAuth app credentials for any OAuth-based provider flow you choose to use

For example, if you want Gmail as the destination provider, you must first
create a Google Cloud project and OAuth client for InboxBridge.

### Important `.env` values

These are the settings most operators care about first:

- `PUBLIC_BASE_URL`: public HTTPS URL used for browser links and default OAuth callbacks
- `MULTI_USER_ENABLED`: choose single-user or multi-user mode
- `SECURITY_TOKEN_ENCRYPTION_KEY`: required for encrypted UI-managed secrets and browser OAuth exchange
- `SECURITY_TOKEN_ENCRYPTION_KEY_ID`: key label stored with encrypted data
- `SECURITY_PASSKEY_*`: passkey/WebAuthn settings
- `MAIL_ACCOUNT_<n>__...`: optional env-managed source mailbox definitions
- `GOOGLE_*`: shared Google OAuth app settings for Gmail destination flows
- `MICROSOFT_*`: shared Microsoft OAuth app settings for Outlook flows
- `TLS_FRONTEND_CERT_HOSTNAMES` and `TLS_BACKEND_CERT_HOSTNAMES`: extra hostnames to include in generated local certificates

## OAuth And Provider Setup

Use these docs when you are ready to connect real providers:

- [Setup Guide](docs/SETUP.md)
- [OAuth Setup](docs/OAUTH_SETUP.md)
- [Trust the local CA](docs/TRUST_LOCAL_CA.md)
- [Architecture Notes](docs/ARCHITECTURE.md)

Quick callback defaults:

- Google: `https://localhost:3000/api/google-oauth/callback`
- Microsoft: `https://localhost:3000/api/microsoft-oauth/callback`

If you deploy on another hostname, set `PUBLIC_BASE_URL` and use a certificate that matches that host.

## Remote Control

InboxBridge includes a smaller mobile-friendly interface at `/remote`.

It is useful when you want to:

- trigger a poll from a phone
- check live poll progress quickly
- pause, resume, stop, retry, or reprioritize a live run
- expose a simpler control surface behind your normal HTTPS access controls

The remote page uses its own scoped session model instead of reusing the main admin UI session.

## Security Notes

- UI-managed OAuth tokens and mailbox secrets are intended to be stored encrypted in PostgreSQL.
- Browser-based Google and Microsoft OAuth exchange requires `SECURITY_TOKEN_ENCRYPTION_KEY`.
- `.env` values are operator-managed configuration and are not encrypted by InboxBridge.
- Passkeys work best on one stable hostname rather than a mix of unrelated hostnames or raw IP addresses.

If a Gmail account is unlinked locally but Google-side revocation fails, the remaining Google grant can be removed manually from `myaccount.google.com` under `Security -> Manage third-party access -> InboxBridge`.

## Local TLS Notes

The Docker Compose setup creates a local certificate authority and signs the frontend/backend certificates with it.

If your browser still shows a certificate warning, trust [`certs/ca.crt`](certs/ca.crt) on the device that will open InboxBridge. This matters for:

- passkeys / WebAuthn
- secure browser APIs such as geolocation
- PWA installation for `/remote`

If you want LAN or Tailscale access, prefer a real hostname covered by the certificate instead of a raw IP address.

## License And Disclaimer

InboxBridge is licensed under the Apache License 2.0. See [LICENSE](LICENSE).

This project is provided on an `AS IS` basis, without warranties or conditions of any kind, express or implied, and is used at your own risk.

You are responsible for reviewing, testing, securing, and operating InboxBridge in your own environment, including how it handles credentials, imported mail, OAuth configuration, TLS, backups, and user access.

InboxBridge is not a managed service, legal/compliance product, or security guarantee.

## AI Disclosure

This project was built with the help of AI-assisted tooling.

AI assistance was used to help design, implement, refactor, test, and document parts of the project, with human review and direction by the repository owner. Users and contributors should still review the code, configuration, and behavior carefully before relying on it in any environment.

## Technology Stack

- Java 25
- Quarkus 3.33.1
- React 19
- Vite 8
- PostgreSQL 16
- Flyway
- Docker Compose

## Project Documentation

- Main setup: [docs/SETUP.md](docs/SETUP.md)
- OAuth details: [docs/OAUTH_SETUP.md](docs/OAUTH_SETUP.md)
- Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Local certificate trust: [docs/TRUST_LOCAL_CA.md](docs/TRUST_LOCAL_CA.md)
- Admin UI notes: [admin-ui/README.md](admin-ui/README.md)

## Community

- Contributing guide: [CONTRIBUTING.md](CONTRIBUTING.md)
- Security policy: [SECURITY.md](SECURITY.md)
- Code of conduct: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)

## Maintenance Automation

- Dependabot is configured in [.github/dependabot.yml](.github/dependabot.yml) for GitHub Actions, Maven, npm, and Docker base-image updates.
- GitHub Releases can be created automatically from tags matching `v*` through [.github/workflows/release.yml](.github/workflows/release.yml).
- The release workflow can also be started manually from the GitHub Actions UI by providing a tag name such as `v0.2.0`.

## Current Version

The repository currently declares version `0.2.0` in [`pom.xml`](pom.xml) and `0.2.0` in [`admin-ui/package.json`](admin-ui/package.json).
