import assert from 'node:assert/strict'
import fs from 'node:fs'
import { buildEnvConfig, generateEncryptionKey, generateEnvText } from './config-generator.mjs'

const config = buildEnvConfig({
  publicBaseUrl: 'https://inboxbridge.example.com',
  encryptionKey: 'base64-demo-key',
  multiUserEnabled: false,
  sourceId: 'outlook-main',
  sourceProtocol: 'IMAP',
  sourceFetchMode: 'IDLE',
  sourceHost: 'outlook.office365.com',
  sourcePort: '993',
  sourceAuthMethod: 'OAUTH2',
  sourceOauthProvider: 'MICROSOFT',
  sourceUsername: 'owner@example.com',
  sourceOauthRefreshToken: 'refresh-token',
  sourceUnreadOnly: true
})

assert.equal(config.hostname, 'inboxbridge.example.com')
assert.equal(config.sourceFetchMode, 'IDLE')
assert.equal(config.multiUserEnabled, false)

const envText = generateEnvText(config)
assert.match(envText, /PUBLIC_BASE_URL=https:\/\/inboxbridge\.example\.com/)
assert.match(envText, /SECURITY_PASSKEY_RP_ID=inboxbridge\.example\.com/)
assert.match(envText, /MULTI_USER_ENABLED=false/)
assert.match(envText, /MAIL_ACCOUNT_0__ID=outlook-main/)
assert.match(envText, /MAIL_ACCOUNT_0__AUTH_METHOD=OAUTH2/)
assert.match(envText, /MAIL_ACCOUNT_0__OAUTH_PROVIDER=MICROSOFT/)
assert.match(envText, /MAIL_ACCOUNT_0__FETCH_MODE=IDLE/)
assert.doesNotMatch(envText, /MAIL_ACCOUNT_0__PASSWORD=/)

const popEnv = generateEnvText({
  publicBaseUrl: 'https://localhost:3000',
  sourceId: 'legacy-pop',
  sourceProtocol: 'POP3',
  sourceHost: 'pop.example.com',
  sourcePort: '995',
  sourceAuthMethod: 'PASSWORD',
  sourceUsername: 'legacy@example.com',
  sourcePassword: 'app-pass'
})

assert.match(popEnv, /MAIL_ACCOUNT_0__PROTOCOL=POP3/)
assert.match(popEnv, /MAIL_ACCOUNT_0__PASSWORD=app-pass/)
assert.doesNotMatch(popEnv, /MAIL_ACCOUNT_0__FETCH_MODE=/)
assert.doesNotMatch(popEnv, /MAIL_ACCOUNT_0__FOLDER=/)

const generatedKey = generateEncryptionKey(Uint8Array.from({ length: 32 }, (_, index) => index))
assert.equal(Buffer.from(generatedKey, 'base64').length, 32)

const siteHtml = fs.readFileSync(new URL('./index.html', import.meta.url), 'utf8')
assert.match(siteHtml, /id="architecture"/)
assert.match(siteHtml, /class="architecture-diagram"/)
assert.match(siteHtml, /class="flow-pulse flow-pulse-one"/)
assert.match(siteHtml, /id="faq"/)
assert.match(siteHtml, /Is IMAP IDLE real-time\?/)
assert.match(siteHtml, /remote-icon\.svg/)
assert.match(siteHtml, /Loading generator…/)
assert.match(siteHtml, /Preparing your starter configuration…/)
assert.match(siteHtml, /class="info-hint"/)
assert.match(siteHtml, /Public base URL help/)
assert.match(siteHtml, /Fetch mode help/)

console.log('site config generator ok')
