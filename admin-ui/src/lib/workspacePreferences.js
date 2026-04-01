export const DEFAULT_UI_PREFERENCES = {
  persistLayout: false,
  layoutEditEnabled: false,
  quickSetupCollapsed: false,
  quickSetupDismissed: false,
  quickSetupPinnedVisible: false,
  adminQuickSetupDismissed: false,
  adminQuickSetupPinnedVisible: false,
  destinationMailboxCollapsed: false,
  userPollingCollapsed: false,
  userStatsCollapsed: false,
  sourceEmailAccountsCollapsed: false,
  adminQuickSetupCollapsed: false,
  systemDashboardCollapsed: false,
  oauthAppsCollapsed: false,
  authSecurityCollapsed: false,
  globalStatsCollapsed: false,
  userManagementCollapsed: false,
  userSectionOrder: ['quickSetup', 'destination', 'sourceEmailAccounts', 'userPolling', 'remoteControl', 'userStats'],
  adminSectionOrder: ['adminQuickSetup', 'systemDashboard', 'oauthApps', 'userManagement', 'authSecurity', 'globalStats'],
  language: 'en',
  notificationHistory: []
}

export const DEFAULT_ADMIN_WORKSPACE = 'user'

export const LAYOUT_PREFERENCE_KEYS = [
  'quickSetupCollapsed',
  'destinationMailboxCollapsed',
  'userPollingCollapsed',
  'userStatsCollapsed',
  'sourceEmailAccountsCollapsed',
  'adminQuickSetupCollapsed',
  'systemDashboardCollapsed',
  'oauthAppsCollapsed',
  'authSecurityCollapsed',
  'globalStatsCollapsed',
  'userManagementCollapsed',
  'userSectionOrder',
  'adminSectionOrder'
]

export function normalizeUiPreferences(payload) {
  const normalizedUserSectionOrder = Array.isArray(payload?.userSectionOrder)
    ? payload.userSectionOrder.map((sectionId) => {
      if (sectionId === 'gmail') return 'destination'
      if (sectionId === 'sourceBridges') return 'sourceEmailAccounts'
      return sectionId
    })
    : []
  const nextUiPreferences = {
    ...DEFAULT_UI_PREFERENCES,
    ...(payload || {}),
    destinationMailboxCollapsed: payload?.destinationMailboxCollapsed ?? payload?.gmailDestinationCollapsed ?? false,
    sourceEmailAccountsCollapsed: payload?.sourceEmailAccountsCollapsed ?? payload?.sourceBridgesCollapsed ?? false,
    adminQuickSetupDismissed: payload?.adminQuickSetupDismissed ?? false,
    adminQuickSetupPinnedVisible: payload?.adminQuickSetupPinnedVisible ?? false,
    userSectionOrder: normalizedUserSectionOrder
  }
  return {
    ...nextUiPreferences,
    ...(nextUiPreferences.persistLayout ? {} : {
      layoutEditEnabled: false,
      quickSetupCollapsed: false,
      quickSetupDismissed: false,
      quickSetupPinnedVisible: false,
      adminQuickSetupDismissed: false,
      adminQuickSetupPinnedVisible: false,
      destinationMailboxCollapsed: false,
      userPollingCollapsed: false,
      userStatsCollapsed: false,
      sourceEmailAccountsCollapsed: false,
      adminQuickSetupCollapsed: false,
      systemDashboardCollapsed: false,
      oauthAppsCollapsed: false,
      authSecurityCollapsed: false,
      globalStatsCollapsed: false,
      userManagementCollapsed: false
    }),
    userSectionOrder: normalizedUserSectionOrder.length
      ? [...normalizedUserSectionOrder]
      : [...DEFAULT_UI_PREFERENCES.userSectionOrder],
    adminSectionOrder: Array.isArray(nextUiPreferences.adminSectionOrder) && nextUiPreferences.adminSectionOrder.length
      ? [...nextUiPreferences.adminSectionOrder]
      : [...DEFAULT_UI_PREFERENCES.adminSectionOrder]
  }
}

export function applyOrderedSectionIds(availableIds, preferredIds) {
  const ordered = preferredIds.filter((sectionId) => availableIds.includes(sectionId))
  return [...ordered, ...availableIds.filter((sectionId) => !ordered.includes(sectionId))]
}

export function captureLayoutPreferences(preferences) {
  return LAYOUT_PREFERENCE_KEYS.reduce((snapshot, key) => {
    const value = preferences?.[key]
    snapshot[key] = Array.isArray(value) ? [...value] : value
    return snapshot
  }, {})
}

export function applyLayoutPreferences(preferences, snapshot) {
  if (!snapshot) {
    return preferences
  }
  return {
    ...preferences,
    ...captureLayoutPreferences(snapshot)
  }
}

export function hasLayoutPreferenceChanges(preferences, snapshot) {
  if (!snapshot) {
    return false
  }
  return LAYOUT_PREFERENCE_KEYS.some((key) => {
    const currentValue = preferences?.[key]
    const snapshotValue = snapshot[key]
    if (Array.isArray(currentValue) || Array.isArray(snapshotValue)) {
      return JSON.stringify(currentValue || []) !== JSON.stringify(snapshotValue || [])
    }
    return currentValue !== snapshotValue
  })
}
