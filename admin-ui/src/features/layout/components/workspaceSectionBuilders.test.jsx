import { render, screen } from '@testing-library/react'
import { buildAdminWorkspaceSections, buildUserWorkspaceSections } from './workspaceSectionBuilders'

vi.mock('@/features/admin/components/AuthSecuritySection', () => ({
  default: (props) => <div data-component="AuthSecuritySection" data-collapsed={String(props.collapsed)} />
}))
vi.mock('@/features/admin/components/OAuthAppsSection', () => ({
  default: (props) => <button onClick={props.onEditGoogle} type="button">OAuthAppsSection</button>
}))
vi.mock('@/features/admin/components/SystemDashboardSection', () => ({
  default: (props) => <button onClick={props.onRunPoll} type="button">SystemDashboardSection</button>
}))
vi.mock('@/features/admin/components/UserManagementSection', () => ({
  default: (props) => <div data-component="UserManagementSection" data-users={props.users?.length || 0} />
}))
vi.mock('@/features/destination/components/DestinationMailboxSection', () => ({
  default: (props) => <button onClick={props.onCollapseToggle} type="button">DestinationMailboxSection</button>
}))
vi.mock('@/features/email-accounts/components/UserEmailAccountsSection', () => ({
  default: (props) => <div data-component="UserEmailAccountsSection" data-fetchers={props.fetchers?.length || 0} />
}))
vi.mock('@/features/polling/components/UserPollingSettingsSection', () => ({
  default: (props) => <button onClick={props.onRunPoll} type="button">UserPollingSettingsSection</button>
}))
vi.mock('@/features/stats/components/PollingStatisticsSection', () => ({
  default: (props) => <div data-component="PollingStatisticsSection" data-title={props.title} />
}))
vi.mock('./RemoteControlLaunchSection', () => ({
  default: () => <div data-component="RemoteControlLaunchSection" />
}))
vi.mock('./SetupGuidePanel', () => ({
  default: (props) => <button onClick={props.onDismiss} type="button">SetupGuidePanel</button>
}))

