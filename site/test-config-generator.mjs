import assert from 'node:assert/strict'
import fs from 'node:fs'
import { buildEnvConfig, generateEncryptionKey, generateEnvText } from './config-generator.mjs'
import { languageOptions, normalizeLocale, translate, translationCatalog } from './i18n.mjs'

const configuredInput = {
  publicHostname: 'inboxbridge.example.com',
  publicPort: '9443',
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
}

const config = buildEnvConfig(configuredInput)

assert.equal(config.hostname, 'inboxbridge.example.com')
assert.equal(config.publicHostname, 'inboxbridge.example.com')
assert.equal(config.publicPort, '9443')
assert.equal(config.publicBaseUrl, 'https://inboxbridge.example.com:9443')
assert.equal(config.sourceFetchMode, 'IDLE')
assert.equal(config.multiUserEnabled, false)

const envText = generateEnvText(configuredInput)
assert.match(envText, /PUBLIC_HOSTNAME=inboxbridge\.example\.com/)
assert.match(envText, /PUBLIC_PORT=9443/)
assert.doesNotMatch(envText, /PUBLIC_BASE_URL=/)
assert.match(envText, /SECURITY_PASSKEY_RP_ID=inboxbridge\.example\.com/)
assert.match(envText, /MULTI_USER_ENABLED=false/)
assert.match(envText, /MAIL_ACCOUNT_0__ID=outlook-main/)
assert.match(envText, /MAIL_ACCOUNT_0__AUTH_METHOD=OAUTH2/)
assert.match(envText, /MAIL_ACCOUNT_0__OAUTH_PROVIDER=MICROSOFT/)
assert.match(envText, /MAIL_ACCOUNT_0__FETCH_MODE=IDLE/)
assert.doesNotMatch(envText, /MAIL_ACCOUNT_0__PASSWORD=/)

