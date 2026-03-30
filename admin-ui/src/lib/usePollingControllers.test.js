import { act, renderHook } from '@testing-library/react'
import { usePollingControllers } from './usePollingControllers'

describe('usePollingControllers', () => {
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
    const errorText = vi.fn((key) => key)

    const hook = renderHook((props) => usePollingControllers(props), {
      initialProps: {
        authOptions: { multiUserEnabled: true },
        closeConfirmation,
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
      openConfirmation,
      pushNotification,
      withPending
    }
  }

  it('hydrates user polling settings while the form is pristine', () => {
    const { result } = renderController()

    act(() => {
      result.current.applyLoadedUserPolling({
        pollEnabledOverride: true,
        pollIntervalOverride: '10m',
        fetchWindowOverride: 25
      })
    })

    expect(result.current.userPollingForm).toEqual({
      pollEnabledMode: 'ENABLED',
      pollIntervalOverride: '10m',
      fetchWindowOverride: '25'
    })
  })

  it('does not overwrite a dirty user polling form with loaded data', () => {
    const { result } = renderController()

    act(() => {
      result.current.handleUserPollingFormChange({
        pollEnabledMode: 'DISABLED',
        pollIntervalOverride: '2m',
        fetchWindowOverride: '12'
      })
    })

    act(() => {
      result.current.applyLoadedUserPolling({
        pollEnabledOverride: null,
        pollIntervalOverride: '10m',
        fetchWindowOverride: 25
      })
    })

    expect(result.current.userPollingForm).toEqual({
      pollEnabledMode: 'DISABLED',
      pollIntervalOverride: '2m',
      fetchWindowOverride: '12'
    })
  })

  it('opens a global poll confirmation with the multi-user copy', async () => {
    const { result, openConfirmation } = renderController({
      authOptions: { multiUserEnabled: true }
    })

    await act(async () => {
      await result.current.runPoll()
    })

    expect(openConfirmation).toHaveBeenCalledWith(expect.objectContaining({
      actionKey: 'runPoll',
      title: 'system.runPollConfirmTitle'
    }))
  })

  it('stores poll errors as localizable notification descriptors', async () => {
    fetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        fetched: 0,
        imported: 0,
        duplicates: 0,
        errors: [],
        errorDetails: [
          {
            code: 'oauth_refresh_token_missing',
            sourceId: 'outlook-main',
            message: 'Source outlook-main is configured for OAuth2 but has no refresh token.'
          }
        ],
        spamJunkMessageCount: 0
      })
    })

    const { result, pushNotification } = renderController()

    await act(async () => {
      await result.current.runUserPoll()
    })

    expect(pushNotification).toHaveBeenLastCalledWith(expect.objectContaining({
      copyText: expect.objectContaining({
        kind: 'pollError',
        value: expect.objectContaining({
          code: 'oauth_refresh_token_missing',
          sourceId: 'outlook-main'
        })
      }),
      message: expect.objectContaining({
        kind: 'pollError',
        value: expect.objectContaining({
          code: 'oauth_refresh_token_missing',
          sourceId: 'outlook-main'
        })
      }),
      targetId: 'source-email-account-outlook-main',
      tone: 'error'
    }))
  })
})
