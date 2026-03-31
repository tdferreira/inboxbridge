import EmailAccountDialog from './EmailAccountDialog'
import EmailAccountListItem from './EmailAccountListItem'
import EmailAccountPollingDialog from './EmailAccountPollingDialog'
import CollapsibleSection from '../common/CollapsibleSection'
import LoadingButton from '../common/LoadingButton'
import './UserEmailAccountsSection.css'

function UserEmailAccountsSection({
  emailAccountForm,
  collapsed,
  collapseLoading,
  connectingEmailAccountId,
  deletingEmailAccountId,
  duplicateIdError,
  emailAccountFolders,
  emailAccountFoldersLoading,
  fetchers,
  fetcherDialogOpen,
  fetcherPollLoadingId,
  fetcherPollingDialog,
  fetcherPollingLoading,
  fetcherPollingForm,
  fetcherRefreshLoadingId,
  fetcherStatsById,
  fetcherStatsLoadingId,
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
  locale,
  availableOAuthProviders = [],
  microsoftOAuthAvailable = true
}) {
  const resolvedOAuthProviders = availableOAuthProviders.length
    ? availableOAuthProviders
    : (microsoftOAuthAvailable ? ['MICROSOFT'] : [])
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
                locale={locale}
                onConfigurePolling={onConfigureFetcherPolling}
                onConnectOAuth={onConnectOAuth}
                onDelete={onDeleteEmailAccount}
                onEdit={onEditEmailAccount}
                onLoadCustomRange={onLoadFetcherCustomRange}
                onRunPoll={onRunFetcherPoll}
                onToggleExpand={onFetcherToggleExpand}
                pollLoading={fetcherPollLoadingId === fetcher.emailAccountId}
                refreshLoading={fetcherRefreshLoadingId === fetcher.emailAccountId}
                stats={fetcherStatsById?.[fetcher.emailAccountId] || null}
                statsLoading={fetcherStatsLoadingId === fetcher.emailAccountId}
                t={t}
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
