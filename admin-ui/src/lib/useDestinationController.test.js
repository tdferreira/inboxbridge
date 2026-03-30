import { act, renderHook } from '@testing-library/react'
import { useDestinationController } from './useDestinationController'

describe('useDestinationController', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function renderController(overrides = {}) {
    const closeConfirmation = vi.fn()
    const loadAppData = vi.fn()
    const openConfirmation = vi.fn()
    const pushNotification = vi.fn()
    const withPending = vi.fn(async (_key, action) => action())
    const t = vi.fn((key) => key)
    const errorText = vi.fn(async (key) => key)

    const hook = renderHook((props) => useDestinationController(props), {
      initialProps: {
        closeConfirmation,
        destinationConfig: {
          provider: 'OUTLOOK_IMAP',
          host: 'outlook.office365.com',
          port: '993',
          tls: true,
          authMethod: 'OAUTH2',
          oauthProvider: 'MICROSOFT',
          username: 'owner@example.com',
          password: 'secret',
          folder: 'INBOX'
        },
        destinationMeta: { linked: false },
        errorText,
        language: 'en',
        loadAppData,
        openConfirmation,
        pushNotification,
        t,
        withPending,
        ...overrides
      }
    })

    return {
      ...hook,
      closeConfirmation,
      errorText,
      loadAppData,
      openConfirmation,
      pushNotification,
      t,
      withPending
    }
  }

  it('saves the destination configuration using normalized numeric fields', async () => {
    fetch.mockResolvedValue({ ok: true, json: async () => ({}) })
    const event = { preventDefault: vi.fn() }
    const { result, loadAppData, pushNotification } = renderController()

    await act(async () => {
      await result.current.saveDestinationConfig(event)
    })

    expect(event.preventDefault).toHaveBeenCalled()
    const [, request] = fetch.mock.calls[0]
    expect(request.method).toBe('PUT')
    expect(JSON.parse(request.body)).toEqual(expect.objectContaining({
      port: 993,
      provider: 'OUTLOOK_IMAP'
    }))
    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      message: 'notifications.destinationSaved',
      tone: 'success'
    }))
    expect(loadAppData).toHaveBeenCalled()
  })

  it('forces stale outlook destination state back to Microsoft OAuth before saving', async () => {
    fetch.mockResolvedValue({ ok: true, json: async () => ({}) })
    const event = { preventDefault: vi.fn() }
    const { result } = renderController({
      destinationConfig: {
        provider: 'OUTLOOK_IMAP',
        host: 'outlook.office365.com',
        port: '993',
        tls: true,
        authMethod: 'PASSWORD',
        oauthProvider: 'NONE',
        username: 'owner@example.com',
        password: 'secret',
        folder: 'INBOX'
      }
    })

    await act(async () => {
      await result.current.saveDestinationConfig(event)
    })

    const [, request] = fetch.mock.calls[0]
    expect(JSON.parse(request.body)).toEqual(expect.objectContaining({
      provider: 'OUTLOOK_IMAP',
      authMethod: 'OAUTH2',
      oauthProvider: 'MICROSOFT',
      password: ''
    }))
  })

  it('opens a reconnect confirmation when Gmail is already linked', () => {
    const { result, openConfirmation } = renderController({
      destinationConfig: {
        provider: 'GMAIL_API'
      },
      destinationMeta: { linked: true }
    })

    act(() => {
      result.current.startDestinationOAuth()
    })

    expect(openConfirmation).toHaveBeenCalledWith(expect.objectContaining({
      actionKey: 'googleOAuthSelf',
      body: 'gmail.reconnectConfirmBody',
      title: 'gmail.reconnectConfirmTitle'
    }))
  })

  it('saves before starting Microsoft destination OAuth', async () => {
    fetch.mockResolvedValue({ ok: true, json: async () => ({}) })
    const assign = vi.fn()
    const originalLocation = window.location
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { assign }
    })

    const { result } = renderController()

    await act(async () => {
      await result.current.startDestinationOAuth()
    })

    expect(fetch).toHaveBeenCalledWith('/api/app/destination-config', expect.objectContaining({ method: 'PUT' }))
    expect(assign).toHaveBeenCalledWith('/api/microsoft-oauth/start/destination?lang=en')

    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation
    })
  })

  it('warns before replacing an already linked Google destination with Microsoft OAuth', async () => {
    const { result, openConfirmation } = renderController({
      destinationMeta: { linked: true, oauthConnected: false, provider: 'GMAIL_API' }
    })

    await act(async () => {
      await result.current.startDestinationOAuth()
    })

    expect(openConfirmation).toHaveBeenCalledWith(expect.objectContaining({
      actionKey: 'microsoftDestinationOAuth',
      title: 'destination.replaceLinkedConfirmTitle',
      body: 'destination.replaceLinkedGoogleConfirmBody'
    }))
  })

  it('warns before replacing a linked Gmail destination when saving and authenticating Outlook', async () => {
    const { result, openConfirmation } = renderController({
      destinationMeta: { linked: true, oauthConnected: false, provider: 'GMAIL_API' }
    })

    await act(async () => {
      await result.current.saveDestinationConfigAndAuthenticate()
    })

    expect(openConfirmation).toHaveBeenCalledWith(expect.objectContaining({
      actionKey: 'microsoftDestinationOAuth',
      title: 'destination.replaceLinkedConfirmTitle',
      body: 'destination.replaceLinkedGoogleConfirmBody'
    }))
  })

  it('tests the destination mailbox connection against the destination test endpoint', async () => {
    fetch.mockResolvedValue({ ok: true, json: async () => ({ success: true, message: 'Connection test succeeded.' }) })
    const { result } = renderController()

    await act(async () => {
      await result.current.testDestinationConnection()
    })

    expect(fetch).toHaveBeenCalledWith('/api/app/destination-config/test-connection', expect.objectContaining({ method: 'POST' }))
  })

  it('saves before authenticating a newly added Gmail destination from the modal flow', async () => {
    fetch.mockResolvedValue({ ok: true, json: async () => ({}) })
    const assign = vi.fn()
    const originalLocation = window.location
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { assign }
    })

    const { result } = renderController({
      destinationConfig: { provider: 'GMAIL_API' },
      destinationMeta: { configured: false, linked: false, oauthConnected: false, provider: 'GMAIL_API' }
    })

    await act(async () => {
      await result.current.saveDestinationConfigAndAuthenticate()
    })

    expect(fetch).toHaveBeenCalledWith('/api/app/destination-config', expect.objectContaining({ method: 'PUT' }))
    expect(assign).toHaveBeenCalledWith('/api/google-oauth/start/self?lang=en')

    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation
    })
  })

  it('uses the saved linked provider for unlink confirmation even when the draft provider changed in the modal', async () => {
    const { result, openConfirmation } = renderController({
      destinationConfig: { provider: 'OUTLOOK_IMAP' },
      destinationMeta: { linked: true, oauthConnected: true, provider: 'GMAIL_API' }
    })

    await act(async () => {
      await result.current.unlinkDestinationAccount()
    })

    expect(openConfirmation).toHaveBeenCalledWith(expect.objectContaining({
      body: 'gmail.unlinkConfirmBody',
      title: 'gmail.unlinkConfirmTitle'
    }))
  })

  it('shows the Microsoft manual removal notice after unlinking an Outlook destination', async () => {
    fetch.mockResolvedValue({ ok: true, json: async () => ({ providerRevocationAttempted: false, providerRevoked: false }) })
    const { result, openConfirmation, pushNotification, loadAppData } = renderController({
      destinationMeta: { linked: true, oauthConnected: true, provider: 'OUTLOOK_IMAP' }
    })

    await act(async () => {
      await result.current.unlinkDestinationAccount()
    })

    const confirmation = openConfirmation.mock.calls[0][0]

    await act(async () => {
      await confirmation.onConfirm()
    })

    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      message: 'notifications.microsoftDestinationUnlinked',
      tone: 'warning'
    }))
    expect(loadAppData).toHaveBeenCalled()
  })

  it('saves before starting Gmail destination OAuth when switching away from Outlook', async () => {
    fetch.mockResolvedValue({ ok: true, json: async () => ({}) })
    const assign = vi.fn()
    const originalLocation = window.location
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { assign }
    })

    const { result } = renderController({
      destinationConfig: { provider: 'GMAIL_API' },
      destinationMeta: { linked: false, oauthConnected: false, provider: 'OUTLOOK_IMAP' }
    })

    await act(async () => {
      await result.current.startDestinationOAuth()
    })

    expect(fetch).toHaveBeenCalledWith('/api/app/destination-config', expect.objectContaining({ method: 'PUT' }))
    expect(assign).toHaveBeenCalledWith('/api/google-oauth/start/self?lang=en')

    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation
    })
  })
})
