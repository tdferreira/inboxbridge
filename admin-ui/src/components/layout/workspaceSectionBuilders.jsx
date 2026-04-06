import AuthSecuritySection from '../admin/AuthSecuritySection'
import { Suspense, lazy } from 'react'
import OAuthAppsSection from '../admin/OAuthAppsSection'
import SystemDashboardSection from '../admin/SystemDashboardSection'
import UserManagementSection from '../admin/UserManagementSection'
import DestinationMailboxSection from '../destination/DestinationMailboxSection'
import UserEmailAccountsSection from '../emailAccounts/UserEmailAccountsSection'
import RemoteControlLaunchSection from './RemoteControlLaunchSection'
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
  session,
  userSetupGuideState
}) {
  return [
    {
      id: 'quickSetup',
      render: () => userSetupGuideState.allStepsComplete && uiPreferences.quickSetupDismissed && !uiPreferences.quickSetupPinnedVisible ? null : (
        <SetupGuidePanel
          collapsed={uiPreferences.quickSetupCollapsed}
          dismissable={userSetupGuideState.allStepsComplete}
          onDismiss={() => dismissQuickSetupGuide('user')}
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
          hasFetchers={emailAccounts.runnableUserEmailAccounts.length > 0}
          onCollapseToggle={() => toggleWorkspaceSection('userPollingCollapsed')}
          onOpenEditor={polling.openUserPollingDialog}
          onPausePoll={polling.pauseLivePoll}
          onResumePoll={polling.resumeLivePoll}
          onRunPoll={polling.runUserPoll}
          onStopPoll={polling.stopLivePoll}
          pollingSettings={polling.userPollingSettings}
          runningPoll={polling.runningUserPoll}
          livePoll={polling.livePoll}
          sectionLoading={isSectionRefreshing('userPollingCollapsed')}
          t={t}
        />
      )
    },
    {
      id: 'remoteControl',
      render: () => <RemoteControlLaunchSection t={t} />
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
            locale={language}
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
          destinationConfig={destinationConfig}
          destinationMeta={destinationMeta}
          duplicateIdError={emailAccounts.emailAccountDuplicateError}
          emailAccountForm={emailAccounts.emailAccountForm}
          emailAccountFolders={emailAccounts.emailAccountFolders}
          emailAccountFoldersLoading={emailAccounts.emailAccountFoldersLoading}
          fetcherDialogOpen={emailAccounts.showFetcherDialog}
          fetcherPollLoadingIds={emailAccounts.fetcherPollLoadingIds}
          fetcherPollingDialog={emailAccounts.fetcherPollingTarget}
          fetcherPollingForm={emailAccounts.fetcherPollingForm}
          fetcherPollingLoading={emailAccounts.fetcherPollingLoading}
          fetcherRefreshLoadingId={emailAccounts.expandedFetcherLoadingId}
          fetcherStatsById={emailAccounts.fetcherStatsById}
          fetcherStatsLoadingId={emailAccounts.fetcherStatsLoadingId}
          fetchers={emailAccounts.visibleFetchers}
          livePoll={polling.livePoll}
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
          onFolderInputActivity={emailAccounts.handleFolderInputActivity}
          onFolderInputFocus={emailAccounts.handleFolderInputFocus}
          onFetcherPollingFormChange={emailAccounts.handleFetcherPollingFormChange}
          onFetcherToggleExpand={emailAccounts.refreshFetcherState}
          onLoadFetcherCustomRange={emailAccounts.loadFetcherCustomRange}
          onResetFetcherPollingSettings={emailAccounts.resetFetcherPollingSettings}
          onRunFetcherPoll={emailAccounts.runFetcherPoll}
          onToggleEmailAccountEnabled={emailAccounts.toggleEmailAccountEnabled}
          onSaveEmailAccount={emailAccounts.saveEmailAccount}
          onSaveEmailAccountWithoutValidation={emailAccounts.saveEmailAccountWithoutValidation}
          onSaveEmailAccountAndConnectOAuth={emailAccounts.saveEmailAccountAndConnectOAuth}
          onSaveFetcherPollingSettings={emailAccounts.saveFetcherPollingSettings}
          onTestEmailAccountConnection={emailAccounts.testEmailAccountConnection}
          saveAndConnectLoading={isPending('bridgeSaveConnect')}
          saveLoading={isPending('bridgeSave')}
          sectionLoading={isSectionRefreshing('sourceEmailAccountsCollapsed')}
          t={t}
          testConnectionLoading={isPending('bridgeConnectionTest')}
          testResult={emailAccounts.emailAccountTestResult}
          togglingEmailAccountId={emailAccounts.togglingEmailAccountId}
          viewerUsername={session?.username || null}
        />
      )
    }
  ]
}

export function buildAdminWorkspaceSections({
  globalStatsNeedsAttention = false,
  adminSetupGuideState,
  authSecuritySettings,
  authOptions,
  dismissQuickSetupGuide,
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
      render: () => adminSetupGuideState.allStepsComplete && uiPreferences.adminQuickSetupDismissed && !uiPreferences.adminQuickSetupPinnedVisible ? null : (
        <SetupGuidePanel
          collapsed={uiPreferences.adminQuickSetupCollapsed}
          dismissable={adminSetupGuideState.allStepsComplete}
          onDismiss={() => dismissQuickSetupGuide('admin')}
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
          livePoll={polling.livePoll}
          locale={language}
          onCollapseToggle={() => toggleWorkspaceSection('systemDashboardCollapsed')}
          onMovePollSourceNext={polling.moveLivePollSourceNext}
          onOpenEditor={polling.openSystemPollingDialog}
          onPausePoll={polling.pauseLivePoll}
          onResumePoll={polling.resumeLivePoll}
          onRetryPollSource={polling.retryLivePollSource}
          onRunPoll={polling.runPoll}
          onStopPoll={polling.stopLivePoll}
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
            attentionActive={globalStatsNeedsAttention}
            collapsed={uiPreferences.globalStatsCollapsed}
            collapseLoading={isPending('uiPreferences') && uiPreferences.persistLayout}
            copy={t('pollingStats.globalCopy')}
            customRangeLoader={loadGlobalCustomRange}
            id="global-polling-stats-section"
            locale={language}
            onCollapseToggle={() => toggleWorkspaceSection('globalStatsCollapsed')}
            scheduledRunAlertInterval={systemDashboard?.polling?.effectivePollInterval || null}
            scheduledRunAlertSourceCount={systemDashboard?.stats?.enabledMailFetchers ?? 0}
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
