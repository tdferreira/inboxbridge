import { act, renderHook } from '@testing-library/react'
import { AUTH_EXPIRED_EVENT } from './api'
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
      onLogoutReset,
      pushNotification
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

  it('loads a registration challenge when the register dialog opens', async () => {
    fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({
        enabled: true,
        provider: 'ALTCHA',
        altcha: {
          challengeId: 'challenge-1',
          algorithm: 'SHA-256',
          challenge: 'abc',
          salt: 'salt',
          signature: 'sig',
          maxNumber: 1000
        }
      })
    })
    const { result } = renderController()

    await act(async () => {
      await result.current.openRegisterDialog()
    })

    expect(fetch).toHaveBeenCalledWith('/api/auth/register/challenge')
    expect(result.current.registerOpen).toBe(true)
    expect(result.current.registerChallenge).toEqual({
      enabled: true,
      provider: 'ALTCHA',
      altcha: {
        challengeId: 'challenge-1',
        algorithm: 'SHA-256',
        challenge: 'abc',
        salt: 'salt',
        signature: 'sig',
        maxNumber: 1000
      }
    })
    expect(result.current.registerForm.captchaToken).toBe('')
  })

  it('refreshes the challenge after a failed registration attempt', async () => {
    fetch
      .mockResolvedValueOnce({
        ok: false,
        text: vi.fn().mockResolvedValue(JSON.stringify({
          code: 'registration_challenge_incorrect',
          message: 'Registration challenge answer is incorrect'
        }))
      })
      .mockResolvedValueOnce({
        ok: true,
        json: vi.fn().mockResolvedValue({
          enabled: true,
          provider: 'ALTCHA',
          altcha: {
            challengeId: 'challenge-2',
            algorithm: 'SHA-256',
            challenge: 'def',
            salt: 'salt-2',
            signature: 'sig-2',
            maxNumber: 1000
          }
        })
      })
    const { result } = renderController()

    act(() => {
      result.current.setRegisterForm({
        username: 'alice',
        password: 'Secret#123',
        confirmPassword: 'Secret#123',
        captchaToken: 'solved-token'
      })
    })

    await act(async () => {
      await result.current.handleRegister({ preventDefault() {} })
    })

    expect(result.current.authError).toBe('The anti-robot check answer is incorrect. Try the new challenge again.')
    expect(result.current.registerChallenge?.altcha?.challengeId).toBe('challenge-2')
    expect(result.current.registerForm.captchaToken).toBe('')
  })

  it('loads session activity when the sessions tab opens', async () => {
    fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({
        recentLogins: [{ id: 1, ipAddress: '203.0.113.9' }],
        activeSessions: [{ id: 1, current: true }],
        geoIpConfigured: true
      })
    })
    const { result } = renderController()

    await act(async () => {
      await result.current.openSecurityPanel('sessions')
    })

    expect(fetch).toHaveBeenCalledWith('/api/account/sessions')
    expect(result.current.sessionActivity.recentLogins).toHaveLength(1)
    expect(result.current.sessionActivity.geoIpConfigured).toBe(true)
    expect(result.current.securityTab).toBe('sessions')
  })

  it('notifies when a newer non-current session is detected in the background', async () => {
    fetch
      .mockResolvedValueOnce({
        ok: true,
        json: vi.fn().mockResolvedValue({
          recentLogins: [{ id: 10, sessionType: 'BROWSER', current: true, createdAt: '2026-03-31T10:00:00Z' }],
          activeSessions: [{ id: 10, sessionType: 'BROWSER', current: true }]
        })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: vi.fn().mockResolvedValue({
          recentLogins: [{ id: 11, sessionType: 'REMOTE', current: false, createdAt: '2026-03-31T10:05:00Z' }, { id: 10, sessionType: 'BROWSER', current: true, createdAt: '2026-03-31T10:00:00Z' }],
          activeSessions: [{ id: 10, sessionType: 'BROWSER', current: true }, { id: 11, sessionType: 'REMOTE', current: false }]
        })
      })
    const { result, pushNotification } = renderController()

    await act(async () => {
      await result.current.pollSessionActivity({ announceNewSessions: true, suppressErrors: true })
    })

    expect(pushNotification).not.toHaveBeenCalled()

    await act(async () => {
      await result.current.pollSessionActivity({ announceNewSessions: true, suppressErrors: true })
    })

    expect(pushNotification).toHaveBeenCalledWith({
      message: {
        kind: 'translation',
        key: 'notifications.newSessionDetectedWithoutLocation',
        params: {
          sessionType: { kind: 'translation', key: 'sessions.kindRemote', params: {} }
        }
      },
      targetId: 'recent-session-REMOTE-11',
      tone: 'warning'
    })
  })

  it('does not show the generic passkey sign-in notification after a successful passkey login', async () => {
    Object.defineProperty(window, 'PublicKeyCredential', {
      configurable: true,
      value: function PublicKeyCredential() {}
    })
    Object.defineProperty(window.navigator, 'credentials', {
      configurable: true,
      value: {
        get: vi.fn().mockResolvedValue({
          id: 'credential-1',
          rawId: new Uint8Array([1, 2, 3]).buffer,
          type: 'public-key',
          response: {
            clientDataJSON: new Uint8Array([4, 5, 6]).buffer,
            authenticatorData: new Uint8Array([7, 8, 9]).buffer,
            signature: new Uint8Array([10, 11, 12]).buffer,
            userHandle: null
          },
          getClientExtensionResults: () => ({})
        })
      }
    })

    fetch
      .mockResolvedValueOnce({
        ok: true,
        json: vi.fn().mockResolvedValue({
          status: 'PASSKEY_REQUIRED',
          passkeyChallenge: {
            ceremonyId: 'ceremony-1',
            publicKeyJson: JSON.stringify({
              publicKey: {
                challenge: 'AQ',
                allowCredentials: []
              }
            })
          }
        })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: vi.fn().mockResolvedValue({
          user: { id: 1, username: 'admin', mustChangePassword: false }
        })
      })

    const { result, pushNotification } = renderController()

    await act(async () => {
      await result.current.handleLogin({ preventDefault() {} })
    })

    expect(result.current.session).toEqual({ id: 1, username: 'admin', mustChangePassword: false })
    expect(pushNotification).not.toHaveBeenCalledWith(
      expect.objectContaining({
        message: { kind: 'translation', key: 'notifications.signedInWithPasskey', params: {} }
      })
    )
  })

  it('clears the current session when an auth-expired event is dispatched', async () => {
    const { result, onLogoutReset } = renderController()

    act(() => {
      result.current.setSession({ username: 'admin' })
      result.current.setPasswordForm({
        currentPassword: 'Current1!',
        newPassword: 'NewPass1!',
        confirmNewPassword: 'NewPass1!'
      })
      result.current.setPasskeyLabel('Laptop')
      result.current.setRegisterForm({
        username: 'alice',
        password: 'Secret#123',
        confirmPassword: 'Secret#123',
        captchaToken: 'solved-token'
      })
    })

    await act(async () => {
      window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT))
    })

    expect(result.current.session).toBeNull()
    expect(result.current.passwordForm).toEqual({
      currentPassword: '',
      newPassword: '',
      confirmNewPassword: ''
    })
    expect(result.current.passkeyLabel).toBe('')
    expect(result.current.registerForm).toEqual({
      username: '',
      password: '',
      confirmPassword: '',
      captchaToken: ''
    })
    expect(result.current.authError).toBe('auth.sessionExpired')
    expect(onLogoutReset).toHaveBeenCalledTimes(1)
  })
})