describe('workspaceSectionBuilders', () => {
  function createCommonArgs() {
    return {
      authOptions: {
        multiUserEnabled: true,
        sourceOAuthProviders: ['google']
      },
      dismissQuickSetupGuide: vi.fn(),
      focusSection: vi.fn(),
      isPending: vi.fn(() => false),
      isSectionRefreshing: vi.fn(() => false),
      language: 'en',
      t: (key) => key,
      toggleWorkspaceSection: vi.fn(),
      uiPreferences: {
        persistLayout: true,
        quickSetupDismissed: false,
        quickSetupPinnedVisible: false,
        quickSetupCollapsed: false,
        destinationMailboxCollapsed: false,
        userPollingCollapsed: false,
        userStatsCollapsed: false,
        sourceEmailAccountsCollapsed: false,
        adminQuickSetupDismissed: false,
        adminQuickSetupPinnedVisible: false,
        adminQuickSetupCollapsed: false,
        systemDashboardCollapsed: false,
        oauthAppsCollapsed: false,
        authSecurityCollapsed: false,
        globalStatsCollapsed: false,
        userManagementCollapsed: false
      }
    }
  }

  it('builds the user workspace sections in the expected order', () => {
    const common = createCommonArgs()

    const sections = buildUserWorkspaceSections({
      ...common,
      destination: {
        saveDestinationConfig: vi.fn(),
        saveDestinationConfigAndAuthenticate: vi.fn(),
        startDestinationOAuth: vi.fn(),
        testDestinationConnection: vi.fn(),
        unlinkDestinationAccount: vi.fn()
      },
      destinationConfig: { provider: 'GMAIL_API' },
      destinationFolders: [],
      destinationFoldersLoading: false,
      destinationMeta: null,
      emailAccounts: {
        runnableUserEmailAccounts: [],
        fetcherPollLoadingIds: [],
        visibleFetchers: []
      },
      loadUserCustomRange: vi.fn(),
      openConfirmation: vi.fn(),
      polling: {
        livePoll: null,
        openUserPollingDialog: vi.fn(),
        pauseLivePoll: vi.fn(),
        resumeLivePoll: vi.fn(),
        runUserPoll: vi.fn(),
        stopLivePoll: vi.fn(),
        runningUserPoll: false,
        userPollingSettings: null
      },
      session: { username: 'alice' },
      setDestinationConfig: vi.fn(),
      userPollingStats: null,
      userSetupGuideState: {
        allStepsComplete: false,
        steps: []
      }
    })

    expect(sections.map((section) => section.id)).toEqual([
      'quickSetup',
      'destination',
      'userPolling',
      'remoteControl',
      'userStats',
      'sourceEmailAccounts'
    ])
  })

  it('wires user section actions through the section descriptors', () => {
    const common = createCommonArgs()
    const toggleWorkspaceSection = common.toggleWorkspaceSection
    const runUserPoll = vi.fn()

    const sections = buildUserWorkspaceSections({
      ...common,
      destination: {
        saveDestinationConfig: vi.fn(),
        saveDestinationConfigAndAuthenticate: vi.fn(),
        startDestinationOAuth: vi.fn(),
        testDestinationConnection: vi.fn(),
        unlinkDestinationAccount: vi.fn()
      },
      destinationConfig: { provider: 'GMAIL_API' },
      destinationFolders: [],
      destinationFoldersLoading: false,
      destinationMeta: null,
      emailAccounts: {
        runnableUserEmailAccounts: [],
        fetcherPollLoadingIds: [],
        visibleFetchers: []
      },
      loadUserCustomRange: vi.fn(),
      openConfirmation: vi.fn(),
      polling: {
        livePoll: null,
        openUserPollingDialog: vi.fn(),
        pauseLivePoll: vi.fn(),
        resumeLivePoll: vi.fn(),
        runUserPoll,
        stopLivePoll: vi.fn(),
        runningUserPoll: false,
        userPollingSettings: null
      },
      session: { username: 'alice' },
      setDestinationConfig: vi.fn(),
      userPollingStats: null,
      userSetupGuideState: {
        allStepsComplete: false,
        steps: []
      }
    })

    render(sections[1].render())
    render(sections[2].render())

    screen.getByRole('button', { name: 'DestinationMailboxSection' }).click()
    screen.getByRole('button', { name: 'UserPollingSettingsSection' }).click()

    expect(toggleWorkspaceSection).toHaveBeenCalledWith('destinationMailboxCollapsed')
    expect(runUserPoll).toHaveBeenCalledTimes(1)
  })

  it('builds the admin workspace sections in the expected order and wires editor actions', () => {
    const common = createCommonArgs()
    const setShowSystemOAuthAppsDialog = vi.fn()
    const setSystemOAuthEditorProvider = vi.fn()
    const setSystemOAuthSettingsDirty = vi.fn()

    const sections = buildAdminWorkspaceSections({
      ...common,
      adminSetupGuideState: {
        allStepsComplete: false,
        steps: []
      },
      authSecuritySettings: null,
      globalStatsNeedsAttention: false,
      loadAdminUserCustomRange: vi.fn(),
      loadGlobalCustomRange: vi.fn(),
      polling: {
        livePoll: null,
        openSystemPollingDialog: vi.fn(),
        runAllUsersPoll: vi.fn(),
        runningAllUsersPoll: false
      },
      session: { role: 'ADMIN' },
      setShowAuthSecurityDialog: vi.fn(),
      setShowSystemOAuthAppsDialog,
      setSystemOAuthEditorProvider,
      setSystemOAuthSettingsDirty,
      systemDashboard: null,
      systemOAuthSettings: null,
      userManagement: {
        users: []
      }
    })

    expect(sections.map((section) => section.id)).toEqual([
      'adminQuickSetup',
      'systemDashboard',
      'oauthApps',
      'authSecurity',
      'globalStats',
      'userManagement'
    ])

    render(sections[2].render())
    screen.getByRole('button', { name: 'OAuthAppsSection' }).click()

    expect(setSystemOAuthSettingsDirty).toHaveBeenCalledWith(false)
    expect(setSystemOAuthEditorProvider).toHaveBeenCalledWith('google')
    expect(setShowSystemOAuthAppsDialog).toHaveBeenCalledWith(true)
  })
})
