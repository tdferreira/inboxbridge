import { buildSetupGuideState } from './setupGuide'
import { translate } from './i18n'

const t = (key, params) => translate('en', key, params)

describe('buildSetupGuideState', () => {
  it('reports complete, pending, and error states from live bridge and gmail data', () => {
    const result = buildSetupGuideState({
      destinationMeta: { linked: true },
      myEmailAccounts: [{
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
      destinationMeta: { linked: true },
      myEmailAccounts: [],
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

  it('reports the full guide complete when all meaningful setup steps are finished', () => {
    const result = buildSetupGuideState({
      destinationMeta: { linked: true },
      myEmailAccounts: [{
        bridgeId: 'source-main',
        authMethod: 'PASSWORD',
        tokenStorageMode: 'PASSWORD',
        totalImportedMessages: 2,
        lastEvent: { status: 'SUCCESS', error: null }
      }],
      session: { username: 'alice', role: 'USER', mustChangePassword: false },
      systemDashboard: {
        overall: {
          totalImportedMessages: 2
        }
      },
      t
    })

    expect(result.allStepsComplete).toBe(true)
  })

  it('renumbers the visible steps sequentially when the oauth step is omitted', () => {
    const result = buildSetupGuideState({
      destinationMeta: { linked: true },
      myEmailAccounts: [{
        bridgeId: 'source-main',
        authMethod: 'PASSWORD',
        tokenStorageMode: 'PASSWORD',
        totalImportedMessages: 2,
        lastEvent: { status: 'SUCCESS', error: null }
      }],
      session: { username: 'alice', role: 'USER', mustChangePassword: false },
      systemDashboard: {
        overall: {
          totalImportedMessages: 2
        }
      },
      t
    })

    expect(result.steps.map((step) => step.title)).toEqual([
      '1. Secure your session',
      '2. Connect your destination mailbox',
      '3. Add at least one email account',
      '4. Run and verify imports'
    ])
  })

  it('shows the admin guide without the user mail-account step', () => {
    const result = buildSetupGuideState({
      session: { id: 1, username: 'admin', role: 'ADMIN', mustChangePassword: false },
      systemOAuthSettings: {
        microsoftClientId: 'microsoft-client-id',
        microsoftClientSecretConfigured: true,
        effectiveMultiUserEnabled: true
      },
      users: [{ id: 1, username: 'admin' }],
      systemDashboard: {
        emailAccounts: [],
        overall: { totalImportedMessages: 0 }
      },
      workspace: 'admin',
      t
    })

    expect(result.steps.map((step) => step.title)).toEqual([
      '1. Configure shared OAuth apps',
      '2. Create a new user',
      '3. Verify the first successful import'
    ])
    expect(result.steps[1].targetId).toBe('user-management-section')
  })

  it('keeps the admin import step pending until shared OAuth is configured', () => {
    const result = buildSetupGuideState({
      session: { id: 1, username: 'admin', role: 'ADMIN', mustChangePassword: false },
      systemOAuthSettings: {
        effectiveMultiUserEnabled: false,
        googleClientId: '',
        googleClientSecretConfigured: false,
        googleRefreshTokenConfigured: false,
        microsoftClientId: '',
        microsoftClientSecretConfigured: false
      },
      systemDashboard: {
        emailAccounts: [{
          id: 'env-outlook',
          lastEvent: { status: 'SUCCESS', error: null }
        }],
        overall: { totalImportedMessages: 7 }
      },
      workspace: 'admin',
      t
    })

    expect(result.steps[0].status).toBe('pending')
    expect(result.steps[1].status).toBe('pending')
    expect(result.steps[1].description).toBe('Finish shared OAuth setup before using the first successful import as a readiness signal.')
  })
})
