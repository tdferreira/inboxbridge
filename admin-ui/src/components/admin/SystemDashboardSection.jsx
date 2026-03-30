import LoadingButton from '../common/LoadingButton'
import PaneToggleButton from '../common/PaneToggleButton'
import DurationValue from '../common/DurationValue'
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
          <div className="muted-box full user-polling-summary">
            {t('system.effectivePolling', { value: dashboard.polling.effectivePollEnabled ? t('common.yes') : t('common.no') })}<br />
            {t('system.effectiveInterval', { value: dashboard.polling.effectivePollInterval })}<br />
            {t('system.effectiveFetchWindow', { value: dashboard.polling.effectiveFetchWindow })}<br />
            {t('system.sourceHostMinSpacing')}: <DurationValue locale={locale} value={dashboard.polling.effectiveSourceHostMinSpacing} /><br />
            {t('system.sourceHostMaxConcurrency')}: {dashboard.polling.effectiveSourceHostMaxConcurrency}<br />
            {t('system.destinationProviderMinSpacing')}: <DurationValue locale={locale} value={dashboard.polling.effectiveDestinationProviderMinSpacing} /><br />
            {t('system.destinationProviderMaxConcurrency')}: {dashboard.polling.effectiveDestinationProviderMaxConcurrency}<br />
            {t('system.throttleLeaseTtl')}: <DurationValue locale={locale} value={dashboard.polling.effectiveThrottleLeaseTtl} /><br />
            {t('system.adaptiveThrottleMaxMultiplier')}: {dashboard.polling.effectiveAdaptiveThrottleMaxMultiplier}<br />
            {t('system.successJitterRatio')}: {dashboard.polling.effectiveSuccessJitterRatio}<br />
            {t('system.maxSuccessJitter')}: <DurationValue locale={locale} value={dashboard.polling.effectiveMaxSuccessJitter} />
          </div>
        </>
      ) : null}
    </section>
  )
}

export default SystemDashboardSection
