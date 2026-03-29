import { act, renderHook } from '@testing-library/react'
import { DEFAULT_EMAIL_ACCOUNT_FORM, applyEmailAccountPreset } from './sourceEmailAccountForm'
import { useEmailAccountsController } from './useEmailAccountsController'

function createEmailAccount(overrides = {}) {
  return {
    bridgeId: 'account-1',
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
        return `Duplicate id: ${params.bridgeId}`
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
        bridgeId: 'stale-id',
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
    const ownAccount = createEmailAccount({ bridgeId: 'zeta', username: 'owner@example.com' })
    const otherAccount = createEmailAccount({ bridgeId: 'alpha', username: 'other@example.com' })

    act(() => {
      result.current.applyLoadedEmailAccounts([ownAccount, otherAccount])
    })

    expect(result.current.visibleFetchers.map((fetcher) => fetcher.bridgeId)).toEqual(['alpha', 'zeta'])

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
})
