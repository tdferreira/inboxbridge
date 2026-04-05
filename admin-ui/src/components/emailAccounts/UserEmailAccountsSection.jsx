import EmailAccountDialog from './EmailAccountDialog'
import EmailAccountListItem from './EmailAccountListItem'
import EmailAccountPollingDialog from './EmailAccountPollingDialog'
import CollapsibleSection from '../common/CollapsibleSection'
import LoadingButton from '../common/LoadingButton'
import './UserEmailAccountsSection.css'

function UserEmailAccountsSection({
  emailAccountForm,
  destinationConfig = null,
  destinationMeta = null,
  collapsed,
  collapseLoading,
  connectingEmailAccountId,
  deletingEmailAccountId,
  duplicateIdError,
  emailAccountFolders,
  emailAccountFoldersLoading,
  fetchers,
  fetcherDialogOpen,
  fetcherPollLoadingIds = [],
  fetcherPollingDialog,
  fetcherPollingLoading,
  fetcherPollingForm,
  fetcherRefreshLoadingId,
  fetcherStatsById,
  fetcherStatsLoadingId,
  livePoll = null,
  onLoadFetcherCustomRange,
  onAddFetcher,
  onApplyPreset,
    onEmailAccountFormChange,
  onCloseDialog,
  onClosePollingDialog,
  onCollapseToggle,
  onConfigureFetcherPolling,
  onConnectOAuth,
    onDeleteEmailAccount,
  onEditEmailAccount,
  onFetcherPollingFormChange,
  onFetcherToggleExpand,
  onResetFetcherPollingSettings,
  onRunFetcherPoll,
  onToggleEmailAccountEnabled,
  onSaveEmailAccount,
  onSaveEmailAccountAndConnectOAuth,
  onSaveFetcherPollingSettings,
  onTestEmailAccountConnection,
  saveLoading,
  saveAndConnectLoading = false,
  sectionLoading = false,
  t,
  testConnectionLoading = false,
  testResult = null,
  togglingEmailAccountId = null,
  viewerUsername = null,
  locale,
  availableOAuthProviders = [],
  microsoftOAuthAvailable = true
}) {
  const resolvedOAuthProviders = availableOAuthProviders.length
    ? availableOAuthProviders
    : (microsoftOAuthAvailable ? ['MICROSOFT'] : [])
  const liveSourcesById = new Map((livePoll?.sources || []).map((source) => [source.sourceId, source]))
  const livePollRunning = Boolean(livePoll?.running)
  return (
    <CollapsibleSection
      actions={!collapsed ? (
            <LoadingButton className="primary" isLoading={false} onClick={onAddFetcher} type="button">
              {t('emailAccounts.addSection')}
            </LoadingButton>
      ) : null}
      className="user-email-accounts-section"
      collapsed={collapsed}
      collapseLoading={collapseLoading}
      copy={t('emailAccounts.copy')}
      id="source-email-accounts-section"
      onCollapseToggle={onCollapseToggle}
      sectionLoading={sectionLoading}
      t={t}
      title={t('emailAccounts.title')}
    >
      <>
        {fetchers.length > 0 ? (
          <div className="fetcher-list">
            {fetchers.map((fetcher) => (
              <EmailAccountListItem
                key={`${fetcher.managementSource}:${fetcher.emailAccountId}`}
                connectLoading={connectingEmailAccountId === fetcher.emailAccountId}
                deleteLoading={deletingEmailAccountId === fetcher.emailAccountId}
                fetcher={fetcher}
                liveSource={liveSourcesById.get(fetcher.emailAccountId) || null}
                locale={locale}
                livePollRunning={livePollRunning}
                onConfigurePolling={onConfigureFetcherPolling}
                onConnectOAuth={onConnectOAuth}
                onDelete={onDeleteEmailAccount}
                onEdit={onEditEmailAccount}
                onLoadCustomRange={onLoadFetcherCustomRange}
                onRunPoll={onRunFetcherPoll}
                onToggleEnabled={onToggleEmailAccountEnabled}
                onToggleExpand={onFetcherToggleExpand}
                pollLoading={fetcherPollLoadingIds.includes(fetcher.emailAccountId)}
                refreshLoading={fetcherRefreshLoadingId === fetcher.emailAccountId}
                stats={fetcherStatsById?.[fetcher.emailAccountId] || null}
                statsLoading={fetcherStatsLoadingId === fetcher.emailAccountId}
                t={t}
                toggleEnabledLoading={togglingEmailAccountId === fetcher.emailAccountId}
                viewerUsername={viewerUsername}
              />
            ))}
          </div>
        ) : (
          <div className="muted-box user-email-accounts-empty-state">
            <strong>{t('emailAccounts.emptyTitle')}</strong><br />
            {t('emailAccounts.emptyBody')}
          </div>
        )}
        {fetcherDialogOpen ? (
          <EmailAccountDialog
            availableOAuthProviders={resolvedOAuthProviders}
            destinationConfig={destinationConfig}
            destinationMeta={destinationMeta}
            emailAccountForm={emailAccountForm}
            emailAccountFolders={emailAccountFolders}
            emailAccountFoldersLoading={emailAccountFoldersLoading}
            duplicateIdError={duplicateIdError}
            onApplyPreset={onApplyPreset}
            onEmailAccountFormChange={onEmailAccountFormChange}
            onClose={onCloseDialog}
            onSave={onSaveEmailAccount}
            onSaveAndConnectOAuth={onSaveEmailAccountAndConnectOAuth}
            onTestEmailAccountConnection={onTestEmailAccountConnection}
            saveLoading={saveLoading}
            saveAndConnectLoading={saveAndConnectLoading}
            t={t}
            testConnectionLoading={testConnectionLoading}
            testResult={testResult}
          />
        ) : null}
        {fetcherPollingDialog ? (
          <EmailAccountPollingDialog
            fetcher={fetcherPollingDialog}
            form={fetcherPollingForm}
            loading={fetcherPollingLoading}
            onChange={onFetcherPollingFormChange}
            onClose={onClosePollingDialog}
            onReset={onResetFetcherPollingSettings}
            onSave={onSaveFetcherPollingSettings}
            t={t}
          />
        ) : null}
      </>
    </CollapsibleSection>
  )
}

export default UserEmailAccountsSection
