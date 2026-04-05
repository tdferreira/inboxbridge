import { buildEnvConfig, generateEncryptionKey, generateEnvText } from './config-generator.mjs'

const form = document.querySelector('[data-env-generator]')
const output = document.querySelector('[data-env-output]')
const copyButton = document.querySelector('[data-copy-env]')
const summary = document.querySelector('[data-env-summary]')
const generateKeyButton = document.querySelector('[data-generate-key]')

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
    ? `Ready for ${config.sourceProtocol} source ${config.sourceId} at ${config.publicBaseUrl}`
    : `Ready for a deployment at ${config.publicBaseUrl}`
}

async function copyEnv() {
  try {
    await navigator.clipboard.writeText(output.value)
    copyButton.textContent = 'Copied'
    window.setTimeout(() => {
      copyButton.textContent = 'Copy'
    }, 1500)
  } catch {
    copyButton.textContent = 'Copy manually'
  }
}

function generateKeyInBrowser() {
  if (!window.crypto?.getRandomValues) {
    summary.textContent = 'This browser cannot generate a secure key here. Generate one locally with openssl rand -base64 32.'
    return
  }
  const bytes = new Uint8Array(32)
  window.crypto.getRandomValues(bytes)
  form.encryptionKey.value = generateEncryptionKey(bytes)
  render()
}

if (form && output && copyButton && summary) {
  syncConditionalFields(form)
  try {
    render()
  } catch (error) {
    output.value = 'The browser-side generator is temporarily unavailable.\n'
    summary.textContent = 'The generator could not be initialized in this browser.'
  }

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
