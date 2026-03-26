import { buildSetupGuideState } from './setupGuide'
import { translate } from './i18n'

const t = (key, params) => translate('en', key, params)

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
      session: { username: 'admin', role: 'ADMIN', mustChangePassword: false },
      systemDashboard: null,
      t
    })

    expect(result.steps[0].status).toBe('complete')
    expect(result.steps[1].status).toBe('complete')
    expect(result.steps[2].status).toBe('complete')
    expect(result.steps[3].status).toBe('error')
    expect(result.steps[4].status).toBe('error')
    expect(result.allStepsComplete).toBe(false)
  })

  it('does not count env-managed fetchers for non-admin usernames', () => {
    const result = buildSetupGuideState({
      gmailMeta: { refreshTokenConfigured: true },
      myBridges: [],
      session: { username: 'alice', role: 'ADMIN', mustChangePassword: false },
      systemDashboard: {
        bridges: [{
          id: 'env-outlook',
          authMethod: 'PASSWORD',
          tokenStorageMode: 'PASSWORD',
          totalImportedMessages: 4,
          lastEvent: { status: 'SUCCESS', error: null }
        }],
        overall: {
          totalImportedMessages: 4
        }
      },
      t
    })

    expect(result.steps[2].status).toBe('pending')
  })
})
