import test from 'node:test'
import assert from 'node:assert/strict'

import { createConfigStore, normalizeServerUrl } from '../../shared/src/config.js'
import { createInvalidExtensionAuthError } from '../../shared/src/auth-errors.js'

test('normalizeServerUrl trims and preserves https origins', () => {
  assert.equal(normalizeServerUrl(' https://mail.example.com/ '), 'https://mail.example.com')
})

test('normalizeServerUrl rejects every non-https origin', () => {
  assert.throws(() => normalizeServerUrl('http://localhost:3000/'), /Use HTTPS/)
  assert.throws(() => normalizeServerUrl('http://example.com'), /Use HTTPS/)
})

test('loadConfig refreshes expiring access tokens and persists the rotated session', async () => {
  let persistedPayload = null
  const store = createConfigStore({
    containsApiPermission: async () => false,
    containsOriginPermission: async () => true,
    currentTime: () => new Date('2026-04-12T10:00:00Z').getTime(),
    decryptJson: async () => ({
      accessToken: 'access-1',
      accessTokenExpiresAt: '2026-04-12T10:00:30Z',
      refreshToken: 'refresh-1',
      refreshTokenExpiresAt: '2026-05-12T10:00:00Z',
      username: 'alice'
    }),
    encryptJson: async (value) => ({ ciphertext: JSON.stringify(value), iv: 'iv' }),
    queryTabs: async () => [],
    refreshExtensionAuth: async () => ({
      session: {
        publicBaseUrl: 'https://mail.example.com',
        tokens: {
          accessToken: 'access-2',
          accessExpiresAt: '2026-04-12T11:00:00Z',
          refreshToken: 'refresh-2',
          refreshExpiresAt: '2026-05-12T11:00:00Z'
        },
        user: {
          username: 'alice'
        }
      }
    }),
    requestApiPermission: async () => true,
    requestOriginPermission: async () => true,
    sleep: async () => {},
    storageGet: async () => ({
      inboxbridgeExtensionConfig: {
        serverUrl: 'https://mail.example.com',
        theme: 'dark',
        auth: { ciphertext: 'encrypted', iv: 'iv' }
      }
    }),
    storageRemove: async () => {},
    storageSet: async (value) => {
      persistedPayload = value
    }
  })

  const config = await store.loadConfig()

  assert.equal(config.token, 'access-2')
  assert.equal(config.refreshToken, 'refresh-2')
  assert.equal(config.language, 'user')
  assert.equal(config.notifyErrors, false)
  assert.equal(config.notifyManualPollSuccess, false)
  assert.equal(config.theme, 'dark-blue')
  assert.equal(config.username, 'alice')
  assert.equal(persistedPayload.inboxbridgeExtensionConfig.serverUrl, 'https://mail.example.com')
  assert.equal(persistedPayload.inboxbridgeExtensionConfig.language, 'user')
  assert.equal(persistedPayload.inboxbridgeExtensionConfig.theme, 'dark-blue')
})

test('loadConfig prefers the canonical username over display name after refresh rotation', async () => {
  const store = createConfigStore({
    containsApiPermission: async () => false,
    containsOriginPermission: async () => true,
    currentTime: () => new Date('2026-04-12T10:00:00Z').getTime(),
    decryptJson: async () => ({
      accessToken: 'access-1',
      accessTokenExpiresAt: '2026-04-12T10:00:30Z',
      refreshToken: 'refresh-1',
      refreshTokenExpiresAt: '2026-05-12T10:00:00Z',
      username: 'admin'
    }),
    encryptJson: async (value) => ({ ciphertext: JSON.stringify(value), iv: 'iv' }),
    queryTabs: async () => [],
    refreshExtensionAuth: async () => ({
      session: {
        publicBaseUrl: 'https://mail.example.com',
        tokens: {
          accessToken: 'access-2',
          accessExpiresAt: '2026-04-12T11:00:00Z',
          refreshToken: 'refresh-2',
          refreshExpiresAt: '2026-05-12T11:00:00Z'
        },
        user: {
          username: 'admin',
          displayName: 'InboxBridge'
        }
      }
    }),
    requestApiPermission: async () => true,
    requestOriginPermission: async () => true,
    sleep: async () => {},
    storageGet: async () => ({
      inboxbridgeExtensionConfig: {
        serverUrl: 'https://mail.example.com',
        theme: 'dark',
        auth: { ciphertext: 'encrypted', iv: 'iv' }
      }
    }),
    storageRemove: async () => {},
    storageSet: async () => {}
  })

  const config = await store.loadConfig()

  assert.equal(config.username, 'admin')
})

