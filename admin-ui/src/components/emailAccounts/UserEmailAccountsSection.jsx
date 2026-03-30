import EmailAccountDialog from './EmailAccountDialog'
import EmailAccountListItem from './EmailAccountListItem'
import EmailAccountPollingDialog from './EmailAccountPollingDialog'
import LoadingButton from '../common/LoadingButton'
import PaneToggleButton from '../common/PaneToggleButton'
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
    <section className="surface-card user-email-accounts-section section-with-corner-toggle" id="source-email-accounts-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">{t('emailAccounts.title')}</div>
          <p className="section-copy">{t('emailAccounts.copy')}</p>
        </div>
        {!collapsed ? (
          <div className="panel-header-actions">
            <LoadingButton className="primary" isLoading={false} onClick={onAddFetcher} type="button">
              {t('emailAccounts.addSection')}
            </LoadingButton>
          </div>
        ) : null}
      </div>
      <PaneToggleButton className="pane-toggle-button-corner" collapseLabel={t('common.collapseSection')} collapsed={collapsed} disabled={collapseLoading} expandLabel={t('common.expandSection')} isLoading={collapseLoading} onClick={onCollapseToggle} />
      {sectionLoading ? (
        <div className="section-refresh-indicator" role="status">
          <span aria-hidden="true" className="section-refresh-spinner" />
          {t('common.refreshingSection')}
        </div>
      ) : null}
      {!collapsed ? (
        fetchers.length > 0 ? (
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
        )
      ) : null}
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
    </section>
  )
}

export default UserEmailAccountsSection
