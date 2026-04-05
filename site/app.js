import { buildEnvConfig, generateEncryptionKey, generateEnvText } from './config-generator.mjs'
import { languageFlag, languageOptions, normalizeLocale, translate } from './i18n.mjs'

const form = document.querySelector('[data-env-generator]')
const output = document.querySelector('[data-env-output]')
const copyButton = document.querySelector('[data-copy-env]')
const summary = document.querySelector('[data-env-summary]')
const generateKeyButton = document.querySelector('[data-generate-key]')
const languagePicker = document.querySelector('[data-language-picker]')
const descriptionMeta = document.querySelector('meta[name="description"]')

let language = normalizeLocale(window.localStorage.getItem('inboxbridge.language') || navigator.language)

function formState(formElement) {
  return {
    publicBaseUrl: formElement.publicBaseUrl.value,
    jdbcUrl: formElement.jdbcUrl.value,
    jdbcUsername: formElement.jdbcUsername.value,
    jdbcPassword: formElement.jdbcPassword.value,
    encryptionKey: formElement.encryptionKey.value,
    encryptionKeyId: formElement.encryptionKeyId.value,
    multiUserEnabled: formElement.multiUserEnabled.checked,
    pollInterval: formElement.pollInterval.value,
    fetchWindow: formElement.fetchWindow.value,
    googleClientId: formElement.googleClientId.value,
    googleClientSecret: formElement.googleClientSecret.value,
    microsoftTenant: formElement.microsoftTenant.value,
    microsoftClientId: formElement.microsoftClientId.value,
    microsoftClientSecret: formElement.microsoftClientSecret.value,
    sourceId: formElement.sourceId.value,
    sourceHost: formElement.sourceHost.value,
    sourcePort: formElement.sourcePort.value,
    sourceProtocol: formElement.sourceProtocol.value,
    sourceAuthMethod: formElement.sourceAuthMethod.value,
    sourceOauthProvider: formElement.sourceOauthProvider.value,
    sourceUsername: formElement.sourceUsername.value,
    sourcePassword: formElement.sourcePassword.value,
    sourceOauthRefreshToken: formElement.sourceOauthRefreshToken.value,
    sourceFolder: formElement.sourceFolder.value,
    sourceUnreadOnly: formElement.sourceUnreadOnly.checked,
    sourceFetchMode: formElement.sourceFetchMode.value,
    sourceCustomLabel: formElement.sourceCustomLabel.value
  }
}

function setText(selector, key, params) {
  const element = document.querySelector(selector)
  if (element) {
    element.textContent = translate(language, key, params)
  }
}

function setManyText(bindings) {
  bindings.forEach(([selector, key]) => setText(selector, key))
}

function setFieldText(fieldId, labelKey, hintKey, placeholderKey = null) {
  const labelCopy = document.querySelector(`label[for="${fieldId}"] .label-copy`)
  if (labelCopy) {
    labelCopy.textContent = translate(language, labelKey)
  }
  const hint = document.querySelector(`label[for="${fieldId}"] .info-hint`)
  if (hint) {
    const label = translate(language, labelKey)
    hint.setAttribute('aria-label', `${label} ${translate(language, 'language.label').toLowerCase() === 'language' ? 'help' : ''}`.trim())
    hint.dataset.hint = translate(language, hintKey)
  }
  const input = document.getElementById(fieldId)
  if (input && placeholderKey) {
    input.placeholder = translate(language, placeholderKey)
  }
}

function setCheckboxText(fieldId, labelKey, hintKey) {
  const checkbox = document.querySelector(`label[for="${fieldId}"] span`)
  if (checkbox) {
    checkbox.textContent = translate(language, labelKey)
  }
  const hint = document.querySelector(`label[for="${fieldId}"] .info-hint`)
  if (hint) {
    hint.setAttribute('aria-label', `${translate(language, labelKey)} help`)
    hint.dataset.hint = translate(language, hintKey)
  }
}

