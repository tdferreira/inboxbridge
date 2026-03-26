import FetcherDialog from './FetcherDialog'
import FetcherListItem from './FetcherListItem'
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
  onAddFetcher,
  onApplyPreset,
  onBridgeFormChange,
  onCloseDialog,
  onCollapseToggle,
  onConnectMicrosoft,
  onDeleteBridge,
  onEditBridge,
  onSaveBridge,
  saveLoading,
  t,
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
                onConnectMicrosoft={onConnectMicrosoft}
                onDelete={onDeleteBridge}
                onEdit={onEditBridge}
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
          saveLoading={saveLoading}
          t={t}
        />
      ) : null}
    </section>
  )
}

export default UserBridgesSection
