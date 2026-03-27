import ImportTimelineChart from '../common/ImportTimelineChart'
import LoadingButton from '../common/LoadingButton'
import PaneToggleButton from '../common/PaneToggleButton'
import './SystemDashboardSection.css'

/**
 * Admin-only system view for poller health, overrides, and manual poll
 * controls.
 */
function SystemDashboardSection({
  collapsed,
  collapseLoading,
  dashboard,
  onCollapseToggle,
  onOpenEditor,
  onRunPoll,
  runningPoll,
  sectionLoading = false,
  t,
  locale
}) {
  return (
    <section className="surface-card system-dashboard-section section-with-corner-toggle" id="system-dashboard-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">{t('system.title')}</div>
          <p className="section-copy">{t('system.copy')}</p>
        </div>
        <div className="panel-header-actions">
          <LoadingButton className="secondary" onClick={onOpenEditor} type="button">
            {t('system.edit')}
          </LoadingButton>
          <LoadingButton className="primary" isLoading={runningPoll} loadingLabel={t('system.runPollLoading')} onClick={onRunPoll}>
            {t('system.runPoll')}
          </LoadingButton>
        </div>
      </div>
      <PaneToggleButton className="pane-toggle-button-corner" collapseLabel={t('common.collapseSection')} collapsed={collapsed} disabled={collapseLoading} expandLabel={t('common.expandSection')} isLoading={collapseLoading} onClick={onCollapseToggle} />
      {sectionLoading ? (
        <div className="section-refresh-indicator" role="status">
          <span aria-hidden="true" className="section-refresh-spinner" />
          {t('common.refreshingSection')}
        </div>
      ) : null}

      {!collapsed && dashboard ? (
        <>
          <div className="system-dashboard-summary">
            <article className="surface-card metric-card"><span className="metric-label">{t('system.configuredBridges')}</span><strong>{dashboard.stats?.configuredMailFetchers ?? dashboard.overall.configuredSources}</strong></article>
            <article className="surface-card metric-card"><span className="metric-label">{t('system.enabledBridges')}</span><strong>{dashboard.stats?.enabledMailFetchers ?? dashboard.overall.enabledSources}</strong></article>
            <article className="surface-card metric-card"><span className="metric-label">{t('system.importedMessages')}</span><strong>{dashboard.stats?.totalImportedMessages ?? dashboard.overall.totalImportedMessages}</strong></article>
            <article className="surface-card metric-card"><span className="metric-label">{t('system.sourcesWithErrors')}</span><strong>{dashboard.stats?.sourcesWithErrors ?? dashboard.overall.sourcesWithErrors}</strong></article>
            <article className="surface-card metric-card"><span className="metric-label">{t('system.pollInterval')}</span><strong>{dashboard.overall.pollInterval}</strong></article>
            <article className="surface-card metric-card"><span className="metric-label">{t('system.fetchWindow')}</span><strong>{dashboard.overall.fetchWindow}</strong></article>
          </div>

          <ImportTimelineChart
            points={dashboard.stats?.importsByDay || []}
            timelines={dashboard.stats?.importTimelines || null}
            t={t}
            title={t('pollingStats.globalTimelineTitle')}
          />
          <div className="muted-box full user-polling-summary">
            {t('system.effectivePolling', { value: dashboard.polling.effectivePollEnabled ? t('common.yes') : t('common.no') })}<br />
            {t('system.effectiveInterval', { value: dashboard.polling.effectivePollInterval })}<br />
            {t('system.effectiveFetchWindow', { value: dashboard.polling.effectiveFetchWindow })}
          </div>
        </>
      ) : null}
    </section>
  )
}

export default SystemDashboardSection