function applyTranslations() {
  document.documentElement.lang = language
  document.title = translate(language, 'page.title')
  if (descriptionMeta) {
    descriptionMeta.setAttribute('content', translate(language, 'page.description'))
  }

  setText('.brand-copy span', 'site.tagline')
  setManyText([
    ['.nav a[href="#features"]', 'nav.features'],
    ['.nav a[href="#architecture"]', 'nav.architecture'],
    ['.nav a[href="#how-it-works"]', 'nav.how'],
    ['.nav a[href="#faq"]', 'nav.faq'],
    ['.nav a[href="#env-generator"]', 'nav.env'],
    ['.nav a[href="https://github.com/tdferreira/inboxbridge"]', 'nav.github'],
    ['.hero .eyebrow', 'hero.eyebrow'],
    ['.hero h1', 'hero.title'],
    ['.hero-actions .button.primary', 'hero.repo'],
    ['.hero-actions .button.secondary', 'hero.env'],
    ['.hero-card-grid article:nth-of-type(1) .mini-kicker', 'hero.sources.kicker'],
    ['.hero-card-grid article:nth-of-type(1) h2', 'hero.sources.title'],
    ['.hero-card-grid article:nth-of-type(1) p', 'hero.sources.body'],
    ['.hero-card-grid article:nth-of-type(2) .mini-kicker', 'hero.dest.kicker'],
    ['.hero-card-grid article:nth-of-type(2) h2', 'hero.dest.title'],
    ['.hero-card-grid article:nth-of-type(2) p', 'hero.dest.body'],
    ['.hero-card-grid article:nth-of-type(3) .mini-kicker', 'hero.realtime.kicker'],
    ['.hero-card-grid article:nth-of-type(3) h2', 'hero.realtime.title'],
    ['.hero-card-grid article:nth-of-type(3) p', 'hero.realtime.body'],
    ['.hero-card-grid article:nth-of-type(4) .mini-kicker', 'hero.control.kicker'],
    ['.hero-card-grid article:nth-of-type(4) h2', 'hero.control.title'],
    ['.hero-card-grid article:nth-of-type(4) p', 'hero.control.body'],
    ['#features .section-header .eyebrow', 'features.eyebrow'],
    ['#features .section-header h2', 'features.title'],
    ['#features .section-header p', 'features.body'],
    ['#features .feature-card:nth-of-type(1) .mini-kicker', 'features.raw.kicker'],
    ['#features .feature-card:nth-of-type(1) h3', 'features.raw.title'],
    ['#features .feature-card:nth-of-type(1) p', 'features.raw.body'],
    ['#features .feature-card:nth-of-type(2) .mini-kicker', 'features.source.kicker'],
    ['#features .feature-card:nth-of-type(2) h3', 'features.source.title'],
    ['#features .feature-card:nth-of-type(2) p', 'features.source.body'],
    ['#features .feature-card:nth-of-type(3) .mini-kicker', 'features.operator.kicker'],
    ['#features .feature-card:nth-of-type(3) h3', 'features.operator.title'],
    ['#features .feature-card:nth-of-type(3) p', 'features.operator.body'],
    ['#architecture .section-header .eyebrow', 'arch.eyebrow'],
    ['#architecture .section-header h2', 'arch.title'],
    ['#architecture .section-header p', 'arch.body'],
    ['.arch-card-sources .mini-kicker', 'arch.sources.kicker'],
    ['.arch-card-sources h3', 'arch.sources.title'],
    ['.arch-card-sources p', 'arch.sources.body'],
    ['.arch-card-core .mini-kicker', 'arch.core.kicker'],
    ['.arch-card-core h3', 'arch.core.title'],
    ['.arch-card-core p', 'arch.core.body'],
    ['.arch-card-destination .mini-kicker', 'arch.dest.kicker'],
    ['.arch-card-destination h3', 'arch.dest.title'],
    ['.arch-card-destination p', 'arch.dest.body'],
    ['.arch-flow:not(.arch-flow-final) .flow-node:first-child', 'arch.flow.detect'],
    ['.arch-flow:not(.arch-flow-final) .flow-node:last-child', 'arch.flow.import'],
    ['.arch-flow-final .flow-node:first-child', 'arch.flow.route'],
    ['.arch-flow-final .flow-node:last-child', 'arch.flow.append'],
    ['.architecture-caption .pill', 'arch.caption.badge'],
    ['.architecture-caption p', 'arch.caption.body'],
    ['.architecture-detail-toggle summary strong', 'arch.detail.title'],
    ['.architecture-detail-summary-copy', 'arch.detail.summary'],
    ['.architecture-detail-copy', 'arch.detail.copy'],
    ['.architecture-detail-card-surface .mini-kicker', 'arch.detail.surface.kicker'],
    ['.architecture-detail-card-surface h3', 'arch.detail.surface.title'],
    ['.architecture-detail-card-surface p', 'arch.detail.surface.body'],
    ['.architecture-detail-card-backend .mini-kicker', 'arch.detail.backend.kicker'],
    ['.architecture-detail-card-backend h3', 'arch.detail.backend.title'],
    ['.architecture-detail-card-backend p', 'arch.detail.backend.body'],
    ['.architecture-detail-card-storage .mini-kicker', 'arch.detail.storage.kicker'],
    ['.architecture-detail-card-storage h3', 'arch.detail.storage.title'],
    ['.architecture-detail-card-storage p', 'arch.detail.storage.body'],
    ['.architecture-detail-card-providers .mini-kicker', 'arch.detail.providers.kicker'],
    ['.architecture-detail-card-providers h3', 'arch.detail.providers.title'],
    ['.architecture-detail-card-providers p', 'arch.detail.providers.body'],
    ['.architecture-detail-flow-ui .architecture-detail-arrow', 'arch.detail.flow.ui'],
    ['.architecture-detail-flow-providers .architecture-detail-arrow', 'arch.detail.flow.providers'],
    ['.architecture-detail-flow-db .architecture-detail-arrow', 'arch.detail.flow.db'],
    ['.architecture-detail-highlights .eyebrow', 'arch.detail.highlights.kicker'],
    ['.architecture-detail-highlights h3', 'arch.detail.highlights.title'],
    ['.architecture-detail-highlights p', 'arch.detail.highlights.body'],
    ['.architecture-detail-card-transport .mini-kicker', 'arch.detail.transport.kicker'],
    ['.architecture-detail-card-transport h3', 'arch.detail.transport.title'],
    ['.architecture-detail-card-transport p', 'arch.detail.transport.body'],
    ['.architecture-detail-card-data .mini-kicker', 'arch.detail.data.kicker'],
    ['.architecture-detail-card-data h3', 'arch.detail.data.title'],
    ['.architecture-detail-card-data p', 'arch.detail.data.body'],
    ['.architecture-detail-card-deploy .mini-kicker', 'arch.detail.deploy.kicker'],
    ['.architecture-detail-card-deploy h3', 'arch.detail.deploy.title'],
    ['.architecture-detail-card-deploy p', 'arch.detail.deploy.body'],
    ['.architecture-detail-card-trust .mini-kicker', 'arch.detail.trust.kicker'],
    ['.architecture-detail-card-trust h3', 'arch.detail.trust.title'],
    ['.architecture-detail-card-trust p', 'arch.detail.trust.body'],
    ['#how-it-works .section-header .eyebrow', 'how.eyebrow'],
    ['#how-it-works .section-header h2', 'how.title'],
    ['#how-it-works .section-header p', 'how.body'],
    ['#how-it-works .how-card:nth-of-type(1) h3', 'how.one.title'],
    ['#how-it-works .how-card:nth-of-type(1) p', 'how.one.body'],
    ['#how-it-works .how-card:nth-of-type(2) h3', 'how.two.title'],
    ['#how-it-works .how-card:nth-of-type(2) p', 'how.two.body'],
    ['#how-it-works .how-card:nth-of-type(3) h3', 'how.three.title'],
    ['#how-it-works .how-card:nth-of-type(3) p', 'how.three.body'],
    ['#faq .section-header .eyebrow', 'faq.eyebrow'],
    ['#faq .section-header h2', 'faq.title'],
    ['#faq .section-header p', 'faq.body'],
    ['#faq .faq-card:nth-of-type(1) summary', 'faq.security.q'],
    ['#faq .faq-card:nth-of-type(1) p', 'faq.security.a'],
    ['#faq .faq-card:nth-of-type(2) summary', 'faq.protocol.q'],
    ['#faq .faq-card:nth-of-type(2) p', 'faq.protocol.a'],
    ['#faq .faq-card:nth-of-type(3) summary', 'faq.forwarding.q'],
    ['#faq .faq-card:nth-of-type(3) p', 'faq.forwarding.a'],
    ['#faq .faq-card:nth-of-type(4) summary', 'faq.content.q'],
    ['#faq .faq-card:nth-of-type(4) p', 'faq.content.a'],
    ['#faq .faq-card:nth-of-type(5) summary', 'faq.idle.q'],
    ['#faq .faq-card:nth-of-type(5) p', 'faq.idle.a'],
    ['#faq .faq-card:nth-of-type(6) summary', 'faq.reply.q'],
    ['#faq .faq-card:nth-of-type(6) p', 'faq.reply.a'],
    ['#faq .faq-card:nth-of-type(7) summary', 'faq.providers.q'],
    ['#faq .faq-card:nth-of-type(7) p', 'faq.providers.a'],
    ['#faq .faq-card:nth-of-type(8) summary', 'faq.pop.q'],
    ['#faq .faq-card:nth-of-type(8) p', 'faq.pop.a'],
    ['#faq .faq-card:nth-of-type(9) summary', 'faq.env.q'],
    ['#faq .faq-card:nth-of-type(9) p', 'faq.env.a'],
    ['#faq .faq-card:nth-of-type(10) summary', 'faq.forwarded.q'],
    ['#faq .faq-card:nth-of-type(10) p', 'faq.forwarded.a'],
    ['#faq .faq-card:nth-of-type(11) summary', 'faq.key.q'],
    ['#faq .faq-card:nth-of-type(11) p', 'faq.key.a'],
    ['#env-generator .section-header .eyebrow', 'env.eyebrow'],
    ['#env-generator .section-header h2', 'env.title'],
    ['#env-generator .section-header p', 'env.body'],
    ['.code-panel .eyebrow', 'env.outputEyebrow'],
    ['.code-panel h3', 'env.outputTitle'],
    ['[data-copy-env]', 'env.copy'],
    ['.info-band-grid > div:nth-of-type(1) .eyebrow', 'band.fit'],
    ['.info-band-grid > div:nth-of-type(2) .eyebrow', 'band.remember'],
    ['.site-footer .shell > span:first-child', 'footer.title'],
    ['.site-footer .shell > span:last-child a:nth-of-type(1)', 'footer.repo'],
    ['.site-footer .shell > span:last-child a:nth-of-type(2)', 'footer.readme'],
    ['.site-footer .shell > span:last-child a:nth-of-type(3)', 'footer.setup']
  ])

  const heroBody = document.querySelector('.hero > .shell > div > p')
  if (heroBody) heroBody.textContent = translate(language, 'hero.body')

  const sourceList = document.querySelectorAll('.arch-card-sources .arch-list li')
  const coreList = document.querySelectorAll('.arch-card-core .arch-list li')
  const destList = document.querySelectorAll('.arch-card-destination .arch-list li')
  const fitList = document.querySelectorAll('.info-band-grid > div:nth-of-type(1) li')
  const rememberList = document.querySelectorAll('.info-band-grid > div:nth-of-type(2) li')

  ;[
    [sourceList[0], 'arch.list.imapPolling'],
    [sourceList[1], 'arch.list.imapIdle'],
    [sourceList[2], 'arch.list.popPolling'],
    [coreList[0], 'arch.list.uid'],
    [coreList[1], 'arch.list.history'],
    [coreList[2], 'arch.list.token'],
    [destList[0], 'arch.list.gmail'],
    [destList[1], 'arch.list.append'],
    [destList[2], 'arch.list.labels'],
    [fitList[0], 'band.fit.one'],
    [fitList[1], 'band.fit.two'],
    [fitList[2], 'band.fit.three'],
    [rememberList[0], 'band.remember.one'],
    [rememberList[1], 'band.remember.two'],
    [rememberList[2], 'band.remember.three'],
    [rememberList[3], 'band.remember.four']
  ].forEach(([element, key]) => {
    if (element) {
      element.textContent = translate(language, key)
    }
  })

  setFieldText('publicBaseUrl', 'env.field.publicBaseUrl', 'env.hint.publicBaseUrl')
  setFieldText('jdbcUrl', 'env.field.jdbcUrl', 'env.hint.jdbcUrl')
  setFieldText('jdbcUsername', 'env.field.jdbcUsername', 'env.hint.jdbcUsername')
  setFieldText('jdbcPassword', 'env.field.jdbcPassword', 'env.hint.jdbcPassword')
  setFieldText('encryptionKey', 'env.field.encryptionKey', 'env.hint.encryptionKey', 'env.placeholder.encryptionKey')
  setFieldText('encryptionKeyId', 'env.field.encryptionKeyId', 'env.hint.encryptionKeyId')
  setFieldText('pollInterval', 'env.field.pollInterval', 'env.hint.pollInterval')
  setFieldText('fetchWindow', 'env.field.fetchWindow', 'env.hint.fetchWindow')
  setFieldText('googleClientId', 'env.field.googleClientId', 'env.hint.googleClientId', 'env.placeholder.google')
  setFieldText('googleClientSecret', 'env.field.googleClientSecret', 'env.hint.googleClientSecret', 'env.placeholder.google')
  setFieldText('microsoftTenant', 'env.field.microsoftTenant', 'env.hint.microsoftTenant')
  setFieldText('microsoftClientId', 'env.field.microsoftClientId', 'env.hint.microsoftClientId', 'env.placeholder.google')
  setFieldText('microsoftClientSecret', 'env.field.microsoftClientSecret', 'env.hint.microsoftClientSecret', 'env.placeholder.google')
  setFieldText('sourceId', 'env.field.sourceId', 'env.hint.sourceId', 'env.placeholder.sourceId')
  setFieldText('sourceProtocol', 'env.field.sourceProtocol', 'env.hint.sourceProtocol')
  setFieldText('sourceHost', 'env.field.sourceHost', 'env.hint.sourceHost')
  setFieldText('sourcePort', 'env.field.sourcePort', 'env.hint.sourcePort')
  setFieldText('sourceAuthMethod', 'env.field.sourceAuthMethod', 'env.hint.sourceAuthMethod')
  setFieldText('sourceOauthProvider', 'env.field.sourceOauthProvider', 'env.hint.sourceOauthProvider')
  setFieldText('sourceUsername', 'env.field.sourceUsername', 'env.hint.sourceUsername', 'env.placeholder.sourceUsername')
  setFieldText('sourcePassword', 'env.field.sourcePassword', 'env.hint.sourcePassword', 'env.placeholder.sourcePassword')
  setFieldText('sourceOauthRefreshToken', 'env.field.sourceOauthRefreshToken', 'env.hint.sourceOauthRefreshToken', 'env.placeholder.sourceOauthRefreshToken')
  setFieldText('sourceFolder', 'env.field.sourceFolder', 'env.hint.sourceFolder')
  setFieldText('sourceFetchMode', 'env.field.sourceFetchMode', 'env.hint.sourceFetchMode')
  setFieldText('sourceCustomLabel', 'env.field.sourceCustomLabel', 'env.hint.sourceCustomLabel', 'env.placeholder.sourceCustomLabel')
  setCheckboxText('multiUserEnabled', 'env.field.multiUser', 'env.hint.multiUser')
  setCheckboxText('sourceUnreadOnly', 'env.field.sourceUnreadOnly', 'env.hint.sourceUnreadOnly')

  const oauthSummary = document.querySelector('.optional-block:nth-of-type(1) .subsection-title')
  const sourceSummary = document.querySelector('.optional-block:nth-of-type(2) .subsection-title')
  if (oauthSummary) oauthSummary.textContent = translate(language, 'env.optional.oauth')
  if (sourceSummary) sourceSummary.textContent = translate(language, 'env.optional.source')

  if (generateKeyButton) {
    const generateLabel = translate(language, 'env.action.generateKey')
    generateKeyButton.setAttribute('aria-label', generateLabel)
    generateKeyButton.setAttribute('title', generateLabel)
  }
}