const popEnv = generateEnvText({
  publicHostname: 'localhost',
  publicPort: '3000',
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

const explicitBaseUrlConfig = buildEnvConfig({
  publicBaseUrl: 'https://demo.example.com:7443'
})

assert.equal(explicitBaseUrlConfig.publicHostname, 'demo.example.com')
assert.equal(explicitBaseUrlConfig.publicPort, '7443')
assert.equal(explicitBaseUrlConfig.publicBaseUrl, 'https://demo.example.com:7443')

const explicitBaseUrlEnv = generateEnvText({
  publicBaseUrl: 'https://demo.example.com:7443'
})

assert.match(explicitBaseUrlEnv, /PUBLIC_BASE_URL=https:\/\/demo\.example\.com:7443/)
assert.match(explicitBaseUrlEnv, /SECURITY_PASSKEY_ORIGINS=https:\/\/demo\.example\.com:7443/)

const generatedKey = generateEncryptionKey(Uint8Array.from({ length: 32 }, (_, index) => index))
assert.equal(Buffer.from(generatedKey, 'base64').length, 32)

assert.deepEqual(languageOptions, ['en', 'fr', 'de', 'pt-PT', 'pt-BR', 'es'])
assert.equal(normalizeLocale('pt'), 'pt-PT')
assert.equal(normalizeLocale('pt-BR'), 'pt-BR')
assert.equal(translate('pt-PT', 'nav.features'), 'Funcionalidades')
assert.equal(translate('fr', 'env.copy'), 'Copier')
assert.match(translate('en', 'arch.detail.copy'), /operational metadata/)
assert.match(translate('en', 'band.remember.three'), /encrypted mailbox and API connections/)
assert.equal(
  Object.keys(translationCatalog.fr).length,
  Object.keys(translationCatalog.en).length
)
assert.equal(
  Object.keys(translationCatalog.de).length,
  Object.keys(translationCatalog.en).length
)
assert.equal(
  Object.keys(translationCatalog['pt-PT']).length,
  Object.keys(translationCatalog.en).length
)
assert.equal(
  Object.keys(translationCatalog['pt-BR']).length,
  Object.keys(translationCatalog.en).length
)
assert.equal(
  Object.keys(translationCatalog.es).length,
  Object.keys(translationCatalog.en).length
)
assert.notEqual(translate('fr', 'hero.body'), translate('en', 'hero.body'))
assert.notEqual(translate('de', 'arch.detail.copy'), translate('en', 'arch.detail.copy'))
assert.notEqual(translate('pt-PT', 'faq.security.a'), translate('en', 'faq.security.a'))
assert.notEqual(translate('pt-BR', 'env.body'), translate('en', 'env.body'))
assert.notEqual(translate('es', 'band.remember.three'), translate('en', 'band.remember.three'))

const siteHtml = fs.readFileSync(new URL('./index.html', import.meta.url), 'utf8')
assert.match(siteHtml, /data-language-picker/)
assert.match(siteHtml, /language-menu-picker language-menu-picker-fallback/)
assert.match(siteHtml, /id="architecture"/)
assert.match(siteHtml, /class="architecture-diagram"/)
assert.match(siteHtml, /class="architecture-detail-toggle"/)
assert.match(siteHtml, /See here a lower-level runtime view/)
assert.match(siteHtml, /A deeper view that shows which pieces handle encrypted transport, runtime coordination, and durable state\./)
assert.match(siteHtml, /class="architecture-detail-diagram"/)
assert.match(siteHtml, /class="architecture-detail-flow architecture-detail-flow-ui"/)
assert.match(siteHtml, /class="architecture-detail-flow architecture-detail-flow-providers"/)
assert.match(siteHtml, /class="architecture-detail-flow architecture-detail-flow-db"/)
assert.match(siteHtml, /IMAPS \/ POP3S \/ HTTPS/)
assert.match(siteHtml, /Encrypted secrets \+ metadata/)
assert.match(siteHtml, /Security highlights/)
assert.match(siteHtml, /Why the self-hosted runtime model matters/)
assert.match(siteHtml, /class="flow-pulse flow-pulse-one"/)
assert.match(siteHtml, /moving mail icons represent messages progressing/)
assert.match(siteHtml, /processes message content only long enough to import it/)
assert.match(siteHtml, /encrypted UI-managed secrets, IMAP checkpoints, dedupe identifiers/)
assert.match(siteHtml, /Raspberry Pi, virtual private server, or dedicated host/)
assert.match(siteHtml, /id="faq"/)
assert.match(siteHtml, /Why is the self-hosted model important for security\?/)
assert.match(siteHtml, /mailbox consolidation without handing credentials to a third-party\s+service/)
assert.match(siteHtml, /Are IMAP and POP3 secure enough for this\?/)
assert.match(siteHtml, /Why import mail instead of using forwarding rules\?/)
assert.match(siteHtml, /Can I still reply from another address\?/)
assert.match(siteHtml, /What providers does InboxBridge work with\?/)
assert.match(siteHtml, /Is IMAP IDLE real-time\?/)
assert.match(siteHtml, /InboxBridge can try to add/)
assert.match(siteHtml, /\$Forwarded/)
assert.match(siteHtml, /mailbox-side hint|handled source message/)
assert.match(siteHtml, /best-effort IMAP keyword/)
assert.match(siteHtml, /remote-icon\.svg/)
assert.match(siteHtml, /Loading generator…/)
assert.match(siteHtml, /Preparing your starter configuration…/)
assert.match(siteHtml, /starting with the minimum `.env` needed to boot InboxBridge/)
assert.match(siteHtml, /avoid\s+leaving mailbox passwords in plain text inside `.env`/)
assert.match(siteHtml, /inside the application web interface/)
assert.match(siteHtml, /encrypted mailbox and API connections/)
assert.match(siteHtml, /shadow archive of message content/)
assert.match(siteHtml, /class="dual-input-row"/)
assert.match(siteHtml, /class="dual-input-row dual-input-row-key"/)
assert.match(siteHtml, /class="dual-input-row dual-input-row-imap"/)
assert.match(siteHtml, /class="input-with-action"/)
assert.match(siteHtml, /class="field-icon-button"/)
assert.match(siteHtml, /class="optional-block field full"/)
assert.match(siteHtml, /Optional shared OAuth apps/)
assert.match(siteHtml, /Optional env-managed source mailbox/)
assert.match(siteHtml, /id="encryptionKey"/)
assert.match(siteHtml, /id="generateKeyButton"/)
assert.match(siteHtml, /id="jdbcUsername"/)
assert.match(siteHtml, /id="jdbcPassword"/)
assert.match(siteHtml, /id="jdbcUrl"/)
assert.match(siteHtml, /id="publicHostname"/)
assert.match(siteHtml, /id="publicPort"/)
assert.match(siteHtml, /id="publicBaseUrl"/)
assert.match(siteHtml, /readonly/)
assert.match(siteHtml, /id="pollInterval"/)
assert.match(siteHtml, /id="fetchWindow"/)
assert.match(siteHtml, /id="microsoftClientId"/)
assert.match(siteHtml, /id="microsoftClientSecret"/)
assert.match(siteHtml, /data-oauth-only hidden/)
assert.match(siteHtml, /id="sourceOauthProvider"/)
assert.match(siteHtml, /id="sourceUsername"/)
assert.match(siteHtml, /id="sourcePassword"/)
assert.match(siteHtml, /id="sourceFolder"/)
assert.match(siteHtml, /id="sourceFetchMode"/)
assert.match(siteHtml, /data-password-only/)
assert.match(siteHtml, /syncStaticConditionalFields/)
assert.match(siteHtml, /class="info-hint"/)
assert.match(siteHtml, /Public hostname help/)
assert.match(siteHtml, /Public port help/)
assert.match(siteHtml, /Computed public base URL help/)
assert.match(siteHtml, /Fetch mode help/)

console.log('site config generator ok')
