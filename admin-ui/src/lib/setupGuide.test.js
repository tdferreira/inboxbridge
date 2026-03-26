import { buildSetupGuideState } from './setupGuide'

describe('buildSetupGuideState', () => {
  it('reports complete, pending, and error states from live bridge and gmail data', () => {
    const result = buildSetupGuideState({
      gmailMeta: { refreshTokenConfigured: true },
      myBridges: [{
        bridgeId: 'outlook-main',
        authMethod: 'OAUTH2',
        oauthRefreshTokenConfigured: true,
        tokenStorageMode: 'DATABASE',
        totalImportedMessages: 0,
        lastEvent: { status: 'ERROR', error: 'consent_required' }
      }],
      session: { role: 'ADMIN', mustChangePassword: false },
      systemDashboard: null
    })

    expect(result.steps[0].status).toBe('complete')
    expect(result.steps[1].status).toBe('complete')
    expect(result.steps[2].status).toBe('complete')
    expect(result.steps[3].status).toBe('error')
    expect(result.steps[4].status).toBe('error')
    expect(result.allStepsComplete).toBe(false)
  })
})
