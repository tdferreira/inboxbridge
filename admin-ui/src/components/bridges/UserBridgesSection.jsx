import FetcherDialog from './FetcherDialog'
import FetcherListItem from './FetcherListItem'
import FetcherPollingDialog from './FetcherPollingDialog'
import LoadingButton from '../common/LoadingButton'
import PaneToggleButton from '../common/PaneToggleButton'
import './UserBridgesSection.css'

/**
 * Displays user-managed and env-managed mail fetchers in one operational list
 * while moving create/edit work into a dedicated modal dialog.
 */
function UserBridgesSection({
  bridgeForm,
  collapsed,
  collapseLoading,
  connectingBridgeId,
  deletingBridgeId,
  duplicateIdError,
  fetchers,
  fetcherDialogOpen,
  fetcherPollLoadingId,
  fetcherPollingDialog,
  fetcherPollingLoading,
  fetcherPollingForm,
  fetcherRefreshLoadingId,
  onAddFetcher,
  onApplyPreset,
  onBridgeFormChange,
  onCloseDialog,
  onClosePollingDialog,
  onCollapseToggle,
  onConfigureFetcherPolling,
  onConnectMicrosoft,
  onDeleteBridge,
  onEditBridge,
  onFetcherPollingFormChange,
  onFetcherToggleExpand,
  onResetFetcherPollingSettings,
  onRunFetcherPoll,
  onSaveBridge,
  onSaveFetcherPollingSettings,
  onTestConnection,
  saveLoading,
  sectionLoading = false,
  t,
  testConnectionLoading = false,
  testResult = null,
  locale
}) {
  return (
    <section className="surface-card user-bridges-section section-with-corner-toggle" id="source-bridges-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">{t('bridges.title')}</div>
          <p className="section-copy">{t('bridges.copy')}</p>
        </div>
        {!collapsed ? (
          <div className="panel-header-actions">
            <LoadingButton className="primary" isLoading={false} onClick={onAddFetcher} type="button">
              {t('bridges.add')}
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
              <FetcherListItem
                key={`${fetcher.managementSource}:${fetcher.bridgeId}`}
                connectLoading={connectingBridgeId === fetcher.bridgeId}
                deleteLoading={deletingBridgeId === fetcher.bridgeId}
                fetcher={fetcher}
                locale={locale}
                onConfigurePolling={onConfigureFetcherPolling}
                onConnectMicrosoft={onConnectMicrosoft}
                onDelete={onDeleteBridge}
                onEdit={onEditBridge}
                onRunPoll={onRunFetcherPoll}
                onToggleExpand={onFetcherToggleExpand}
                pollLoading={fetcherPollLoadingId === fetcher.bridgeId}
                refreshLoading={fetcherRefreshLoadingId === fetcher.bridgeId}
                t={t}
              />
            ))}
          </div>
        ) : (
          <div className="muted-box user-bridges-empty-state">
            <strong>{t('bridges.emptyTitle')}</strong><br />
            {t('bridges.emptyBody')}
          </div>
        )
      ) : null}
      {fetcherDialogOpen ? (
        <FetcherDialog
          bridgeForm={bridgeForm}
          duplicateIdError={duplicateIdError}
          onApplyPreset={onApplyPreset}
          onBridgeFormChange={onBridgeFormChange}
          onClose={onCloseDialog}
          onSave={onSaveBridge}
          onTestConnection={onTestConnection}
          saveLoading={saveLoading}
          t={t}
          testConnectionLoading={testConnectionLoading}
          testResult={testResult}
        />
      ) : null}
      {fetcherPollingDialog ? (
        <FetcherPollingDialog
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

export default UserBridgesSection
