import test from 'node:test'
import assert from 'node:assert/strict'

import {
  fetchStatus,
  loginExtension,
  refreshExtensionAuth,
  runPoll,
  subscribeExtensionEvents
} from '../../shared/src/api.js'

function jsonResponse(payload, status = 200) {
  return {
    headers: new Headers({ 'content-type': 'application/json' }),
    ok: status >= 200 && status < 300,
    status,
    async json() {
      return payload
    },
    async text() {
      return JSON.stringify(payload)
    }
  }
}

test('fetchStatus includes bearer token auth and returns parsed payload', async () => {
  let request
  global.fetch = async (url, options) => {
    request = { url, options }
    return jsonResponse({ summary: { errorSourceCount: 0 } })
  }

  const payload = await fetchStatus('https://mail.example.com', 'token-1')

  assert.equal(request.url, 'https://mail.example.com/api/extension/status')
  assert.equal(request.options.headers.get('Authorization'), 'Bearer token-1')
  assert.equal(payload.summary.errorSourceCount, 0)
})

test('loginExtension posts JSON and returns the parsed auth payload', async () => {
  let request
  global.fetch = async (url, options) => {
    request = { url, options }
    return jsonResponse({ status: 'AUTHENTICATED' })
  }

  const payload = await loginExtension('https://mail.example.com', { username: 'alice', password: 'secret' })

  assert.equal(request.url, 'https://mail.example.com/api/extension/auth/login')
  assert.equal(request.options.method, 'POST')
  assert.equal(request.options.headers.get('Content-Type'), 'application/json')
  assert.equal(payload.status, 'AUTHENTICATED')
})

test('refreshExtensionAuth posts the refresh token JSON payload', async () => {
  let request
  global.fetch = async (url, options) => {
    request = { url, options }
    return jsonResponse({ status: 'AUTHENTICATED' })
  }

  await refreshExtensionAuth('https://mail.example.com', 'refresh-1')

  assert.equal(request.url, 'https://mail.example.com/api/extension/auth/refresh')
  assert.equal(request.options.body, JSON.stringify({ refreshToken: 'refresh-1' }))
})

test('runPoll surfaces unauthorized failures clearly', async () => {
  global.fetch = async () => jsonResponse({ message: 'The saved InboxBridge sign-in is no longer valid.' }, 401)

  await assert.rejects(
    () => runPoll('https://mail.example.com', 'token-1'),
    /no longer valid/
  )
})

test('fetchStatus surfaces network fetch failures with an InboxBridge-specific transport hint', async () => {
  global.fetch = async () => {
    throw new TypeError('Failed to fetch')
  }

  await assert.rejects(
    () => fetchStatus('https://mail.example.com', 'token-1'),
    /InboxBridge could not be reached at https:\/\/mail\.example\.com/
  )
})

test('subscribeExtensionEvents points the shared SSE helper at the extension stream endpoint', () => {
  let streamRequest = null

  const subscription = subscribeExtensionEvents('https://mail.example.com', 'token-1', {
    onDisconnect() {},
    onEvent() {},
    subscribeToJsonSseImpl(request) {
      streamRequest = request
      return { close() {}, completed: Promise.resolve() }
    }
  })

  assert.equal(streamRequest.url, 'https://mail.example.com/api/extension/events')
  assert.equal(streamRequest.token, 'token-1')
  assert.equal(typeof subscription.close, 'function')
})
