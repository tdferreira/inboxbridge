export function jsonResponse(payload) {
  return {
    ok: true,
    status: 200,
    json: () => Promise.resolve(payload),
    text: () => Promise.resolve(JSON.stringify(payload))
  }
}

export function textError(message) {
  return {
    ok: false,
    status: 400,
    statusText: 'Bad Request',
    json: () => Promise.resolve({ message }),
    text: () => Promise.resolve(message)
  }
}

export function htmlError(status, statusText, html) {
  return {
    ok: false,
    status,
    statusText,
    json: () => Promise.resolve({}),
    text: () => Promise.resolve(html)
  }
}

export function clearLocalStorage() {
  if (typeof window.localStorage?.clear === 'function') {
    window.localStorage.clear()
    return
  }
  Object.keys(window.localStorage || {}).forEach((key) => {
    delete window.localStorage[key]
  })
}

export function createWorkspaceRouteFetch({ session, uiPreferences = {}, sessionActivityResponses = null }) {
  let storedUiPreferences = { ...uiPreferences }
  let sessionActivityIndex = 0

  return vi.fn(async (input, init = {}) => {
    const url = String(input)
    const method = init.method || 'GET'

    if (url === '/api/auth/options') {
      return jsonResponse({
        multiUserEnabled: true,
        microsoftOAuthAvailable: true,
        googleOAuthAvailable: true,
        sourceOAuthProviders: ['MICROSOFT', 'GOOGLE']
      })
    }
    if (url === '/api/auth/me') {
      return jsonResponse(session)
    }
    if (url === '/api/app/destination-config' || url === '/api/app/gmail-config') {
      return jsonResponse({
        provider: 'GMAIL_API',
        linked: false,
        oauthConnected: false,
        destinationUser: 'me',
        redirectUri: 'https://localhost:3000/api/google-oauth/callback',
        googleRedirectUri: 'https://localhost:3000/api/google-oauth/callback',
        createMissingLabels: true,
        neverMarkSpam: false,
        processForCalendar: false,
        sharedGoogleClientConfigured: true,
        secureStorageConfigured: true
      })
    }
    if (url === '/api/app/polling-settings') {
      return jsonResponse({
        defaultPollEnabled: true,
        pollEnabledOverride: null,
        effectivePollEnabled: true,
        defaultPollInterval: '5m',
        pollIntervalOverride: null,
        effectivePollInterval: '5m',
        defaultFetchWindow: 50,
        fetchWindowOverride: null,
        effectiveFetchWindow: 50
      })
    }
    if (url === '/api/app/polling-stats') {
      return jsonResponse({
        totalImportedMessages: 0,
        configuredMailFetchers: 0,
        enabledMailFetchers: 0,
        sourcesWithErrors: 0,
        importsByDay: [],
        importTimelines: {},
        duplicateTimelines: {},
        errorTimelines: {},
        health: { activeMailFetchers: 0, coolingDownMailFetchers: 0, failingMailFetchers: 0, disabledMailFetchers: 0 },
        providerBreakdown: [],
        manualRuns: 0,
        scheduledRuns: 0,
        averagePollDurationMillis: 0
      })
    }
    if (url === '/api/app/email-accounts') {
      return jsonResponse([])
    }
    if (url === '/api/app/ui-preferences') {
      if (method === 'PUT') {
        storedUiPreferences = {
          ...storedUiPreferences,
          ...JSON.parse(init.body || '{}')
        }
      }
      return jsonResponse(storedUiPreferences)
    }
    if (url === '/api/account/passkeys') {
      return jsonResponse([])
    }
    if (url === '/api/account/sessions') {
      if (sessionActivityResponses?.length) {
        const response = sessionActivityResponses[Math.min(sessionActivityIndex, sessionActivityResponses.length - 1)]
        sessionActivityIndex += 1
        return jsonResponse(response)
      }
      return jsonResponse({
        recentLogins: [],
        activeSessions: [],
        geoIpConfigured: false
      })
    }

    if (session.role === 'ADMIN') {
      if (url === '/api/admin/oauth-app-settings') {
        return jsonResponse({
          effectiveMultiUserEnabled: true,
          multiUserEnabledOverride: null,
          googleDestinationUser: 'me',
          googleRedirectUri: 'https://localhost:3000/api/google-oauth/callback',
          googleClientId: '',
          googleClientSecretConfigured: false,
          googleRefreshTokenConfigured: false,
          microsoftClientId: '',
          microsoftRedirectUri: 'https://localhost:3000/api/microsoft-oauth/callback',
          microsoftClientSecretConfigured: false,
          secureStorageConfigured: true
        })
      }
      if (url === '/api/admin/auth-security-settings') {
        return jsonResponse({
          defaultLoginFailureThreshold: 5,
          loginFailureThresholdOverride: null,
          effectiveLoginFailureThreshold: 5,
          defaultLoginInitialBlock: 'PT5M',
          loginInitialBlockOverride: null,
          effectiveLoginInitialBlock: 'PT5M',
          defaultLoginMaxBlock: 'PT1H',
          loginMaxBlockOverride: null,
          effectiveLoginMaxBlock: 'PT1H',
          defaultRegistrationChallengeEnabled: true,
          registrationChallengeEnabledOverride: null,
          effectiveRegistrationChallengeEnabled: true,
          defaultRegistrationChallengeTtl: 'PT10M',
          registrationChallengeTtlOverride: null,
          effectiveRegistrationChallengeTtl: 'PT10M',
          defaultGeoIpEnabled: false,
          geoIpEnabledOverride: null,
          effectiveGeoIpEnabled: false,
          defaultGeoIpPrimaryProvider: 'IPWHOIS',
          geoIpPrimaryProviderOverride: null,
          effectiveGeoIpPrimaryProvider: 'IPWHOIS',
          defaultGeoIpFallbackProviders: 'IPAPI_CO,IP_API,IPINFO_LITE',
          geoIpFallbackProvidersOverride: null,
          effectiveGeoIpFallbackProviders: 'IPAPI_CO,IP_API,IPINFO_LITE',
          defaultGeoIpCacheTtl: 'PT720H',
          geoIpCacheTtlOverride: null,
          effectiveGeoIpCacheTtl: 'PT720H',
          defaultGeoIpProviderCooldown: 'PT5M',
          geoIpProviderCooldownOverride: null,
          effectiveGeoIpProviderCooldown: 'PT5M',
          defaultGeoIpRequestTimeout: 'PT3S',
          geoIpRequestTimeoutOverride: null,
          effectiveGeoIpRequestTimeout: 'PT3S',
          availableGeoIpProviders: 'IPWHOIS, IPAPI_CO, IP_API, IPINFO_LITE',
          geoIpIpinfoTokenConfigured: false,
          secureStorageConfigured: true
        })
      }
      if (url === '/api/admin/dashboard') {
        return jsonResponse({
          overall: {
            configuredSources: 0,
            enabledSources: 0,
            totalImportedMessages: 0,
            sourcesWithErrors: 0,
            pollInterval: '5m',
            fetchWindow: 50
          },
          stats: {
            totalImportedMessages: 0,
            configuredMailFetchers: 0,
            enabledMailFetchers: 0,
            sourcesWithErrors: 0,
            importsByDay: [],
            importTimelines: {},
            duplicateTimelines: {},
            errorTimelines: {},
            health: { activeMailFetchers: 0, coolingDownMailFetchers: 0, failingMailFetchers: 0, disabledMailFetchers: 0 },
            providerBreakdown: [],
            manualRuns: 0,
            scheduledRuns: 0,
            averagePollDurationMillis: 0
          },
          polling: {
            defaultPollEnabled: true,
            pollEnabledOverride: null,
            effectivePollEnabled: true,
            defaultPollInterval: '5m',
            pollIntervalOverride: null,
            effectivePollInterval: '5m',
            defaultFetchWindow: 50,
            fetchWindowOverride: null,
            effectiveFetchWindow: 50
          },
          emailAccounts: [],
          recentEvents: []
        })
      }
      if (url === '/api/admin/users') {
        return jsonResponse([])
      }
    }

    throw new Error(`Unexpected request: ${method} ${url}`)
  })
}
