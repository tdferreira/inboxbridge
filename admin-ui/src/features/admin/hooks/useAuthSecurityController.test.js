import { act, renderHook, waitFor } from '@testing-library/react'
import { AUTH_EXPIRED_EVENT } from '@/lib/api'
import { useAuthSecurityController } from './useAuthSecurityController'

describe('useAuthSecurityController', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.useRealTimers()
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

  it('exports the latest secret-management report as a downloadable JSON file', async () => {
    const click = vi.fn()
    const createObjectURL = vi.fn(() => 'blob:secret-report')
    const revokeObjectURL = vi.fn()
    const originalCreateElement = document.createElement.bind(document)
    let downloadAnchor = null
    const createElement = vi.spyOn(document, 'createElement').mockImplementation((tagName) => {
      if (tagName === 'a') {
        downloadAnchor = {
          click,
          download: '',
          href: ''
        }
        return downloadAnchor
      }
      return originalCreateElement(tagName)
    })
    Object.defineProperty(window, 'URL', {
      configurable: true,
      value: {
        createObjectURL,
        revokeObjectURL
      }
    })
    fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({
        exportedAt: '2026-04-15T12:00:00Z',
        status: {
          mode: 'LOCAL',
          activeKeyVersion: 'LOCAL:v2'
        }
      })
    })
    const { result, pushNotification } = renderController()

    await act(async () => {
      const completed = await result.current.handleExportSecretManagementReport()
      expect(completed).toBe(true)
    })

    expect(fetch).toHaveBeenCalledWith('/api/admin/secret-management/report')
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(createObjectURL.mock.calls[0][0]).toBeInstanceOf(Blob)
    expect(createElement).toHaveBeenCalledWith('a')
    expect(downloadAnchor.href).toBe('blob:secret-report')
    expect(downloadAnchor.download).toBe('inboxbridge-secret-management-report-2026-04-15T12-00-00Z.json')
    expect(click).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:secret-report')
    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      message: expect.objectContaining({
        kind: 'translation',
        key: 'notifications.secretManagementReportExported'
      }),
      targetId: 'secret-management-section',
      tone: 'success'
    }))
  })

  it('loads the backend-generated secret-management recovery checklist', async () => {
    fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({
        title: 'Secret-management recovery checklist',
        rollbackRecommended: true
      })
    })
    const { result } = renderController()

    await act(async () => {
      const guide = await result.current.loadSecretManagementRecoveryGuide()
      expect(guide).toEqual({
        title: 'Secret-management recovery checklist',
        rollbackRecommended: true
      })
    })

    expect(fetch).toHaveBeenCalledWith('/api/admin/secret-management/recovery-guide')
  })

  it('surfaces a notification when the secret-management recovery checklist cannot be loaded', async () => {
    fetch.mockResolvedValue({
      ok: false,
      status: 500,
      text: vi.fn().mockResolvedValue('backend unavailable')
    })
    const { result, pushNotification } = renderController()

    await act(async () => {
      const guide = await result.current.loadSecretManagementRecoveryGuide()
      expect(guide).toBeNull()
    })

    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      tone: 'error',
      targetId: 'secret-management-section'
    }))
  })

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

  it('starts with empty login fields when bootstrap prefill is not enabled', () => {
    const { result } = renderController({ bootstrapLoginPrefillEnabled: false })

    expect(result.current.loginForm).toEqual({ username: '', password: '' })
  })

  it('prefills bootstrap credentials only when explicitly enabled', () => {
    const { result, rerender } = renderController({ bootstrapLoginPrefillEnabled: true })

    expect(result.current.loginForm).toEqual({ username: 'admin', password: 'nimda' })

    rerender({
      bootstrapLoginPrefillEnabled: false,
      closeConfirmation: vi.fn(),
      errorText: vi.fn((key) => key),
      loadAppData: vi.fn(),
      onLogoutReset: vi.fn(),
      openConfirmation: vi.fn(),
      pushNotification: vi.fn(),
      t: vi.fn((key) => key),
      withPending: vi.fn(async (_key, action) => action())
    })

    expect(result.current.loginForm).toEqual({ username: '', password: '' })
  })

  it('clears login credentials on logout and does not reapply bootstrap prefill', async () => {
    fetch.mockResolvedValue({ ok: true, status: 204 })
    const { result } = renderController({ bootstrapLoginPrefillEnabled: true })

    expect(result.current.loginForm).toEqual({ username: 'admin', password: 'nimda' })

    act(() => {
      result.current.setSession({ username: 'alice' })
      result.current.setLoginForm({ username: 'alice', password: 'Secret#123' })
    })

    await act(async () => {
      await result.current.handleLogout()
    })

    expect(fetch).toHaveBeenCalledWith('/api/auth/logout', { method: 'POST' })
    expect(result.current.session).toBeNull()
    expect(result.current.loginForm).toEqual({ username: '', password: '' })
    expect(result.current.loginStage).toBe('username')
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
          parameters: {
            algorithm: 'PBKDF2/SHA-256',
            nonce: '00112233445566778899aabbccddeeff',
            salt: '0f0e0d0c0b0a09080706050403020100',
            cost: 5000,
            keyLength: 32,
            keyPrefix: '00'
          },
          signature: 'sig'
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
        parameters: {
          algorithm: 'PBKDF2/SHA-256',
          nonce: '00112233445566778899aabbccddeeff',
          salt: '0f0e0d0c0b0a09080706050403020100',
          cost: 5000,
          keyLength: 32,
          keyPrefix: '00'
        },
        signature: 'sig'
      }
    })
    expect(result.current.registerForm.captchaToken).toBe('')
  })

  it('normalizes secret-management status arrays and optional rotation metadata', async () => {
    fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({
        secureStorageConfigured: true,
        mode: 'LOCAL',
        providerId: 'LOCAL',
        providerComponents: null,
        modeAssessments: null,
        configuredLegacyKeyIds: null,
        keyUsage: null,
        reencryptionRequirements: null,
        recentRetirementReviews: null,
        rotationPlan: {
          planId: 'provider-migration',
          title: 'Provider migration is pending',
          summary: '4 stored records still depend on older or different encryption targets.',
          recommendedAction: 'Run full re-encryption before retiring the previous provider path.',
          targetKeyVersion: 'OPENBAO_TRANSIT:transit:v3',
          affectedRecordCount: 4,
          unavailableRecordCount: 0,
          impactedAreas: ['source-mailboxes', 'destination-mailboxes'],
          rotationNeeded: true,
          requiresFullReencryption: true,
          metadataRewrapSupported: false
        }
      })
    })
    const { result } = renderController()

    act(() => {
      result.current.setSession({ role: 'ADMIN' })
    })

    await act(async () => {
      await result.current.loadSecretManagementStatus()
    })

    expect(result.current.secretManagementStatus.providerComponents).toEqual([])
    expect(result.current.secretManagementStatus.modeAssessments).toEqual([])
    expect(result.current.secretManagementStatus.configuredLegacyKeyIds).toEqual([])
    expect(result.current.secretManagementStatus.keyUsage).toEqual([])
    expect(result.current.secretManagementStatus.reencryptionRequirements).toEqual([])
    expect(result.current.secretManagementStatus.recentRetirementReviews).toEqual([])
    expect(result.current.secretManagementStatus.rotationPlan).toEqual({
      planId: 'provider-migration',
      title: 'Provider migration is pending',
      summary: '4 stored records still depend on older or different encryption targets.',
      recommendedAction: 'Run full re-encryption before retiring the previous provider path.',
      targetKeyVersion: 'OPENBAO_TRANSIT:transit:v3',
      affectedRecordCount: 4,
      unavailableRecordCount: 0,
      impactedAreas: ['source-mailboxes', 'destination-mailboxes'],
      rotationNeeded: true,
      requiresFullReencryption: true,
      metadataRewrapSupported: false
    })
  })

  it('loads a backend-generated secret-management migration guide', async () => {
    fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({
        currentMode: 'LOCAL',
        targetMode: 'OPENBAO_TRANSIT',
        targetReady: true,
        checks: [{ checkId: 'target-ready', satisfied: true }],
        beforeSwitchSteps: ['Prepare the new provider.'],
        switchSteps: ['Set SECRET_PROVIDER_MODE=OPENBAO_TRANSIT.'],
        afterSwitchSteps: ['Run stored-secret re-encryption.']
      })
    })
    const { result } = renderController()

    const guide = await result.current.loadSecretManagementMigrationGuide('OPENBAO_TRANSIT')

    expect(fetch).toHaveBeenCalledWith('/api/admin/secret-management/migration-guide?targetMode=OPENBAO_TRANSIT')
    expect(guide.targetMode).toBe('OPENBAO_TRANSIT')
    expect(guide.targetReady).toBe(true)
  })

  it('notifies when loading the secret-management migration guide fails', async () => {
    fetch.mockResolvedValue({
      ok: false,
      text: vi.fn().mockResolvedValue(JSON.stringify({
        code: 'bad_request',
        message: 'The target mode is invalid.'
      }))
    })
    const { result, pushNotification } = renderController()

    const guide = await result.current.loadSecretManagementMigrationGuide('INVALID')

    expect(guide).toBeNull()
    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      tone: 'error',
      targetId: 'secret-management-section'
    }))
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
            parameters: {
              algorithm: 'PBKDF2/SHA-256',
              nonce: 'ffeeddccbbaa99887766554433221100',
              salt: '00112233445566778899aabbccddeeff',
              cost: 5000,
              keyLength: 32,
              keyPrefix: '00'
            },
            signature: 'sig-2'
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

  it('briefly cools down repeated login submissions on the frontend', async () => {
    vi.useFakeTimers()
    fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({
        user: { id: 1, username: 'admin', mustChangePassword: false }
      })
    })
    const { result } = renderController()

    await act(async () => {
      result.current.setLoginForm({ username: 'admin', password: 'Secret#123' })
    })

    await act(async () => {
      await result.current.handleLogin({ preventDefault() {} })
    })

    await act(async () => {
      await result.current.handleLogin({ preventDefault() {} })
    })

    await act(async () => {
      await result.current.handleLogin({ preventDefault() {} })
    })

    expect(fetch).toHaveBeenCalledTimes(1)
    expect(result.current.loginCoolingDown).toBe(true)

    await act(async () => {
      vi.advanceTimersByTime(1500)
    })

    expect(result.current.loginCoolingDown).toBe(false)
    vi.useRealTimers()
  })

  it('briefly cools down repeated registration submissions on the frontend', async () => {
    vi.useFakeTimers()
    fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({
        username: 'alice',
        message: 'Registration received.'
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

    await act(async () => {
      await result.current.handleRegister({ preventDefault() {} })
    })

    expect(fetch).toHaveBeenCalledTimes(1)
    expect(result.current.registerCoolingDown).toBe(true)

    await act(async () => {
      vi.advanceTimersByTime(1500)
    })

    expect(result.current.registerCoolingDown).toBe(false)
    vi.useRealTimers()
  })

  it('loads session activity when the sessions tab opens', async () => {
    fetch
      .mockResolvedValueOnce({
        ok: true,
        json: vi.fn().mockResolvedValue({
          recentLogins: [{ id: 1, ipAddress: '203.0.113.9' }],
          activeSessions: [{ id: 1, current: true }],
          geoIpConfigured: true
        })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: vi.fn().mockResolvedValue([
          { id: 44, label: 'Laptop', tokenPrefix: 'ibx_123' }
        ])
      })
    const { result } = renderController()

    await act(async () => {
      await result.current.openSecurityPanel('sessions')
    })

    expect(fetch).toHaveBeenCalledWith('/api/account/sessions')
    expect(fetch).toHaveBeenCalledWith('/api/extension/sessions')
    expect(result.current.sessionActivity.recentLogins).toHaveLength(1)
    expect(result.current.sessionActivity.geoIpConfigured).toBe(true)
    expect(result.current.extensionSessions).toEqual([
      { id: 44, label: 'Laptop', tokenPrefix: 'ibx_123' }
    ])
    expect(result.current.securityTab).toBe('sessions')
  })

  it('revokes an extension session after confirmation', async () => {
    const openConfirmation = vi.fn()
    fetch.mockResolvedValue({
      ok: true,
      status: 204,
      text: vi.fn().mockResolvedValue(''),
      json: vi.fn().mockResolvedValue(null)
    })
    const { result, pushNotification } = renderController({ openConfirmation })

    await act(async () => {
      await result.current.handleRevokeExtensionSession({ id: 88, label: 'Firefox profile' })
    })

    expect(openConfirmation).toHaveBeenCalledTimes(1)
    const confirmation = openConfirmation.mock.calls[0][0]

    await act(async () => {
      await confirmation.onConfirm()
    })

    expect(fetch).toHaveBeenCalledWith('/api/extension/sessions/88', { method: 'DELETE' })
    expect(pushNotification).toHaveBeenCalledWith({
      message: {
        kind: 'translation',
        key: 'notifications.extensionSessionRevoked',
        params: {}
      },
      targetId: 'security-extension-sessions-panel-section',
      tone: 'success'
    })
  })

  it('revokes all extension sessions after confirmation', async () => {
    const openConfirmation = vi.fn()
    fetch
      .mockResolvedValueOnce({
        ok: true,
        status: 204,
        text: vi.fn().mockResolvedValue('')
      })
      .mockResolvedValueOnce({
        ok: true,
        json: vi.fn().mockResolvedValue([])
      })
    const { result, pushNotification } = renderController({ openConfirmation })

    await act(async () => {
      await result.current.handleRevokeAllExtensionSessions()
    })

    expect(openConfirmation).toHaveBeenCalledTimes(1)
    const confirmation = openConfirmation.mock.calls[0][0]

    await act(async () => {
      await confirmation.onConfirm()
    })

    expect(fetch).toHaveBeenCalledWith('/api/extension/sessions', { method: 'DELETE' })
    expect(pushNotification).toHaveBeenCalledWith({
      message: {
        kind: 'translation',
        key: 'notifications.extensionSessionsRevoked',
        params: {}
      },
      targetId: 'security-extension-sessions-panel-section',
      tone: 'success'
    })
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
      result.current.setLoginForm({ username: 'admin', password: '' })
    })

    await act(async () => {
      await result.current.handleLogin({ preventDefault() {} })
    })

    await act(async () => {
      await result.current.handleLogin({ preventDefault() {} })
    })

    await waitFor(() => {
      expect(result.current.session).toEqual({ id: 1, username: 'admin', mustChangePassword: false })
    })
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

  it('loads secret-management status for admin sessions', async () => {
    fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({
        secureStorageConfigured: true,
        mode: 'LOCAL',
        providerId: 'LOCAL',
        activeKeyVersion: 'LOCAL:v2',
        activeKeyId: 'LOCAL:v2',
        configuredLegacyKeyIds: ['LOCAL:v1'],
        protectedRecordCount: 7,
        activeKeyRecordCount: 5,
        nonActiveKeyRecordCount: 2,
        unavailableKeyRecordCount: 0,
        envManagedMailboxSecretsAllowed: false,
        configuredEnvManagedSourceCount: 3,
        envManagedGoogleRefreshTokenConfigured: true,
        safeToRetireLegacyKeys: false,
        legacyKeyRetirementReady: false,
        reauthenticationRequired: true,
        reauthenticationSatisfied: false,
        reauthenticationExpiresAt: null,
        reencryptionPreview: {
          activeKeyVersion: 'LOCAL:v2',
          totalRecordsPendingUpdate: 2,
          totalSecretValuesPendingRewrite: 2,
          totalFullReencryptionCount: 2,
          totalMetadataRewrapCount: 0,
          areas: []
        },
        reencryptionRequest: {
          status: 'PENDING',
          executeAfter: '2026-04-16T10:00:00Z',
          verificationPassed: false,
          plannedPreview: {
            activeKeyVersion: 'LOCAL:v2',
            totalRecordsPendingUpdate: 2,
            totalSecretValuesPendingRewrite: 2,
            totalFullReencryptionCount: 2,
            totalMetadataRewrapCount: 0,
            areas: []
          },
          totalRecordsUpdated: 0,
          totalSecretValuesReencrypted: 0,
          totalFullReencryptionCount: 0,
          totalMetadataRewrapCount: 0,
          areas: [],
          followUp: {
            browserExtensionSessionsRevoked: 0,
            remoteSessionsRevoked: 0,
            cachedOAuthAccessTokensCleared: 0
          },
          verification: null
        },
        keyUsage: [
          {
            keyVersion: 'LOCAL:v2',
            recordCount: 5,
            areas: 'oauth-credentials',
            active: true,
            availableForDecryption: true
          }
        ],
        retirementRequirements: [
          {
            requirementId: 'rotation-complete',
            title: 'No encrypted records still depend on a legacy rotation target',
            detail: '2 stored records still depend on older or different encryption targets and must be rewritten to LOCAL:v2.',
            remediationSteps: ['Finish the pending re-encryption or metadata rewrap first.'],
            configReferences: ['active provider / key settings'],
            actionTargetId: 'secret-management-rotation-plan',
            actionLabel: 'Review rotation plan',
            satisfied: false,
            blocking: true
          }
        ],
        latestRetirementReview: {
          reviewId: 8,
          reviewedAt: '2026-04-15T13:00:00Z',
          reviewedByUserId: 1,
          reviewedByUsername: 'admin',
          providerId: 'LOCAL',
          activeKeyVersion: 'LOCAL:v2',
          activeKeyId: 'LOCAL:v2',
          configuredLegacyKeyIds: ['LOCAL:v1'],
          safeToRetireLegacyKeys: false,
          legacyKeyRetirementReady: false,
          nonActiveKeyRecordCount: 2,
          unavailableKeyRecordCount: 0,
          latestRequestStatus: 'PENDING',
          blockingRequirementsRemaining: 1,
          unsatisfiedRequirementIds: ['rotation-complete']
        },
        recentRetirementReviews: [
          {
            reviewId: 8,
            reviewedAt: '2026-04-15T13:00:00Z',
            reviewedByUserId: 1,
            reviewedByUsername: 'admin',
            providerId: 'LOCAL',
            activeKeyVersion: 'LOCAL:v2',
            activeKeyId: 'LOCAL:v2',
            configuredLegacyKeyIds: ['LOCAL:v1'],
            safeToRetireLegacyKeys: false,
            legacyKeyRetirementReady: false,
            nonActiveKeyRecordCount: 2,
            unavailableKeyRecordCount: 0,
            latestRequestStatus: 'PENDING',
            blockingRequirementsRemaining: 1,
            unsatisfiedRequirementIds: ['rotation-complete']
          }
        ]
      })
    })
    const { result } = renderController()

    act(() => {
      result.current.setSession({ id: 1, role: 'ADMIN' })
    })

    await act(async () => {
      await result.current.loadSecretManagementStatus()
    })

    expect(fetch).toHaveBeenCalledWith('/api/admin/secret-management')
    expect(result.current.secretManagementStatus.activeKeyId).toBe('LOCAL:v2')
    expect(result.current.secretManagementStatus.configuredLegacyKeyIds).toEqual(['LOCAL:v1'])
    expect(result.current.secretManagementStatus.envManagedMailboxSecretsAllowed).toBe(false)
    expect(result.current.secretManagementStatus.configuredEnvManagedSourceCount).toBe(3)
    expect(result.current.secretManagementStatus.reauthenticationRequired).toBe(true)
    expect(result.current.secretManagementStatus.reauthenticationSatisfied).toBe(false)
    expect(result.current.secretManagementStatus.reencryptionPreview).toEqual({
      activeKeyVersion: 'LOCAL:v2',
      totalRecordsPendingUpdate: 2,
      totalSecretValuesPendingRewrite: 2,
      totalFullReencryptionCount: 2,
      totalMetadataRewrapCount: 0,
      areas: []
    })
    expect(result.current.secretManagementStatus.reencryptionRequest).toEqual(expect.objectContaining({
      status: 'PENDING',
      plannedPreview: {
        activeKeyVersion: 'LOCAL:v2',
        totalRecordsPendingUpdate: 2,
        totalSecretValuesPendingRewrite: 2,
        totalFullReencryptionCount: 2,
        totalMetadataRewrapCount: 0,
        areas: []
      }
    }))
    expect(result.current.secretManagementStatus.legacyKeyRetirementReady).toBe(false)
    expect(result.current.secretManagementStatus.retirementRequirements).toHaveLength(1)
    expect(result.current.secretManagementStatus.latestRetirementReview).toEqual(expect.objectContaining({
      reviewId: 8,
      reviewedByUsername: 'admin'
    }))
    expect(result.current.secretManagementStatus.recentRetirementReviews).toHaveLength(1)
  })

  it('records a secret-management retirement review and refreshes the status', async () => {
    fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({
        secureStorageConfigured: true,
        mode: 'LOCAL',
        providerId: 'LOCAL',
        activeKeyVersion: 'LOCAL:v2',
        activeKeyId: 'LOCAL:v2',
        configuredLegacyKeyIds: ['LOCAL:v1'],
        legacyKeyRetirementReady: false,
        retirementRequirements: [],
        latestRetirementReview: {
          reviewId: 9,
          reviewedAt: '2026-04-15T14:00:00Z',
          reviewedByUserId: 1,
          reviewedByUsername: 'admin',
          providerId: 'LOCAL',
          activeKeyVersion: 'LOCAL:v2',
          activeKeyId: 'LOCAL:v2',
          configuredLegacyKeyIds: ['LOCAL:v1'],
          safeToRetireLegacyKeys: false,
          legacyKeyRetirementReady: false,
          nonActiveKeyRecordCount: 2,
          unavailableKeyRecordCount: 0,
          latestRequestStatus: 'PENDING',
          blockingRequirementsRemaining: 1,
          unsatisfiedRequirementIds: ['rotation-complete']
        },
        recentRetirementReviews: [
          {
            reviewId: 9,
            reviewedAt: '2026-04-15T14:00:00Z',
            reviewedByUserId: 1,
            reviewedByUsername: 'admin',
            providerId: 'LOCAL',
            activeKeyVersion: 'LOCAL:v2',
            activeKeyId: 'LOCAL:v2',
            configuredLegacyKeyIds: ['LOCAL:v1'],
            safeToRetireLegacyKeys: false,
            legacyKeyRetirementReady: false,
            nonActiveKeyRecordCount: 2,
            unavailableKeyRecordCount: 0,
            latestRequestStatus: 'PENDING',
            blockingRequirementsRemaining: 1,
            unsatisfiedRequirementIds: ['rotation-complete']
          }
        ]
      })
    })
    const { result, pushNotification } = renderController()

    act(() => {
      result.current.setSession({ id: 1, role: 'ADMIN' })
    })

    await act(async () => {
      await result.current.handleRecordSecretManagementRetirementReview()
    })

    expect(fetch).toHaveBeenCalledWith('/api/admin/secret-management/retirement-review', {
      method: 'POST'
    })
    expect(result.current.secretManagementStatus.latestRetirementReview).toEqual(expect.objectContaining({
      reviewId: 9,
      reviewedByUsername: 'admin'
    }))
    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      message: { kind: 'translation', key: 'notifications.secretManagementRetirementReviewRecorded', params: {} },
      targetId: 'secret-management-section',
      tone: 'success'
    }))
  })

  it('verifies secret-management retirement completion and updates status', async () => {
    fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({
        secureStorageConfigured: true,
        mode: 'LOCAL',
        providerId: 'LOCAL',
        activeKeyVersion: 'LOCAL:v2',
        activeKeyId: 'LOCAL:v2',
        configuredLegacyKeyIds: [],
        safeToRetireLegacyKeys: true,
        legacyKeyRetirementReady: true,
        retirementRequirements: [],
        latestRetirementReview: {
          reviewId: 9,
          reviewedAt: '2026-04-15T14:00:00Z',
          reviewedByUserId: 1,
          reviewedByUsername: 'admin',
          providerId: 'LOCAL',
          activeKeyVersion: 'LOCAL:v2',
          activeKeyId: 'LOCAL:v2',
          configuredLegacyKeyIds: [],
          safeToRetireLegacyKeys: true,
          legacyKeyRetirementReady: true,
          nonActiveKeyRecordCount: 0,
          unavailableKeyRecordCount: 0,
          latestRequestStatus: 'COMPLETED',
          blockingRequirementsRemaining: 0,
          unsatisfiedRequirementIds: [],
          completion: {
            verifiedAt: '2026-04-15T15:00:00Z',
            verifiedByUserId: 1,
            verifiedByUsername: 'admin',
            status: 'VERIFIED',
            message: 'Legacy-key cleanup was verified against the latest recorded retirement review.',
            unsatisfiedCheckIds: []
          }
        },
        recentRetirementReviews: []
      })
    })
    const { result, pushNotification } = renderController()

    act(() => {
      result.current.setSession({ id: 1, role: 'ADMIN' })
    })

    await act(async () => {
      await result.current.handleVerifySecretManagementRetirementCompletion()
    })

    expect(fetch).toHaveBeenCalledWith('/api/admin/secret-management/retirement-complete', {
      method: 'POST'
    })
    expect(result.current.secretManagementStatus.latestRetirementReview).toEqual(expect.objectContaining({
      reviewId: 9,
      completion: expect.objectContaining({
        status: 'VERIFIED'
      })
    }))
    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      message: { kind: 'translation', key: 'notifications.secretManagementRetirementCompletionVerified', params: {} },
      targetId: 'secret-management-section',
      tone: 'success'
    }))
  })

  it('re-encrypts stored secrets and refreshes secret-management status', async () => {
    fetch
      .mockResolvedValueOnce({
        ok: true,
        json: vi.fn().mockResolvedValue({
          activeKeyVersion: 'LOCAL:v2',
          totalRecordsUpdated: 4,
          totalSecretValuesReencrypted: 9,
          areas: [],
          followUp: {
            browserExtensionSessionsRevoked: 2,
            remoteSessionsRevoked: 1,
            cachedOAuthAccessTokensCleared: 3
          }
        })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: vi.fn().mockResolvedValue({
          secureStorageConfigured: true,
          mode: 'LOCAL',
          providerId: 'LOCAL',
          activeKeyVersion: 'LOCAL:v2',
          activeKeyId: 'LOCAL:v2',
          configuredLegacyKeyIds: [],
          protectedRecordCount: 9,
          activeKeyRecordCount: 9,
          nonActiveKeyRecordCount: 0,
          unavailableKeyRecordCount: 0,
          safeToRetireLegacyKeys: true,
          reauthenticationRequired: false,
          reauthenticationSatisfied: true,
          reauthenticationExpiresAt: null,
          keyUsage: []
        })
      })
    const pushNotification = vi.fn()
    const withPending = vi.fn(async (_key, action) => action())
    const { result } = renderHook((props) => useAuthSecurityController(props), {
      initialProps: {
        bootstrapLoginPrefillEnabled: false,
        closeConfirmation: vi.fn(),
        errorText: (key) => key,
        loadAppData: vi.fn(),
        onLogoutReset: vi.fn(),
        openConfirmation: vi.fn(),
        pushNotification,
        t: (key, params) => {
          if (key === 'notifications.secretManagementReencryptedWithFollowUp') {
            return `Re-encrypted ${params.records} records / ${params.secrets} secrets`
          }
          return key
        },
        withPending
      }
    })

    act(() => {
      result.current.setSession({ id: 1, role: 'ADMIN' })
    })

    await act(async () => {
      result.current.setSecretReencryptOptions({
        revokeBrowserExtensionSessions: true,
        revokeRemoteSessions: true,
        clearCachedOAuthAccessTokens: true
      })
    })

    let completed = null
    await act(async () => {
      completed = await result.current.handleReencryptStoredSecrets()
    })

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/admin/secret-management/re-encrypt', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        revokeBrowserExtensionSessions: true,
        revokeRemoteSessions: true,
        clearCachedOAuthAccessTokens: true
      })
    })
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/admin/secret-management')
    expect(completed).toEqual(expect.objectContaining({
      activeKeyVersion: 'LOCAL:v2',
      totalRecordsUpdated: 4,
      totalSecretValuesReencrypted: 9
    }))
    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      message: 'Re-encrypted 4 records / 9 secrets',
      targetId: 'secret-management-section',
      tone: 'success'
    }))
  })

  it('verifies secret-management password re-authentication and updates status', async () => {
    fetch.mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({
        secureStorageConfigured: true,
        mode: 'LOCAL',
        providerId: 'LOCAL',
        activeKeyVersion: 'LOCAL:v2',
        activeKeyId: 'LOCAL:v2',
        configuredLegacyKeyIds: [],
        protectedRecordCount: 9,
        activeKeyRecordCount: 9,
        nonActiveKeyRecordCount: 0,
        unavailableKeyRecordCount: 0,
        safeToRetireLegacyKeys: true,
        reauthenticationRequired: true,
        reauthenticationSatisfied: true,
        reauthenticationExpiresAt: '2026-04-15T10:25:30Z',
        keyUsage: []
      })
    })
    const { result, pushNotification } = renderController()

    act(() => {
      result.current.setSession({ id: 1, role: 'ADMIN' })
    })

    let completed = null
    await act(async () => {
      completed = await result.current.handleVerifySecretManagementPassword('Current1!')
    })

    expect(fetch).toHaveBeenCalledWith('/api/admin/secret-management/re-auth/password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password: 'Current1!' })
    })
    expect(completed).toEqual(expect.objectContaining({
      reauthenticationRequired: true,
      reauthenticationSatisfied: true
    }))
    expect(result.current.secretManagementStatus.reauthenticationSatisfied).toBe(true)
    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      message: { kind: 'translation', key: 'notifications.secretManagementReauthenticationVerified', params: {} },
      targetId: 'secret-management-section',
      tone: 'success'
    }))
  })

  it('verifies secret-management passkey re-authentication and updates status', async () => {
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
          ceremonyId: 'ceremony-1',
          publicKeyJson: JSON.stringify({
            publicKey: {
              challenge: 'AQ',
              allowCredentials: []
            }
          })
        })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: vi.fn().mockResolvedValue({
          secureStorageConfigured: true,
          mode: 'LOCAL',
          providerId: 'LOCAL',
          activeKeyVersion: 'LOCAL:v2',
          activeKeyId: 'LOCAL:v2',
          configuredLegacyKeyIds: [],
          protectedRecordCount: 9,
          activeKeyRecordCount: 9,
          nonActiveKeyRecordCount: 0,
          unavailableKeyRecordCount: 0,
          safeToRetireLegacyKeys: true,
          reauthenticationRequired: true,
          reauthenticationSatisfied: true,
          reauthenticationExpiresAt: '2026-04-15T10:25:30Z',
          keyUsage: []
        })
      })
    const { result, pushNotification } = renderController()

    act(() => {
      result.current.setSession({ id: 1, role: 'ADMIN' })
    })

    let completed = null
    await act(async () => {
      completed = await result.current.handleVerifySecretManagementPasskey()
    })

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/admin/secret-management/re-auth/passkey/options', {
      method: 'POST'
    })
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/admin/secret-management/re-auth/passkey/verify', expect.objectContaining({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }))
    expect(completed).toEqual(expect.objectContaining({
      reauthenticationRequired: true,
      reauthenticationSatisfied: true
    }))
    expect(result.current.secretManagementStatus.reauthenticationSatisfied).toBe(true)
    expect(pushNotification).toHaveBeenCalledWith(expect.objectContaining({
      message: { kind: 'translation', key: 'notifications.secretManagementReauthenticationVerified', params: {} },
      targetId: 'secret-management-section',
      tone: 'success'
    }))
  })
})