test('loadConfig keeps saved auth when access-token refresh has a transient failure', async () => {
  let persistedPayload = null
  const store = createConfigStore({
    containsApiPermission: async () => false,
    containsOriginPermission: async () => true,
    currentTime: () => new Date('2026-04-12T10:00:00Z').getTime(),
    decryptJson: async () => ({
      accessToken: 'access-1',
      accessTokenExpiresAt: '2026-04-12T10:00:30Z',
      refreshToken: 'refresh-1',
      refreshTokenExpiresAt: '2026-05-12T10:00:00Z',
      username: 'alice'
    }),
    encryptJson: async (value) => ({ ciphertext: JSON.stringify(value), iv: 'iv' }),
    queryTabs: async () => [],
    refreshExtensionAuth: async () => {
      throw new TypeError('Failed to fetch')
    },
    requestApiPermission: async () => true,
    requestOriginPermission: async () => true,
    sleep: async () => {},
    storageGet: async () => ({
      inboxbridgeExtensionConfig: {
        serverUrl: 'https://mail.example.com',
        theme: 'dark',
        notifyErrors: true,
        auth: { ciphertext: 'encrypted', iv: 'iv' }
      }
    }),
    storageRemove: async () => {},
    storageSet: async (value) => {
      persistedPayload = value
    }
  })

  const config = await store.loadConfig()

  assert.equal(config.token, 'access-1')
  assert.equal(config.refreshToken, 'refresh-1')
  assert.equal(config.refreshFailed, true)
  assert.equal(config.notifyErrors, true)
  assert.equal(persistedPayload, null)
})

test('loadConfig keeps newer rotated auth when an older concurrent refresh is rejected', async () => {
  const storedConfigs = [
    {
      serverUrl: 'https://mail.example.com',
      theme: 'dark',
      auth: { version: 'old' }
    },
    {
      serverUrl: 'https://mail.example.com',
      theme: 'dark',
      auth: { version: 'new' }
    }
  ]
  let storageReads = 0
  let persistedPayload = null
  const store = createConfigStore({
    containsApiPermission: async () => false,
    containsOriginPermission: async () => true,
    currentTime: () => new Date('2026-04-12T10:00:00Z').getTime(),
    decryptJson: async (payload) => payload.version === 'new'
      ? {
          accessToken: 'access-2',
          accessTokenExpiresAt: '2026-04-12T11:00:00Z',
          refreshToken: 'refresh-2',
          refreshTokenExpiresAt: '2026-05-12T11:00:00Z',
          username: 'alice'
        }
      : {
          accessToken: 'access-1',
          accessTokenExpiresAt: '2026-04-12T10:00:30Z',
          refreshToken: 'refresh-1',
          refreshTokenExpiresAt: '2026-05-12T10:00:00Z',
          username: 'alice'
        },
    encryptJson: async (value) => ({ ciphertext: JSON.stringify(value), iv: 'iv' }),
    queryTabs: async () => [],
    refreshExtensionAuth: async () => {
      throw createInvalidExtensionAuthError()
    },
    requestApiPermission: async () => true,
    requestOriginPermission: async () => true,
    storageGet: async () => {
      const config = storedConfigs[Math.min(storageReads, storedConfigs.length - 1)]
      storageReads += 1
      return { inboxbridgeExtensionConfig: config }
    },
    storageRemove: async () => {},
    storageSet: async (value) => {
      persistedPayload = value
    }
  })

  const config = await store.loadConfig()

  assert.equal(config.token, 'access-2')
  assert.equal(config.refreshToken, 'refresh-2')
  assert.equal(config.username, 'alice')
  assert.equal(persistedPayload, null)
})

