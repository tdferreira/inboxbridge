import { act, renderHook } from '@testing-library/react'
import { useAuthSecurityController } from './useAuthSecurityController'

describe('useAuthSecurityController', () => {
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
    const onLogoutReset = vi.fn()
    const openConfirmation = vi.fn()
    const pushNotification = vi.fn()
    const t = vi.fn((key) => key)
    const errorText = vi.fn((key) => key)
    const withPending = vi.fn(async (_key, action) => action())

    const hook = renderHook((props) => useAuthSecurityController(props), {
      initialProps: {
        closeConfirmation,
        errorText,
        loadAppData,
        onLogoutReset,
        openConfirmation,
        pushNotification,
        t,
        withPending,
        ...overrides
      }
    })

    return {
      ...hook,
      onLogoutReset
    }
  }

  it('marks the security dialog dirty when password fields are populated', () => {
    const { result } = renderController()

    act(() => {
      result.current.setPasswordForm({
        currentPassword: 'Current1!',
        newPassword: 'NewPass1!',
        confirmNewPassword: ''
      })
    })

    expect(result.current.securityDialogDirty).toBe(true)
  })

  it('normalizes loaded passkeys to an array', () => {
    const { result } = renderController()

    act(() => {
      result.current.applyLoadedPasskeys([{ id: 1, label: 'Laptop' }])
    })
    expect(result.current.myPasskeys).toEqual([{ id: 1, label: 'Laptop' }])

    act(() => {
      result.current.applyLoadedPasskeys(null)
    })
    expect(result.current.myPasskeys).toEqual([])
  })

  it('clears session state on a 401 loadSession response', async () => {
    fetch.mockResolvedValue({ status: 401, ok: false })
    const { result } = renderController()

    act(() => {
      result.current.setSession({ username: 'admin' })
    })

    await act(async () => {
      await result.current.loadSession()
    })

    expect(result.current.session).toBeNull()
    expect(result.current.authError).toBe('')
    expect(result.current.authLoading).toBe(false)
  })
})
