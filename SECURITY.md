# Security Policy

## Supported Scope

InboxBridge handles credentials, OAuth configuration, mailbox access, and imported-message flows. Security reports are welcome, especially for issues involving:

- authentication and session handling
- passkeys / WebAuthn
- OAuth flows and token storage
- source or destination mailbox isolation
- privilege escalation
- sensitive-data exposure
- TLS or CSRF protections

## Reporting a Vulnerability

Please do not open public GitHub issues for suspected security vulnerabilities.

Instead:

1. Use GitHub private vulnerability reporting or a private security advisory if it is enabled for this repository.
2. If that is not available, contact the repository owner privately through GitHub.
3. Include clear reproduction steps, affected versions or commits, impact, and any suggested mitigations.

Please avoid publicly disclosing an issue until the maintainer has had a reasonable chance to investigate and respond.

## What To Include

A good report includes:

- a short summary of the issue
- the affected area of the project
- reproduction steps
- expected impact
- any environment assumptions
- logs, screenshots, or proof-of-concept details when helpful

## Response Expectations

This is a self-hosted open-source project maintained on a best-effort basis. Response times may vary, but good-faith reports are appreciated and taken seriously.

## Operational Responsibility

Even with secure defaults and careful review, InboxBridge is provided on an `AS IS` basis. Operators are responsible for testing, deployment hardening, network exposure, backup strategy, credential handling, and safe use in their own environments.
