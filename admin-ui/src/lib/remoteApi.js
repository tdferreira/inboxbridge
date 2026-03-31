import { apiErrorText } from './api'

export class RemoteUnauthorizedError extends Error {
  constructor(message = 'Remote session expired') {
    super(message)
    this.name = 'RemoteUnauthorizedError'
  }
}

function csrfToken() {
  if (typeof document === 'undefined') return ''
  return document.cookie
    .split(';')
    .map((chunk) => chunk.trim())
    .find((chunk) => chunk.startsWith('inboxbridge_remote_csrf='))
    ?.slice('inboxbridge_remote_csrf='.length) || ''
}

async function request(url, options = {}) {
  const method = options.method || 'GET'
  const headers = {
    ...(options.headers || {})
  }
  if (method !== 'GET' && method !== 'HEAD') {
    headers['X-InboxBridge-CSRF'] = csrfToken()
  }
  const response = await fetch(url, {
    credentials: 'include',
    ...options,
    headers
  })
  if (response.status === 401) {
    throw new RemoteUnauthorizedError(await apiErrorText(response, 'Remote session expired'))
  }
  if (!response.ok) {
    throw new Error(await apiErrorText(response, `Remote request failed (${response.status})`))
  }
  if (response.status === 204) return null
  return response.json()
}

export function remoteLogin(payload) {
  return request('/api/remote/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
}

export function remoteStartPasskey(payload) {
  return request('/api/remote/auth/passkey/options', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
}

export function remoteFinishPasskey(payload) {
  return request('/api/remote/auth/passkey/verify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
}

export function remoteSession() {
  return request('/api/remote/auth/me')
}

export function remoteLogout() {
  return request('/api/remote/auth/logout', { method: 'POST' })
}

export function remoteControl() {
  return request('/api/remote/control')
}

export function remoteRunUserPoll() {
  return request('/api/remote/poll/run', { method: 'POST' })
}

export function remoteRunAllUsersPoll() {
  return request('/api/remote/poll/all-users/run', { method: 'POST' })
}

export function remoteRunSourcePoll(sourceId) {
  return request(`/api/remote/sources/${encodeURIComponent(sourceId)}/poll/run`, { method: 'POST' })
}
