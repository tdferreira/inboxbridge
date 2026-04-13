import { act, renderHook, waitFor } from '@testing-library/react'
import { DEFAULT_EMAIL_ACCOUNT_FORM, applyEmailAccountPreset } from '@/lib/sourceEmailAccountForm'
import { useEmailAccountsController } from './useEmailAccountsController'

function createEmailAccount(overrides = {}) {
  return {
    emailAccountId: 'account-1',
    username: 'owner@example.com',
    authMethod: 'PASSWORD',
    password: 'secret',
    oauthProvider: 'NONE',
    host: 'imap.example.com',
    port: '993',
    connectionSecurity: 'SSL_TLS',
    folders: ['INBOX'],
    markReadAfterPoll: false,
    postPollAction: 'NONE',
    postPollTargetFolder: '',
    ...overrides
  }
}

describe('useEmailAccountsController', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  function renderController(overrides = {}) {
    const withPending = vi.fn(async (_key, action) => action())
    const pushNotification = vi.fn()
    const t = vi.fn((key, params = {}) => {
      if (key === 'emailAccounts.duplicateId') {
        return `Duplicate id: ${params.emailAccountId}`
      }
      return key
    })
    const errorText = vi.fn((key) => key)
    const loadAppData = vi.fn()

    const hook = renderHook((props) => useEmailAccountsController(props), {
      initialProps: {
        activeBatchPollSourceIds: [],
        authOptions: { sourceOAuthProviders: ['GOOGLE', 'MICROSOFT'] },
      errorText,
        isPending: () => false,
        language: 'en',
        loadAppData,
        pushNotification,
        refreshSectionData: vi.fn(),
        sessionUsername: 'owner@example.com',
        systemDashboardEmailAccounts: [],
        t,
        withPending,
        ...overrides
      }
    })

    return {
      ...hook,
      errorText,
      loadAppData,
      pushNotification,
      t,
      withPending
    }
  }

  it('resets the form when opening the add email account dialog', () => {
    const { result } = renderController()

    act(() => {
      result.current.handleEmailAccountFormChange({
        ...DEFAULT_EMAIL_ACCOUNT_FORM,
        emailAccountId: 'stale-id',
        authMethod: 'OAUTH2',
        oauthProvider: 'GOOGLE'
      })
    })

    act(() => {
      result.current.openAddFetcherDialog()
    })

    expect(result.current.showFetcherDialog).toBe(true)
    expect(result.current.emailAccountForm).toEqual(DEFAULT_EMAIL_ACCOUNT_FORM)
    expect(result.current.emailAccountDuplicateError).toBe('')
  })

  it('loads visible email accounts in sorted order and hydrates the edit form', () => {
    const { result } = renderController()
    const ownAccount = createEmailAccount({ emailAccountId: 'zeta', username: 'owner@example.com' })
    const otherAccount = createEmailAccount({ emailAccountId: 'alpha', username: 'other@example.com' })

    act(() => {
      result.current.applyLoadedEmailAccounts([ownAccount, otherAccount])
    })

    expect(result.current.visibleFetchers.map((fetcher) => fetcher.emailAccountId)).toEqual(['alpha', 'zeta'])

    act(() => {
      result.current.editEmailAccount(ownAccount)
    })

    expect(result.current.showFetcherDialog).toBe(true)
    expect(result.current.emailAccountForm).toEqual(expect.objectContaining({
      emailAccountId: 'zeta',
      originalEmailAccountId: 'zeta',
      username: 'owner@example.com'
    }))
  })

  it('applies provider presets through the normalized email-account form contract', () => {
    const { result } = renderController()

    act(() => {
      result.current.handleEmailAccountFormChange(DEFAULT_EMAIL_ACCOUNT_FORM)
    })

    act(() => {
      result.current.applyEmailAccountPreset('gmail')
    })

    expect(result.current.emailAccountForm).toEqual(
      applyEmailAccountPreset(DEFAULT_EMAIL_ACCOUNT_FORM, 'gmail', {
        sourceOAuthProviders: ['GOOGLE', 'MICROSOFT']
      })
    )
  })

  it('stores duplicate email-account notifications as translation descriptors', async () => {
    const { result, pushNotification } = renderController()

    act(() => {
      result.current.applyLoadedEmailAccounts([createEmailAccount({ emailAccountId: 'account-1' })])
      result.current.handleEmailAccountFormChange({
        ...DEFAULT_EMAIL_ACCOUNT_FORM,
        originalEmailAccountId: '',
        emailAccountId: 'account-1'
      })
    })

    await act(async () => {
      await result.current.saveEmailAccount({ preventDefault: vi.fn() })
    })

    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      message: expect.objectContaining({
        key: 'emailAccounts.duplicateId',
        kind: 'translation',
        params: { emailAccountId: 'account-1' }
      }),
      tone: 'error'
    }))
  })

  it('loads source folders after a successful IMAP connection test', async () => {
    const originalFetch = global.fetch
    global.fetch = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          message: 'Connection test succeeded.',
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          authenticated: true,
          folder: 'INBOX',
          folderAccessible: true
        })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ folders: ['INBOX', 'Archive'] })
      })

    const { result } = renderController()

    act(() => {
      result.current.handleEmailAccountFormChange({
        ...DEFAULT_EMAIL_ACCOUNT_FORM,
        emailAccountId: 'fetcher-a',
        host: 'imap.example.com',
        username: 'user@example.com',
        password: 'secret'
      })
    })

    await act(async () => {
      await result.current.testEmailAccountConnection()
    })

    expect(global.fetch).toHaveBeenNthCalledWith(1, '/api/app/email-accounts/test-connection', expect.objectContaining({ method: 'POST' }))
    expect(global.fetch).toHaveBeenNthCalledWith(2, '/api/app/email-accounts/folders', expect.objectContaining({ method: 'POST' }))
    expect(result.current.emailAccountFolders).toEqual(['INBOX', 'Archive'])

    global.fetch = originalFetch
  })

  it('auto-enables TLS after the connection test verifies a secure endpoint', async () => {
    const originalFetch = global.fetch
    global.fetch = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          message: 'Connection test succeeded over TLS.',
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          tlsAvailable: true,
          tlsRecommended: true,
          recommendedTlsPort: 993,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          authenticated: true,
          folder: 'INBOX',
          folderAccessible: true
        })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ folders: ['INBOX', 'Archive'] })
      })

    const { result, pushNotification } = renderController()

    act(() => {
      result.current.handleEmailAccountFormChange({
        ...DEFAULT_EMAIL_ACCOUNT_FORM,
        emailAccountId: 'fetcher-a',
        host: 'imap.example.com',
        port: 143,
        tls: false,
        username: 'user@example.com',
        password: 'secret'
      })
    })

    await act(async () => {
      await result.current.testEmailAccountConnection()
    })

    expect(result.current.emailAccountForm.tls).toBe(true)
    expect(result.current.emailAccountForm.port).toBe(993)
    expect(result.current.emailAccountTestResult.tlsRecommended).toBe(true)
    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      message: 'Connection test succeeded over TLS.',
      tone: 'warning'
    }))

    global.fetch = originalFetch
  })

  it('loads source folders when editing an existing IMAP account', async () => {
    const originalFetch = global.fetch
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ folders: ['INBOX', 'Archive'] })
    })

    const { result } = renderController()
    const existingAccount = createEmailAccount({
      emailAccountId: 'fetcher-a',
      protocol: 'IMAP',
      folder: 'INBOX'
    })

    act(() => {
      result.current.editEmailAccount(existingAccount)
    })

    await waitFor(() => {
      expect(result.current.emailAccountFolders).toEqual(['INBOX', 'Archive'])
    })
    expect(global.fetch).toHaveBeenCalledWith('/api/app/email-accounts/folders', expect.objectContaining({ method: 'POST' }))

    global.fetch = originalFetch
  })

  it('loads source folders when the folder field receives focus and the dialog still has no folder list', async () => {
    const originalFetch = global.fetch
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ folders: ['INBOX', 'Archive'] })
    })

    const { result } = renderController()

    act(() => {
      result.current.openAddFetcherDialog()
      result.current.handleEmailAccountFormChange({
        ...DEFAULT_EMAIL_ACCOUNT_FORM,
        emailAccountId: 'fetcher-a',
        protocol: 'IMAP',
        host: 'imap.example.com',
        username: 'user@example.com',
        password: 'secret'
      })
    })

    await act(async () => {
      result.current.handleFolderInputFocus()
    })

    await waitFor(() => {
      expect(result.current.emailAccountFolders).toEqual(['INBOX', 'Archive'])
    })
    expect(global.fetch).toHaveBeenCalledTimes(1)

    global.fetch = originalFetch
  })

  it('retries folder loading on later focus after an earlier edit-time fetch failed', async () => {
    const originalFetch = global.fetch
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-04-06T00:00:00Z'))
    global.fetch = vi.fn()
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ folders: ['INBOX', 'Archive'] })
      })

    const { result } = renderController()
    const existingAccount = createEmailAccount({
      emailAccountId: 'fetcher-a',
      protocol: 'IMAP',
      folder: 'INBOX'
    })

    await act(async () => {
      result.current.editEmailAccount(existingAccount)
      await Promise.resolve()
      await Promise.resolve()
    })

    expect(global.fetch).toHaveBeenCalledTimes(1)
    expect(result.current.emailAccountFolders).toEqual([])

    await act(async () => {
      vi.advanceTimersByTime(2_500)
      result.current.handleFolderInputFocus()
      await Promise.resolve()
      await Promise.resolve()
    })

    expect(result.current.emailAccountFolders).toEqual(['INBOX', 'Archive'])
    expect(global.fetch).toHaveBeenCalledTimes(2)

    vi.useRealTimers()
    global.fetch = originalFetch
  })

  it('does not refetch folders on repeated focus after they were already loaded, but does refetch after server settings change', async () => {
    const originalFetch = global.fetch
    global.fetch = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ folders: ['INBOX', 'Archive'] })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ folders: ['Projects', 'Sent'] })
      })

    const { result } = renderController()

    act(() => {
      result.current.openAddFetcherDialog()
      result.current.handleEmailAccountFormChange({
        ...DEFAULT_EMAIL_ACCOUNT_FORM,
        emailAccountId: 'fetcher-a',
        protocol: 'IMAP',
        host: 'imap.example.com',
        username: 'user@example.com',
        password: 'secret'
      })
    })

    await act(async () => {
      result.current.handleFolderInputFocus()
    })

    await waitFor(() => {
      expect(result.current.emailAccountFolders).toEqual(['INBOX', 'Archive'])
    })

    act(() => {
      result.current.handleFolderInputFocus()
    })

    expect(global.fetch).toHaveBeenCalledTimes(1)

    act(() => {
      result.current.handleEmailAccountFormChange((current) => ({
        ...current,
        host: 'mail.changed.example.com'
      }))
    })

    await act(async () => {
      result.current.handleFolderInputFocus()
    })

    await waitFor(() => {
      expect(result.current.emailAccountFolders).toEqual(['Projects', 'Sent'])
    })
    expect(global.fetch).toHaveBeenCalledTimes(2)

    global.fetch = originalFetch
  })

  it('uses first-letter typing as a fallback fetch trigger only while folders are still unknown', async () => {
    const originalFetch = global.fetch
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ folders: ['INBOX', 'Archive'] })
    })

    const { result } = renderController()

    act(() => {
      result.current.openAddFetcherDialog()
      result.current.handleEmailAccountFormChange({
        ...DEFAULT_EMAIL_ACCOUNT_FORM,
        emailAccountId: 'fetcher-a',
        protocol: 'IMAP',
        host: 'imap.example.com',
        username: 'user@example.com',
        password: 'secret'
      })
    })

    await act(async () => {
      result.current.handleFolderInputActivity('I')
    })

    await waitFor(() => {
      expect(result.current.emailAccountFolders).toEqual(['INBOX', 'Archive'])
    })
    expect(global.fetch).toHaveBeenCalledTimes(1)

    act(() => {
      result.current.handleFolderInputActivity('In')
      result.current.handleFolderInputActivity('A')
    })

    expect(global.fetch).toHaveBeenCalledTimes(1)

    global.fetch = originalFetch
  })

  it('includes post-poll source actions in the save payload', async () => {
    const originalFetch = global.fetch
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ emailAccountId: 'fetcher-a' })
    })

    const { result, loadAppData } = renderController()

    act(() => {
      result.current.handleEmailAccountFormChange({
        ...DEFAULT_EMAIL_ACCOUNT_FORM,
        emailAccountId: 'fetcher-a',
        host: 'imap.example.com',
        username: 'user@example.com',
        password: 'secret',
        markReadAfterPoll: true,
        postPollAction: 'MOVE',
        postPollTargetFolder: 'Archive'
      })
    })

    await act(async () => {
      await result.current.saveEmailAccount({ preventDefault: vi.fn() })
    })

    const [, request] = global.fetch.mock.calls[0]
    expect(request.method).toBe('PUT')
    expect(JSON.parse(request.body)).toEqual(expect.objectContaining({
      markReadAfterPoll: true,
      postPollAction: 'MOVE',
      postPollTargetFolder: 'Archive'
    }))
    expect(loadAppData).toHaveBeenCalled()

    global.fetch = originalFetch
  })

  it('marks only the active batch-polled fetcher as loading', () => {
    const { result } = renderController({ activeBatchPollSourceIds: ['beta'] })

    act(() => {
      result.current.applyLoadedEmailAccounts([
        createEmailAccount({ emailAccountId: 'alpha' }),
        createEmailAccount({ emailAccountId: 'beta' })
      ])
    })

    expect(result.current.fetcherPollLoadingIds).toEqual(['beta'])
  })

  it('marks every running batch-polled fetcher as loading when multiple sources run in parallel', () => {
    const { result } = renderController({ activeBatchPollSourceIds: ['alpha', 'beta'] })

    act(() => {
      result.current.applyLoadedEmailAccounts([
        createEmailAccount({ emailAccountId: 'alpha' }),
        createEmailAccount({ emailAccountId: 'beta' }),
        createEmailAccount({ emailAccountId: 'gamma' })
      ])
    })

    expect(result.current.fetcherPollLoadingIds.slice().sort()).toEqual(['alpha', 'beta'])
  })

  it('ignores disabled fetchers when the active batch source matches them', () => {
    const { result } = renderController({
      activeBatchPollSourceIds: ['env-fetcher'],
      sessionUsername: 'admin',
      systemDashboardEmailAccounts: [
        createEmailAccount({ emailAccountId: 'env-fetcher', managementSource: 'ENVIRONMENT', enabled: false })
      ]
    })

    act(() => {
      result.current.applyLoadedEmailAccounts([
        createEmailAccount({ emailAccountId: 'db-fetcher' })
      ], [
        { id: 'env-fetcher', emailAccountId: 'env-fetcher', enabled: false, effectivePollEnabled: true, effectivePollInterval: '5m', effectiveFetchWindow: 50, protocol: 'IMAP', authMethod: 'PASSWORD', oauthProvider: 'NONE', host: 'imap.example.com', port: 993, tls: true, folder: 'INBOX', unreadOnly: false, customLabel: '', markReadAfterPoll: false, postPollAction: 'NONE', postPollTargetFolder: '', tokenStorageMode: 'PASSWORD', totalImportedMessages: 0, lastImportedAt: null, lastEvent: null, pollingState: null }
      ])
    })

    expect(result.current.fetcherPollLoadingIds).toEqual([])
  })

  it('toggles a database-backed fetcher enabled state through the save endpoint', async () => {
    const originalFetch = global.fetch
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ emailAccountId: 'account-1', enabled: false })
    })

    const { result, loadAppData, pushNotification } = renderController()
    const fetcher = createEmailAccount({
      emailAccountId: 'account-1',
      enabled: true,
      protocol: 'IMAP',
      host: 'imap.example.com',
      port: 993,
      tls: true,
      username: 'owner@example.com',
      folder: 'INBOX',
      unreadOnly: false,
      customLabel: 'Inbox'
    })

    await act(async () => {
      await result.current.toggleEmailAccountEnabled(fetcher)
    })

    const [, request] = global.fetch.mock.calls[0]
    expect(request.method).toBe('PUT')
    expect(JSON.parse(request.body)).toEqual(expect.objectContaining({
      emailAccountId: 'account-1',
      originalEmailAccountId: 'account-1',
      enabled: false
    }))
    expect(loadAppData).toHaveBeenCalled()
    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      message: expect.objectContaining({
        key: 'notifications.bridgeDisabled',
        kind: 'translation',
        params: { emailAccountId: 'account-1' }
      }),
      tone: 'success'
    }))

    global.fetch = originalFetch
  })

  it('can save an unvalidated email account with polling disabled', async () => {
    const originalFetch = global.fetch
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ emailAccountId: 'fetcher-a' })
    })

    const { result, loadAppData } = renderController()

    act(() => {
      result.current.handleEmailAccountFormChange({
        ...DEFAULT_EMAIL_ACCOUNT_FORM,
        emailAccountId: 'fetcher-a',
        host: 'imap.example.com',
        username: 'user@example.com',
        password: 'secret',
        enabled: true
      })
    })

    await act(async () => {
      await result.current.saveEmailAccountWithoutValidation()
    })

    const [, request] = global.fetch.mock.calls[0]
    expect(request.method).toBe('PUT')
    expect(JSON.parse(request.body)).toEqual(expect.objectContaining({
      emailAccountId: 'fetcher-a',
      enabled: false
    }))
    expect(loadAppData).toHaveBeenCalled()

    global.fetch = originalFetch
  })
})