test('loadConfig waits briefly for a concurrently rotated token before clearing auth', async () => {
  let persistedPayload = null
  let sleepCalls = 0
  let storageReads = 0
  const store = createConfigStore({
    containsApiPermission: async () => false,
    containsOriginPermission: async () => true,
    currentTime: () => new Date('2026-04-12T10:00:00Z').getTime(),
    decryptJson: async (auth) => {
      if (auth?.ciphertext === 'encrypted-fresh') {
        return {
          accessToken: 'access-2',
          accessTokenExpiresAt: '2026-04-12T11:00:00Z',
          refreshToken: 'refresh-2',
          refreshTokenExpiresAt: '2026-05-12T11:00:00Z',
          username: 'alice'
        }
      }
      return {
        accessToken: 'access-1',
        accessTokenExpiresAt: '2026-04-12T10:00:30Z',
        refreshToken: 'refresh-1',
        refreshTokenExpiresAt: '2026-05-12T10:00:00Z',
        username: 'alice'
      }
    },
    encryptJson: async (value) => ({ ciphertext: JSON.stringify(value), iv: 'iv' }),
    queryTabs: async () => [],
    refreshExtensionAuth: async () => {
      throw createInvalidExtensionAuthError()
    },
    requestApiPermission: async () => true,
    requestOriginPermission: async () => true,
    sleep: async () => {
      sleepCalls += 1
    },
    storageGet: async () => {
      storageReads += 1
      if (storageReads <= 2) {
        return {
          inboxbridgeExtensionConfig: {
            serverUrl: 'https://mail.example.com',
            language: 'pt-PT',
            theme: 'dark',
            notifyErrors: true,
            notifyManualPollSuccess: true,
            userLanguage: 'en',
            userThemeMode: 'DARK_BLUE',
            auth: { ciphertext: 'encrypted', iv: 'iv' }
          }
        }
      }
      return {
        inboxbridgeExtensionConfig: {
          serverUrl: 'https://mail.example.com',
          language: 'pt-PT',
          theme: 'dark',
          notifyErrors: true,
          notifyManualPollSuccess: true,
          userLanguage: 'en',
          userThemeMode: 'DARK_BLUE',
          auth: { ciphertext: 'encrypted-fresh', iv: 'iv-fresh' }
        }
      }
    },
    storageRemove: async () => {},
    storageSet: async (value) => {
      persistedPayload = value
    }
  })

  const config = await store.loadConfig()

  assert.equal(config.token, 'access-2')
  assert.equal(config.refreshToken, 'refresh-2')
  assert.equal(config.username, 'alice')
  assert.equal(sleepCalls, 1)
  assert.equal(persistedPayload, null)
})

test('loadConfig clears auth only when refresh is rejected and no newer token exists', async () => {
  let persistedPayload = null
  const warnings = []
  const store = createConfigStore({
    containsApiPermission: async () => false,
    containsOriginPermission: async () => true,
    currentTime: () => new Date('2026-04-12T10:00:00Z').getTime(),
    decryptJson: async () => ({
      accessToken: 'access-1',
      accessTokenExpiresAt: '2026-04-12T10:00:30Z',
      refreshToken: 'refresh-1',
      refreshTokenExpiresAt: '2026-05-12T10:00:00Z',
      username: 'alice'
    }),
    encryptJson: async (value) => ({ ciphertext: JSON.stringify(value), iv: 'iv' }),
    logger: {
      info: () => {},
      warn: (...args) => warnings.push(args)
    },
    queryTabs: async () => [],
    refreshExtensionAuth: async () => {
      throw createInvalidExtensionAuthError()
    },
    requestApiPermission: async () => true,
    requestOriginPermission: async () => true,
    sleep: async () => {},
    storageGet: async () => ({
      inboxbridgeExtensionConfig: {
        serverUrl: 'https://mail.example.com',
        language: 'pt-PT',
        theme: 'dark',
        notifyErrors: true,
        notifyManualPollSuccess: true,
        userLanguage: 'en',
        userThemeMode: 'DARK_BLUE',
        auth: { ciphertext: 'encrypted', iv: 'iv' }
      }
    }),
    storageRemove: async () => {},
    storageSet: async (value) => {
      persistedPayload = value
    }
  })

  const config = await store.loadConfig()

  assert.equal(config.token, undefined)
  assert.equal(config.refreshToken, undefined)
  assert.equal(config.serverUrl, 'https://mail.example.com')
  assert.equal(config.notifyErrors, true)
  assert.equal(config.notifyManualPollSuccess, true)
  assert.deepEqual(persistedPayload, {
    inboxbridgeExtensionConfig: {
      language: 'pt-PT',
      notifyErrors: true,
      notifyManualPollSuccess: true,
      serverUrl: 'https://mail.example.com',
      theme: 'dark-blue',
      userLanguage: 'en',
      userThemeMode: 'DARK_BLUE'
    }
  })
  assert.equal(warnings.some((entry) => entry[1] === 'Cleared saved extension auth after confirmed invalid refresh token.'), true)
  assert.equal(JSON.stringify(warnings).includes('refresh-1'), false)
  assert.equal(JSON.stringify(warnings).includes('access-1'), false)
})

test('detectServerUrl prefers the last real InboxBridge browser tab over extension pages', async () => {
  const store = createConfigStore({
    containsApiPermission: async () => false,
    containsOriginPermission: async () => true,
    decryptJson: async () => null,
    encryptJson: async () => null,
    queryTabs: async () => [
      { active: true, lastAccessed: 20, title: 'InboxBridge Extension Settings', url: 'chrome-extension://example/options.html' },
      { active: false, lastAccessed: 10, title: 'InboxBridge Go', url: 'https://mail-older.example.com/remote' },
      { active: false, lastAccessed: 30, title: 'InboxBridge Go', url: 'https://mail.example.com/remote' }
    ],
    refreshExtensionAuth: async () => null,
    requestApiPermission: async () => true,
    requestOriginPermission: async () => true,
    storageGet: async () => ({}),
    storageRemove: async () => {},
    storageSet: async () => {}
  })

  assert.equal(await store.detectServerUrl(), 'https://mail.example.com')
})

