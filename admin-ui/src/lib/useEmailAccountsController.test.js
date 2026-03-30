import { act, renderHook, waitFor } from '@testing-library/react'
import { DEFAULT_EMAIL_ACCOUNT_FORM, applyEmailAccountPreset } from './sourceEmailAccountForm'
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
    ...overrides
  }
}

describe('useEmailAccountsController', () => {
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
})
