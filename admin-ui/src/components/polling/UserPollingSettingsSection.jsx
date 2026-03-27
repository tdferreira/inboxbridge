import LoadingButton from '../common/LoadingButton'
import PaneToggleButton from '../common/PaneToggleButton'
import './UserPollingSettingsSection.css'

/**
 * Lets each authenticated user override polling cadence for their own
 * database-backed mail fetchers while keeping deployment defaults visible.
 */
function UserPollingSettingsSection({
  collapsed,
  collapseLoading,
  hasFetchers,
  onCollapseToggle,
  onOpenEditor,
  pollingSettings,
  sectionLoading = false,
  t
}) {
  return (
    <section className="surface-card user-polling-section section-with-corner-toggle" id="user-polling-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">{t('userPolling.title')}</div>
          <p className="section-copy">{t('userPolling.copy')}</p>
        </div>
      </div>
      <PaneToggleButton className="pane-toggle-button-corner" collapseLabel={t('common.collapseSection')} collapsed={collapsed} disabled={collapseLoading} expandLabel={t('common.expandSection')} isLoading={collapseLoading} onClick={onCollapseToggle} />
      {sectionLoading ? (
        <div className="section-refresh-indicator" role="status">
          <span aria-hidden="true" className="section-refresh-spinner" />
          {t('common.refreshingSection')}
        </div>
      ) : null}

      {!collapsed && pollingSettings ? (
        <>
          {!hasFetchers ? (
            <div className="muted-box user-polling-empty-state">{t('userPolling.noFetchers')}</div>
          ) : null}
          <div className="muted-box full user-polling-summary">
            {t('userPolling.effectivePolling', { value: pollingSettings.effectivePollEnabled ? t('common.yes') : t('common.no') })}<br />
            {t('userPolling.effectiveInterval', { value: pollingSettings.effectivePollInterval })}<br />
            {t('userPolling.effectiveFetchWindow', { value: pollingSettings.effectiveFetchWindow })}
          </div>
          <div className="action-row">
            <LoadingButton className="primary" onClick={onOpenEditor} type="button">
              {t('userPolling.edit')}
            </LoadingButton>
          </div>
        </>
      ) : null}
    </section>
  )
}

export default UserPollingSettingsSection
