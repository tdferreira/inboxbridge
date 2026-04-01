import {
  applyLayoutPreferences,
  applyOrderedSectionIds,
  captureLayoutPreferences,
  hasLayoutPreferenceChanges,
  normalizeUiPreferences
} from './workspacePreferences'

describe('workspacePreferences', () => {
  it('normalizes legacy section ids and collapsed flags', () => {
    const normalized = normalizeUiPreferences({
      persistLayout: true,
      gmailDestinationCollapsed: true,
      sourceBridgesCollapsed: true,
      userSectionOrder: ['quickSetup', 'gmail', 'sourceBridges']
    })

    expect(normalized.destinationMailboxCollapsed).toBe(true)
    expect(normalized.sourceEmailAccountsCollapsed).toBe(true)
    expect(normalized.userSectionOrder).toEqual(['quickSetup', 'destination', 'sourceEmailAccounts'])
  })

  it('appends missing sections after preferred ids', () => {
    expect(applyOrderedSectionIds(
      ['destination', 'userPolling', 'sourceEmailAccounts', 'remoteControl'],
      ['sourceEmailAccounts', 'destination']
    )).toEqual(['sourceEmailAccounts', 'destination', 'userPolling', 'remoteControl'])
  })

  it('defaults user workspace order with the remote control section included', () => {
    const normalized = normalizeUiPreferences({})

    expect(normalized.userSectionOrder).toEqual([
      'quickSetup',
      'destination',
      'sourceEmailAccounts',
      'userPolling',
      'remoteControl',
      'userStats'
    ])
  })

  it('defaults admin workspace order to the administration-specific sequence', () => {
    const normalized = normalizeUiPreferences({})

    expect(normalized.adminSectionOrder).toEqual([
      'adminQuickSetup',
      'systemDashboard',
      'oauthApps',
      'userManagement',
      'authSecurity',
      'globalStats'
    ])
  })

  it('keeps admin quick setup visibility preferences separate from the user workspace guide', () => {
    const normalized = normalizeUiPreferences({
      persistLayout: true,
      quickSetupDismissed: true,
      quickSetupPinnedVisible: true,
      adminQuickSetupDismissed: false,
      adminQuickSetupPinnedVisible: true
    })

    expect(normalized.quickSetupDismissed).toBe(true)
    expect(normalized.quickSetupPinnedVisible).toBe(true)
    expect(normalized.adminQuickSetupDismissed).toBe(false)
    expect(normalized.adminQuickSetupPinnedVisible).toBe(true)
  })

  it('drops transient quick setup visibility preferences when layout persistence is disabled', () => {
    const normalized = normalizeUiPreferences({
      persistLayout: false,
      quickSetupDismissed: true,
      quickSetupPinnedVisible: true,
      adminQuickSetupDismissed: true,
      adminQuickSetupPinnedVisible: true
    })

    expect(normalized.quickSetupDismissed).toBe(false)
    expect(normalized.quickSetupPinnedVisible).toBe(false)
    expect(normalized.adminQuickSetupDismissed).toBe(false)
    expect(normalized.adminQuickSetupPinnedVisible).toBe(false)
  })

  it('captures and reapplies only layout-specific preferences', () => {
    const snapshot = captureLayoutPreferences({
      destinationMailboxCollapsed: true,
      language: 'pt-BR',
      userSectionOrder: ['sourceEmailAccounts', 'destination']
    })

    expect(snapshot).toEqual(expect.objectContaining({
      destinationMailboxCollapsed: true,
      userSectionOrder: ['sourceEmailAccounts', 'destination']
    }))

    expect(applyLayoutPreferences({
      destinationMailboxCollapsed: false,
      language: 'en',
      userSectionOrder: ['destination', 'sourceEmailAccounts']
    }, snapshot)).toEqual(expect.objectContaining({
      destinationMailboxCollapsed: true,
      language: 'en',
      userSectionOrder: ['sourceEmailAccounts', 'destination']
    }))
  })

  it('detects when the current layout differs from the saved snapshot', () => {
    const snapshot = captureLayoutPreferences({
      adminSectionOrder: ['adminQuickSetup', 'systemDashboard'],
      quickSetupCollapsed: false
    })

    expect(hasLayoutPreferenceChanges({
      adminSectionOrder: ['adminQuickSetup', 'systemDashboard'],
      quickSetupCollapsed: false
    }, snapshot)).toBe(false)

    expect(hasLayoutPreferenceChanges({
      adminSectionOrder: ['systemDashboard', 'adminQuickSetup'],
      quickSetupCollapsed: false
    }, snapshot)).toBe(true)
  })
})