function syncConditionalFields(formElement) {
  const protocol = formElement.sourceProtocol.value
  const authMethod = formElement.sourceAuthMethod.value
  const isImap = protocol === 'IMAP'
  const isOauth = authMethod === 'OAUTH2'

  formElement.querySelectorAll('[data-imap-only]').forEach((element) => {
    element.hidden = !isImap
  })
  formElement.querySelectorAll('[data-password-only]').forEach((element) => {
    element.hidden = isOauth
  })
  formElement.querySelectorAll('[data-oauth-only]').forEach((element) => {
    element.hidden = !isOauth
  })
}

function render() {
  const state = formState(form)
  const config = buildEnvConfig(state)
  output.value = generateEnvText(state)
  summary.textContent = config.sourceId
    ? translate(language, 'env.summary.readySource', { protocol: config.sourceProtocol, sourceId: config.sourceId, publicBaseUrl: config.publicBaseUrl })
    : translate(language, 'env.summary.readyDeployment', { publicBaseUrl: config.publicBaseUrl })
}

async function copyEnv() {
  try {
    await navigator.clipboard.writeText(output.value)
    copyButton.textContent = translate(language, 'env.copied')
    window.setTimeout(() => {
      copyButton.textContent = translate(language, 'env.copy')
    }, 1500)
  } catch {
    copyButton.textContent = translate(language, 'env.copyManual')
  }
}