test('detectServerUrl ignores unrelated active tabs such as Gmail and falls back to real InboxBridge tabs', async () => {
  const store = createConfigStore({
    containsApiPermission: async () => false,
    containsOriginPermission: async () => true,
    decryptJson: async () => null,
    encryptJson: async () => null,
    queryTabs: async () => [
      { active: true, lastAccessed: 50, title: 'Inbox (42) - alice@example.com - Gmail', url: 'https://mail.google.com/mail/u/2/#inbox' },
      { active: false, lastAccessed: 40, title: 'InboxBridge Admin', url: 'https://bridge.example.com/' },
      { active: false, lastAccessed: 30, title: 'InboxBridge Go', url: 'https://bridge-older.example.com/remote' }
    ],
    refreshExtensionAuth: async () => null,
    requestApiPermission: async () => true,
    requestOriginPermission: async () => true,
    storageGet: async () => ({}),
    storageRemove: async () => {},
    storageSet: async () => {}
  })

  assert.equal(await store.detectServerUrl(), 'https://bridge.example.com')
})

test('ensureTabPermission keeps tabs access optional until the user asks for current-tab detection', async () => {
  let requested = 0
  const store = createConfigStore({
    containsApiPermission: async () => false,
    containsOriginPermission: async () => true,
    decryptJson: async () => null,
    encryptJson: async () => null,
    queryTabs: async () => [],
    refreshExtensionAuth: async () => null,
    requestApiPermission: async (permission) => {
      requested += 1
      assert.equal(permission, 'tabs')
      return true
    },
    requestOriginPermission: async () => true,
    storageGet: async () => ({}),
    storageRemove: async () => {},
    storageSet: async () => {}
  })

  assert.equal(await store.ensureTabPermission(), true)
  assert.equal(requested, 1)
})

test('ensureOriginPermission retries with contains after a direct request throws', async () => {
  let containsChecks = 0
  const store = createConfigStore({
    containsApiPermission: async () => false,
    containsOriginPermission: async () => {
      containsChecks += 1
      return true
    },
    decryptJson: async () => null,
    encryptJson: async () => null,
    queryTabs: async () => [],
    refreshExtensionAuth: async () => null,
    requestApiPermission: async () => true,
    requestOriginPermission: async () => {
      throw new Error('permissions.request may only be called from a user input handler')
    },
    storageGet: async () => ({}),
    storageRemove: async () => {},
    storageSet: async () => {}
  })

  assert.equal(await store.ensureOriginPermission('https://mail.example.com'), true)
  assert.equal(containsChecks, 1)
})

test('ensureNotificationPermission keeps browser notifications optional until the user enables them', async () => {
  let requested = 0
  const store = createConfigStore({
    containsApiPermission: async (permission) => permission === 'notifications' ? false : false,
    containsOriginPermission: async () => true,
    decryptJson: async () => null,
    encryptJson: async () => null,
    queryTabs: async () => [],
    refreshExtensionAuth: async () => null,
    requestApiPermission: async (permission) => {
      requested += 1
      assert.equal(permission, 'notifications')
      return true
    },
    requestOriginPermission: async () => true,
    storageGet: async () => ({}),
    storageRemove: async () => {},
    storageSet: async () => {}
  })

  assert.equal(await store.ensureNotificationPermission(), true)
  assert.equal(requested, 1)
})

test('saveThemePreference persists the selected theme without disturbing saved config', async () => {
  let persistedPayload = null
  const store = createConfigStore({
    containsApiPermission: async () => false,
    containsOriginPermission: async () => true,
    decryptJson: async () => null,
    encryptJson: async () => null,
    queryTabs: async () => [],
    refreshExtensionAuth: async () => null,
    requestApiPermission: async () => true,
    requestOriginPermission: async () => true,
    storageGet: async () => ({
      inboxbridgeExtensionConfig: {
        serverUrl: 'https://mail.example.com',
        language: 'user',
        theme: 'system',
        auth: { ciphertext: 'encrypted', iv: 'iv' }
      }
    }),
    storageRemove: async () => {},
    storageSet: async (value) => {
      persistedPayload = value
    }
  })

  await store.saveThemePreference('dark')

  assert.deepEqual(persistedPayload, {
    inboxbridgeExtensionConfig: {
      serverUrl: 'https://mail.example.com',
      language: 'user',
        theme: 'dark-blue',
      auth: { ciphertext: 'encrypted', iv: 'iv' }
    }
  })
})
