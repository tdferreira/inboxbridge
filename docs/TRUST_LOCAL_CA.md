# Trust the local CA

InboxBridge's Docker Compose setup generates a local certificate authority and
uses it to sign the frontend/backend HTTPS certificates.

The CA certificate is:

- [`certs/ca.crt`](../certs/ca.crt)

Import and trust that CA on every device/browser that will open InboxBridge.
Trusting only the site certificate is not enough when the chain itself is still
unknown to the device.

## Why this matters

If the browser still reports a TLS/certificate problem, secure-context features
may stay blocked even when the hostname is present in the certificate SAN list.
That can affect:

- passkeys / WebAuthn
- some geolocation and secure-context browser APIs
- PWA installability

## macOS

Use `Keychain Access`, not the new Passwords app.

1. Open `Keychain Access`.
2. Import [`certs/ca.crt`](../certs/ca.crt) into the `System` keychain for
   system-wide trust, or `login` for user-only trust.
3. Open the imported certificate.
4. Expand `Trust`.
5. Set `When using this certificate` to `Always Trust`.
6. Close the dialog and authenticate if macOS asks.
7. Restart the browser.

## Windows

1. Open `mmc`.
2. Add the `Certificates` snap-in for `Computer account`.
3. Go to `Trusted Root Certification Authorities -> Certificates`.
4. Import [`certs/ca.crt`](../certs/ca.crt).
5. Restart the browser.

## Linux

Typical distro commands:

Debian / Ubuntu:

```bash
sudo cp certs/ca.crt /usr/local/share/ca-certificates/inboxbridge-local-ca.crt
sudo update-ca-certificates
```

Fedora / CentOS / RHEL:

```bash
sudo cp certs/ca.crt /etc/pki/ca-trust/source/anchors/inboxbridge-local-ca.crt
sudo update-ca-trust
```

Restart the browser after updating the trust store.

## Firefox

Firefox may use its own trust store.

Options:

1. `Settings -> Certificates -> View Certificates`
2. `Authorities -> Import`
3. Import [`certs/ca.crt`](../certs/ca.crt)
4. Trust it for identifying websites

Or enable OS trust reuse through `about:config`:

- set `security.enterprise_roots.enabled=true`

## iPhone / iPad

1. Transfer [`certs/ca.crt`](../certs/ca.crt) to the device.
2. Open it and install the profile.
3. Go to `Settings -> General -> About -> Certificate Trust Settings`.
4. Enable full trust for that root certificate.

## Android

The exact path varies by vendor/version, but usually:

1. Copy [`certs/ca.crt`](../certs/ca.crt) to the device.
2. Open `Settings -> Security` or `Encryption & credentials`.
3. Choose `Install a certificate`.
4. Install it as a `CA certificate`.

## Hostname note

Certificate trust is only one requirement. The browser must also reach
InboxBridge through a hostname that matches:

- the generated certificate SAN entries
- `PUBLIC_BASE_URL`
- `SECURITY_PASSKEY_RP_ID`
- `SECURITY_PASSKEY_ORIGINS` for passkeys/WebAuthn

If you use passkeys, prefer one canonical hostname across LAN/Tailscale access
instead of mixing unrelated domains such as `.local` and `.ts.net`.
