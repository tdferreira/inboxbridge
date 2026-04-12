import test from 'node:test'
import assert from 'node:assert/strict'
import { completeBrowserSignIn, createBrowserAuthPkcePair } from '../../shared/src/browser-auth.js'

test('browser auth pkce pair returns an S256 challenge and verifier', async () => {
  const originalCrypto = globalThis.crypto
  const originalBtoa = globalThis.btoa
  Object.defineProperty(globalThis, 'crypto', {
    configurable: true,
    value: {
    getRandomValues(target) {
      target.set(new Uint8Array(target.length).map((_, index) => index + 1))
      return target
    },
    subtle: {
      async digest(_algorithm, payload) {
        return new Uint8Array(payload).buffer
      }
    }
    }
  })
  globalThis.btoa = (value) => Buffer.from(value, 'binary').toString('base64')

  const pair = await createBrowserAuthPkcePair()

  assert.equal(pair.codeChallengeMethod, 'S256')
  assert.match(pair.codeVerifier, /^[A-Za-z0-9_-]+$/)
  assert.match(pair.codeChallenge, /^[A-Za-z0-9_-]+$/)

  Object.defineProperty(globalThis, 'crypto', { configurable: true, value: originalCrypto })
  globalThis.btoa = originalBtoa
})

test('browser auth opens the InboxBridge page and redeems the completed session', async () => {
  const originalCrypto = globalThis.crypto
  const originalBtoa = globalThis.btoa
  Object.defineProperty(globalThis, 'crypto', {
    configurable: true,
    value: {
    getRandomValues(target) {
      target.set(new Uint8Array(target.length).fill(7))
      return target
    },
    subtle: {
      async digest(_algorithm, payload) {
        return new Uint8Array(payload).buffer
      }
    }
    }
  })
  globalThis.btoa = (value) => Buffer.from(value, 'binary').toString('base64')

  let openedUrl = null
  let redeemAttempts = 0
  const session = await completeBrowserSignIn({
    browserFamily: 'chromium',
    extensionVersion: '0.1.0',
    label: 'Chromium browser extension',
    openWindow: async (url) => {
      openedUrl = url
    },
    pollIntervalMs: 1,
    redeemExtensionBrowserAuth: async (_serverUrl, request) => {
      redeemAttempts += 1
      if (redeemAttempts === 1) {
        return { status: 'PENDING' }
      }
      return {
        session: {
          publicBaseUrl: 'https://mail.example.com',
          tokens: {
            accessToken: 'access-1',
            refreshToken: 'refresh-1'
          },
          user: {
            username: 'alice'
          }
        },
        status: 'AUTHENTICATED'
      }
    },
    serverUrl: 'https://mail.example.com',
    sleep: async () => {},
    startExtensionBrowserAuth: async (_serverUrl, request) => {
      assert.equal(request.codeChallengeMethod, 'S256')
      assert.equal(request.browserFamily, 'chromium')
      return {
        browserUrl: 'https://mail.example.com/?extensionAuthRequest=request-1',
        requestId: 'request-1'
      }
    },
    timeoutMs: 100
  })

  assert.equal(openedUrl, 'https://mail.example.com/?extensionAuthRequest=request-1')
  assert.equal(session.tokens.accessToken, 'access-1')
  assert.equal(redeemAttempts, 2)

  Object.defineProperty(globalThis, 'crypto', { configurable: true, value: originalCrypto })
  globalThis.btoa = originalBtoa
})
