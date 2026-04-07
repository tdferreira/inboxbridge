import { render } from '@testing-library/react'
import AuthSecuritySection from '@/features/admin/components/AuthSecuritySection'
import OAuthAppsSection from '@/features/admin/components/OAuthAppsSection'
import SystemDashboardSection from '@/features/admin/components/SystemDashboardSection'
import UserManagementSection from '@/features/admin/components/UserManagementSection'
import DestinationMailboxSection from '@/features/destination/components/DestinationMailboxSection'
import UserEmailAccountsSection from '@/features/email-accounts/components/UserEmailAccountsSection'
import SetupGuidePanel from './SetupGuidePanel'
import UserPollingSettingsSection from '@/features/polling/components/UserPollingSettingsSection'
import PollingStatisticsSection from '@/features/stats/components/PollingStatisticsSection'

const t = (key) => key

const sectionCases = [
  {
    name: 'SetupGuidePanel',
    renderSection: () => (
      <SetupGuidePanel
        collapsed
        dismissable={false}
        onDismiss={vi.fn()}
        onFocusSection={vi.fn()}
        onToggleCollapse={vi.fn()}
        savingLayout={false}
        steps={[]}
        t={t}
      />
    )
  },
  {
    name: 'DestinationMailboxSection',
    renderSection: () => (
      <DestinationMailboxSection
        collapsed
        collapseLoading={false}
        destinationConfig={{ provider: 'GMAIL_API' }}
        destinationMeta={null}
        isAdmin={false}
        locale="en"
        onCollapseToggle={vi.fn()}
        onSave={vi.fn()}
        onSaveAndAuthenticate={vi.fn()}
        onTestConnection={vi.fn()}
        onUnlinkOAuth={vi.fn()}
        saveLoading={false}
        t={t}
        unlinkLoading={false}
      />
    )
  },
  {
    name: 'UserPollingSettingsSection',
    renderSection: () => (
      <UserPollingSettingsSection
        collapsed
        collapseLoading={false}
        hasFetchers={false}
        onCollapseToggle={vi.fn()}
        onOpenEditor={vi.fn()}
        onRunPoll={vi.fn()}
        pollingSettings={null}
        t={t}
      />
    )
  },
  {
    name: 'PollingStatisticsSection',
    renderSection: () => (
      <PollingStatisticsSection
        collapsed
        collapseLoading={false}
        copy="copy"
        id="stats-section"
        onCollapseToggle={vi.fn()}
        stats={null}
        t={t}
        title="title"
      />
    )
  },
  {
    name: 'UserEmailAccountsSection',
    renderSection: () => (
      <UserEmailAccountsSection
        collapsed
        collapseLoading={false}
        connectingEmailAccountId={null}
        deletingEmailAccountId={null}
        duplicateIdError=""
        emailAccountFolders={[]}
        emailAccountFoldersLoading={false}
        emailAccountForm={{}}
        fetcherDialogOpen={false}
        fetcherPollLoadingIds={[]}
        fetcherPollingDialog={null}
        fetcherPollingForm={{}}
        fetcherPollingLoading={false}
        fetcherRefreshLoadingId={null}
        fetcherStatsById={{}}
        fetcherStatsLoadingId={null}
        fetchers={[]}
        locale="en"
        onAddFetcher={vi.fn()}
        onApplyPreset={vi.fn()}
        onCloseDialog={vi.fn()}
        onClosePollingDialog={vi.fn()}
        onCollapseToggle={vi.fn()}
        onConfigureFetcherPolling={vi.fn()}
        onConnectOAuth={vi.fn()}
        onDeleteEmailAccount={vi.fn()}
        onEditEmailAccount={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onFetcherPollingFormChange={vi.fn()}
        onFetcherToggleExpand={vi.fn()}
        onLoadFetcherCustomRange={vi.fn()}
        onResetFetcherPollingSettings={vi.fn()}
        onRunFetcherPoll={vi.fn()}
        onSaveEmailAccount={vi.fn()}
        onSaveEmailAccountAndConnectOAuth={vi.fn()}
        onSaveFetcherPollingSettings={vi.fn()}
        onTestEmailAccountConnection={vi.fn()}
        saveLoading={false}
        t={t}
      />
    )
  },
  {
    name: 'SystemDashboardSection',
    renderSection: () => (
      <SystemDashboardSection
        collapsed
        collapseLoading={false}
        dashboard={null}
        locale="en"
        onCollapseToggle={vi.fn()}
        onOpenEditor={vi.fn()}
        onRunPoll={vi.fn()}
        runningPoll={false}
        t={t}
      />
    )
  },
  {
    name: 'OAuthAppsSection',
    renderSection: () => (
      <OAuthAppsSection
        collapsed
        collapseLoading={false}
        oauthSettings={null}
        onCollapseToggle={vi.fn()}
        onEditGoogle={vi.fn()}
        onEditMicrosoft={vi.fn()}
        t={t}
      />
    )
  },
  {
    name: 'AuthSecuritySection',
    renderSection: () => (
      <AuthSecuritySection
        authSecuritySettings={null}
        collapsed
        collapseLoading={false}
        locale="en"
        onCollapseToggle={vi.fn()}
        onOpenEditor={vi.fn()}
        t={t}
      />
    )
  },
  {
    name: 'UserManagementSection',
    renderSection: () => (
      <UserManagementSection
        collapsed
        collapseLoading={false}
        createUserDialogOpen={false}
        createUserForm={{}}
        createUserLoading={false}
        duplicateUsername={false}
        expandedUserId={null}
        locale="en"
        modeToggleLoading={false}
        multiUserEnabled={false}
        onCloseCreateUserDialog={vi.fn()}
        onCollapseToggle={vi.fn()}
        onCreateUser={vi.fn()}
        onCreateUserFormChange={vi.fn()}
        onDeleteUser={vi.fn()}
        onForcePasswordChange={vi.fn()}
        onLoadUserCustomRange={vi.fn()}
        onOpenCreateUserDialog={vi.fn()}
        onOpenResetPasswordDialog={vi.fn()}
        onResetUserPasskeys={vi.fn()}
        onToggleExpandUser={vi.fn()}
        onToggleMultiUserEnabled={vi.fn()}
        onToggleUserActive={vi.fn()}
        onUpdateUser={vi.fn()}
        selectedUserConfig={null}
        selectedUserLoading={false}
        session={{ id: 1, role: 'ADMIN' }}
        t={t}
        updatingPasskeysResetUserId={null}
        updatingUserId={null}
        users={[]}
      />
    )
  }
]

describe('workspace section shell audit', () => {
  it.each(sectionCases)('%s renders through the shared CollapsibleSection shell', ({ renderSection }) => {
    const { container } = render(renderSection())

    expect(container.querySelector('.section-card-shell')).toBeInTheDocument()
    expect(container.querySelector('.section-with-corner-toggle')).toBeInTheDocument()
  })
})