function generateKeyInBrowser() {
  if (!window.crypto?.getRandomValues) {
    summary.textContent = translate(language, 'env.summary.keyUnavailable')
    return
  }
  const bytes = new Uint8Array(32)
  window.crypto.getRandomValues(bytes)
  form.encryptionKey.value = generateEncryptionKey(bytes)
  render()
}

function renderLanguagePicker() {
  if (!languagePicker) {
    return
  }

  languagePicker.innerHTML = ''

  const menu = document.createElement('div')
  menu.className = 'fetcher-menu language-menu-panel'
  menu.hidden = true
  menu.setAttribute('role', 'menu')

  const button = document.createElement('button')
  button.type = 'button'
  button.className = 'language-menu-button'
  button.setAttribute('aria-haspopup', 'menu')
  button.setAttribute('aria-expanded', 'false')
  button.setAttribute('aria-label', translate(language, 'language.label'))

  function syncButton() {
    button.innerHTML = `
      <span aria-hidden="true" class="language-menu-button-flag">${languageFlag(language)}</span>
      <span class="language-menu-button-text">${translate(language, `language.${language}`)}</span>
      <span aria-hidden="true" class="language-menu-button-caret">▾</span>
    `
    button.setAttribute('aria-label', translate(language, 'language.label'))
  }

  function rebuildMenu() {
    menu.innerHTML = ''
    languageOptions.forEach((value) => {
      const option = document.createElement('button')
      option.type = 'button'
      option.className = value === language ? 'language-menu-option language-menu-option-selected' : 'language-menu-option'
      option.setAttribute('role', 'menuitemradio')
      option.setAttribute('aria-pressed', value === language ? 'true' : 'false')
      option.innerHTML = `
        <span aria-hidden="true" class="language-menu-option-flag">${languageFlag(value)}</span>
        <span class="language-menu-option-label">${translate(language, `language.${value}`)}</span>
      `
      option.addEventListener('click', () => {
        language = value
        window.localStorage.setItem('inboxbridge.language', language)
        syncButton()
        rebuildMenu()
        applyTranslations()
        if (form && output && copyButton && summary) {
          render()
        } else {
          summary.textContent = translate(language, 'env.preparing')
        }
        menu.hidden = true
        button.setAttribute('aria-expanded', 'false')
      })
      menu.append(option)
    })
  }

  syncButton()
  rebuildMenu()

  button.addEventListener('click', () => {
    const nextOpen = menu.hidden
    menu.hidden = !nextOpen
    button.setAttribute('aria-expanded', nextOpen ? 'true' : 'false')
  })

  document.addEventListener('mousedown', (event) => {
    if (languagePicker.contains(event.target)) {
      return
    }
    menu.hidden = true
    button.setAttribute('aria-expanded', 'false')
  })

  languagePicker.append(button, menu)
}

renderLanguagePicker()
applyTranslations()

if (form && output && copyButton && summary) {
  syncConditionalFields(form)
  try {
    render()
  } catch {
    output.value = translate(language, 'env.generatorUnavailableText')
    summary.textContent = translate(language, 'env.summary.generatorUnavailable')
  }

  output.value = output.value === 'Loading generator…' ? translate(language, 'env.loading') : output.value
  summary.textContent = summary.textContent === 'Preparing your starter configuration…'
    ? translate(language, 'env.preparing')
    : summary.textContent

  form.addEventListener('input', () => {
    syncConditionalFields(form)
    render()
  })
  form.addEventListener('change', () => {
    syncConditionalFields(form)
    render()
  })
  copyButton.addEventListener('click', copyEnv)
  generateKeyButton?.addEventListener('click', generateKeyInBrowser)
}
