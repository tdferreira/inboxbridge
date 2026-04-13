import { createInvalidExtensionAuthError, isInvalidExtensionAuthError } from './auth-errors.js'
import { subscribeToJsonSse } from './fetch-sse.js'

const DEFAULT_TIMEOUT_MS = 10_000

export async function fetchStatus(serverUrl, token) {
  return fetchJson(`${serverUrl}/api/extension/status`, { token })
}

export function subscribeExtensionEvents(serverUrl, token, { onDisconnect, onEvent, subscribeToJsonSseImpl = subscribeToJsonSse } = {}) {
  return subscribeToJsonSseImpl({
    onDisconnect,
    onEvent,
    token,
    url: `${serverUrl}/api/extension/events`
  })
}

export async function runPoll(serverUrl, token) {
  return fetchJson(`${serverUrl}/api/extension/poll`, {
    method: 'POST',
    token
  })
}

export async function loginExtension(serverUrl, request) {
  return fetchJson(`${serverUrl}/api/extension/auth/login`, {
    method: 'POST',
    json: request
  })
}

export async function startExtensionBrowserAuth(serverUrl, request) {
  return fetchJson(`${serverUrl}/api/extension/auth/browser-handoff/start`, {
    method: 'POST',
    json: request
  })
}

export async function redeemExtensionBrowserAuth(serverUrl, request) {
  return fetchJson(`${serverUrl}/api/extension/auth/browser-handoff/redeem`, {
    method: 'POST',
    json: request
  })
}

export async function verifyExtensionPasskey(serverUrl, request) {
  return fetchJson(`${serverUrl}/api/extension/auth/passkey/verify`, {
    method: 'POST',
    json: request
  })
}

export async function refreshExtensionAuth(serverUrl, refreshToken) {
  return fetchJson(`${serverUrl}/api/extension/auth/refresh`, {
    method: 'POST',
    json: { refreshToken }
  })
}

async function fetchJson(url, options = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS)
  const headers = new Headers(options.headers || {})
  if (options.token) {
    headers.set('Authorization', `Bearer ${options.token}`)
  }
  if (options.json !== undefined) {
    headers.set('Content-Type', 'application/json')
  }
  try {
    const response = await fetch(url, {
      method: options.method || 'GET',
      headers,
      body: options.json === undefined ? undefined : JSON.stringify(options.json),
      signal: controller.signal
    })

    const payload = await parsePayload(response)
    if (response.status === 401) {
      throw createInvalidExtensionAuthError(payload?.message || 'The saved InboxBridge sign-in is no longer valid.')
    }

    if (!response.ok) {
      throw new Error(payload?.message || payload?.error || payload?.detail || `InboxBridge returned ${response.status}.`)
    }

    return payload
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('InboxBridge did not respond in time.')
    }
    if (isNetworkFetchError(error)) {
      throw new Error(buildNetworkErrorMessage(url))
    }
    throw error
  } finally {
    clearTimeout(timer)
  }
}

function isNetworkFetchError(error) {
  return error instanceof TypeError || /failed to fetch/i.test(String(error?.message || ''))
}

function buildNetworkErrorMessage(url) {
  try {
    const origin = new URL(url).origin
    return `InboxBridge could not be reached at ${origin}. Check the saved URL, confirm the server is running, and make sure this browser trusts the site's HTTPS certificate.`
  } catch {
    return 'InboxBridge could not be reached. Check the saved URL, confirm the server is running, and make sure this browser trusts the site HTTPS certificate.'
  }
}

async function parsePayload(response) {
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    return response.json()
  }
  const text = await response.text()
  if (!text) {
    return null
  }
  try {
    return JSON.parse(text)
  } catch {
    return { message: text }
  }
}
