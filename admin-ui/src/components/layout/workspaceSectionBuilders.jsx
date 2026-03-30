import AuthSecuritySection from '../admin/AuthSecuritySection'
import { Suspense, lazy } from 'react'
import OAuthAppsSection from '../admin/OAuthAppsSection'
import SystemDashboardSection from '../admin/SystemDashboardSection'
import UserManagementSection from '../admin/UserManagementSection'
import DestinationMailboxSection from '../destination/DestinationMailboxSection'
import UserEmailAccountsSection from '../emailAccounts/UserEmailAccountsSection'
import SetupGuidePanel from './SetupGuidePanel'
import UserPollingSettingsSection from '../polling/UserPollingSettingsSection'

const PollingStatisticsSection = lazy(() => import('../stats/PollingStatisticsSection'))

export function buildUserWorkspaceSections({
  authOptions,
  destination,
  destinationConfig,
  destinationFolders,
  destinationFoldersLoading,
  destinationMeta,
  dismissQuickSetupGuide,
  emailAccounts,
  focusSection,
  isPending,
  isSectionRefreshing,
  language,
  loadUserCustomRange,
  openConfirmation,
  polling,
  setDestinationConfig,
  t,
  toggleWorkspaceSection,
  uiPreferences,
  userPollingStats,
  userSetupGuideState
}) {
  return [
    {
      id: 'quickSetup',
      render: () => userSetupGuideState.allStepsComplete && uiPreferences.quickSetupDismissed ? null : (
        <SetupGuidePanel
          collapsed={uiPreferences.quickSetupCollapsed}
          dismissable={userSetupGuideState.allStepsComplete}
          onDismiss={dismissQuickSetupGuide}
          onFocusSection={focusSection}
          onToggleCollapse={() => toggleWorkspaceSection('quickSetupCollapsed')}
          savingLayout={isPending('uiPreferences')}
          sectionLoading={isSectionRefreshing('quickSetupCollapsed')}
          steps={userSetupGuideState.steps}
          t={t}
        />
      )
    },
    {
      id: 'destination',
      render: () => (
        <DestinationMailboxSection
          collapsed={uiPreferences.destinationMailboxCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          destinationConfig={destinationConfig}
          destinationFolders={destinationFolders}
          destinationFoldersLoading={destinationFoldersLoading}
          destinationMeta={destinationMeta}
          isAdmin={false}
          locale={language}
          oauthLoading={isPending('googleOAuthSelf') || isPending('microsoftDestinationOAuth')}
          onCollapseToggle={() => toggleWorkspaceSection('destinationMailboxCollapsed')}
          onConnectOAuth={destination.startDestinationOAuth}
          onSave={destination.saveDestinationConfig}
          onSaveAndAuthenticate={destination.saveDestinationConfigAndAuthenticate}
          onTestConnection={destination.testDestinationConnection}
          onUnlinkOAuth={destination.unlinkDestinationAccount}
          saveLoading={isPending('destinationSave')}
          sectionLoading={isSectionRefreshing('destinationMailboxCollapsed')}
          setDestinationConfig={setDestinationConfig}
          t={t}
          testConnectionLoading={isPending('destinationConnectionTest')}
          unlinkLoading={isPending('destinationUnlink')}
        />
      )
    },
    {
      id: 'userPolling',
      render: () => (
        <UserPollingSettingsSection
          collapsed={uiPreferences.userPollingCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          hasFetchers={emailAccounts.userEmailAccounts.length > 0}
          onCollapseToggle={() => toggleWorkspaceSection('userPollingCollapsed')}
          onOpenEditor={polling.openUserPollingDialog}
          onRunPoll={polling.runUserPoll}
          pollingSettings={polling.userPollingSettings}
          runningPoll={polling.runningUserPoll}
          sectionLoading={isSectionRefreshing('userPollingCollapsed')}
          t={t}
        />
      )
    },
    {
      id: 'userStats',
      render: () => (
        <Suspense fallback={<div className="muted-box">{t('common.refreshingSection')}</div>}>
          <PollingStatisticsSection
            collapsed={uiPreferences.userStatsCollapsed}
            collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
            copy={t('pollingStats.userCopy')}
            customRangeLoader={loadUserCustomRange}
            id="user-polling-stats-section"
            onCollapseToggle={() => toggleWorkspaceSection('userStatsCollapsed')}
            sectionLoading={isSectionRefreshing('userPollingCollapsed')}
            stats={userPollingStats}
            t={t}
            title={t('pollingStats.userTitle')}
          />
        </Suspense>
      )
    },
    {
      id: 'sourceEmailAccounts',
      render: () => (
        <UserEmailAccountsSection
          availableOAuthProviders={authOptions.sourceOAuthProviders}
          collapsed={uiPreferences.sourceEmailAccountsCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          connectingEmailAccountId={emailAccounts.connectingEmailAccountId}
          deletingEmailAccountId={emailAccounts.deletingEmailAccountId}
          duplicateIdError={emailAccounts.emailAccountDuplicateError}
          emailAccountForm={emailAccounts.emailAccountForm}
          emailAccountFolders={emailAccounts.emailAccountFolders}
          emailAccountFoldersLoading={emailAccounts.emailAccountFoldersLoading}
          fetcherDialogOpen={emailAccounts.showFetcherDialog}
          fetcherPollLoadingId={emailAccounts.fetcherPollLoadingId}
          fetcherPollingDialog={emailAccounts.fetcherPollingTarget}
          fetcherPollingForm={emailAccounts.fetcherPollingForm}
          fetcherPollingLoading={emailAccounts.fetcherPollingLoading}
          fetcherRefreshLoadingId={emailAccounts.expandedFetcherLoadingId}
          fetcherStatsById={emailAccounts.fetcherStatsById}
          fetcherStatsLoadingId={emailAccounts.fetcherStatsLoadingId}
          fetchers={emailAccounts.visibleFetchers}
          locale={language}
          onAddFetcher={emailAccounts.openAddFetcherDialog}
          onApplyPreset={emailAccounts.applyEmailAccountPreset}
          onCloseDialog={emailAccounts.closeFetcherDialog}
          onClosePollingDialog={emailAccounts.closeFetcherPollingDialog}
          onCollapseToggle={() => toggleWorkspaceSection('sourceEmailAccountsCollapsed')}
          onConfigureFetcherPolling={emailAccounts.openFetcherPollingDialog}
          onConnectOAuth={emailAccounts.startSourceOAuth}
          onDeleteEmailAccount={(emailAccountId) => emailAccounts.deleteEmailAccount(emailAccountId, openConfirmation)}
          onEditEmailAccount={emailAccounts.editEmailAccount}
          onEmailAccountFormChange={emailAccounts.handleEmailAccountFormChange}
          onFetcherPollingFormChange={emailAccounts.handleFetcherPollingFormChange}
          onFetcherToggleExpand={emailAccounts.refreshFetcherState}
          onLoadFetcherCustomRange={emailAccounts.loadFetcherCustomRange}
          onResetFetcherPollingSettings={emailAccounts.resetFetcherPollingSettings}
          onRunFetcherPoll={emailAccounts.runFetcherPoll}
          onSaveEmailAccount={emailAccounts.saveEmailAccount}
          onSaveEmailAccountAndConnectOAuth={emailAccounts.saveEmailAccountAndConnectOAuth}
          onSaveFetcherPollingSettings={emailAccounts.saveFetcherPollingSettings}
          onTestEmailAccountConnection={emailAccounts.testEmailAccountConnection}
          saveAndConnectLoading={isPending('bridgeSaveConnect')}
          saveLoading={isPending('bridgeSave')}
          sectionLoading={isSectionRefreshing('sourceEmailAccountsCollapsed')}
          t={t}
          testConnectionLoading={isPending('bridgeConnectionTest')}
          testResult={emailAccounts.emailAccountTestResult}
        />
      )
    }
  ]
}

export function buildAdminWorkspaceSections({
  adminSetupGuideState,
  authSecuritySettings,
  authOptions,
  isPending,
  isSectionRefreshing,
  language,
  loadAdminUserCustomRange,
  loadGlobalCustomRange,
  polling,
  session,
  setShowAuthSecurityDialog,
  setShowSystemOAuthAppsDialog,
  setSystemOAuthEditorProvider,
  setSystemOAuthSettingsDirty,
  systemDashboard,
  systemOAuthSettings,
  t,
  toggleWorkspaceSection,
  uiPreferences,
  userManagement,
  focusSection
}) {
  return [
    {
      id: 'adminQuickSetup',
      render: () => adminSetupGuideState.allStepsComplete ? null : (
        <SetupGuidePanel
          collapsed={uiPreferences.adminQuickSetupCollapsed}
          dismissable={false}
          onDismiss={() => {}}
          onFocusSection={focusSection}
          onToggleCollapse={() => toggleWorkspaceSection('adminQuickSetupCollapsed')}
          savingLayout={isPending('uiPreferences')}
          sectionLoading={isSectionRefreshing('adminQuickSetupCollapsed')}
          steps={adminSetupGuideState.steps}
          t={t}
        />
      )
    },
    {
      id: 'systemDashboard',
      render: () => (
        <SystemDashboardSection
          collapsed={uiPreferences.systemDashboardCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          dashboard={systemDashboard}
          locale={language}
          onCollapseToggle={() => toggleWorkspaceSection('systemDashboardCollapsed')}
          onOpenEditor={polling.openSystemPollingDialog}
          onRunPoll={polling.runPoll}
          runningPoll={polling.runningPoll}
          sectionLoading={isSectionRefreshing('systemDashboardCollapsed')}
          t={t}
        />
      )
    },
    {
      id: 'oauthApps',
      render: () => (
        <OAuthAppsSection
          collapsed={uiPreferences.oauthAppsCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          oauthSettings={systemOAuthSettings}
          onCollapseToggle={() => toggleWorkspaceSection('oauthAppsCollapsed')}
          onEditGoogle={() => {
            setSystemOAuthSettingsDirty(false)
            setSystemOAuthEditorProvider('google')
            setShowSystemOAuthAppsDialog(true)
          }}
          onEditMicrosoft={() => {
            setSystemOAuthSettingsDirty(false)
            setSystemOAuthEditorProvider('microsoft')
            setShowSystemOAuthAppsDialog(true)
          }}
          sectionLoading={isSectionRefreshing('oauthAppsCollapsed')}
          t={t}
        />
      )
    },
    {
      id: 'authSecurity',
      render: () => (
        <AuthSecuritySection
          authSecuritySettings={authSecuritySettings}
          collapsed={uiPreferences.authSecurityCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          locale={language}
          onCollapseToggle={() => toggleWorkspaceSection('authSecurityCollapsed')}
          onOpenEditor={() => setShowAuthSecurityDialog(true)}
          sectionLoading={isSectionRefreshing('authSecurityCollapsed')}
          t={t}
        />
      )
    },
    {
      id: 'globalStats',
      render: () => (
        <Suspense fallback={<div className="muted-box">{t('common.refreshingSection')}</div>}>
          <PollingStatisticsSection
            collapsed={uiPreferences.globalStatsCollapsed}
            collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
            copy={t('pollingStats.globalCopy')}
            customRangeLoader={loadGlobalCustomRange}
            id="global-polling-stats-section"
            onCollapseToggle={() => toggleWorkspaceSection('globalStatsCollapsed')}
            sectionLoading={isSectionRefreshing('systemDashboardCollapsed')}
            stats={systemDashboard?.stats || null}
            t={t}
            title={t('pollingStats.globalTitle')}
          />
        </Suspense>
      )
    },
    {
      id: 'userManagement',
      render: () => (
        <UserManagementSection
          collapsed={uiPreferences.userManagementCollapsed}
          collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
          createUserDialogOpen={userManagement.showCreateUserDialog}
          createUserForm={userManagement.createUserForm}
          createUserLoading={isPending('createUser')}
          duplicateUsername={userManagement.duplicateCreateUsername}
          expandedUserId={userManagement.selectedUserId}
          locale={language}
          modeToggleLoading={isPending('multiUserModeSave')}
          multiUserEnabled={authOptions.multiUserEnabled}
          onCloseCreateUserDialog={userManagement.closeCreateUserDialog}
          onCollapseToggle={() => toggleWorkspaceSection('userManagementCollapsed')}
          onCreateUser={userManagement.createUser}
          onCreateUserFormChange={userManagement.setCreateUserForm}
          onDeleteUser={userManagement.requestDeleteUser}
          onForcePasswordChange={userManagement.requestForcePasswordChange}
          onLoadUserCustomRange={loadAdminUserCustomRange}
          onOpenCreateUserDialog={userManagement.openCreateUserDialog}
          onOpenResetPasswordDialog={userManagement.openResetPasswordDialog}
          onResetUserPasskeys={userManagement.resetUserPasskeys}
          onToggleExpandUser={userManagement.toggleExpandedUser}
          onToggleMultiUserEnabled={userManagement.requestToggleMultiUserMode}
          onToggleUserActive={userManagement.requestToggleUserActive}
          onUpdateUser={userManagement.updateUser}
          sectionLoading={isSectionRefreshing('userManagementCollapsed')}
          selectedUserConfig={userManagement.selectedUserConfig}
          selectedUserLoading={userManagement.selectedUserLoading}
          session={session}
          t={t}
          updatingPasskeysResetUserId={userManagement.selectedUserConfig?.user?.id && isPending(`resetPasskeys:${userManagement.selectedUserConfig.user.id}`)
            ? userManagement.selectedUserConfig.user.id
            : null}
          updatingUserId={userManagement.selectedUserConfig?.user?.id && (
            isPending(`updateUser:${userManagement.selectedUserConfig.user.id}`)
            || isPending(`deleteUser:${userManagement.selectedUserConfig.user.id}`)
          )
            ? userManagement.selectedUserConfig.user.id
            : null}
          users={userManagement.users}
        />
      )
    }
  ]
}
