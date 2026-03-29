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
      ['destination', 'userPolling', 'sourceEmailAccounts'],
      ['sourceEmailAccounts', 'destination']
    )).toEqual(['sourceEmailAccounts', 'destination', 'userPolling'])
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