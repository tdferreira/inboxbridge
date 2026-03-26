import InfoHint from '../common/InfoHint'
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
  onPollingFormChange,
  onResetPollingSettings,
  onSavePollingSettings,
  onCollapseToggle,
  onRunPoll,
  pollingSettingsForm,
  pollingSettingsLoading,
  runningPoll,
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
          <LoadingButton className="primary" isLoading={runningPoll} loadingLabel={t('system.runPollLoading')} onClick={onRunPoll}>
            {t('system.runPoll')}
          </LoadingButton>
        </div>
      </div>
      <PaneToggleButton className="pane-toggle-button-corner" collapseLabel={t('common.collapseSection')} collapsed={collapsed} disabled={collapseLoading} expandLabel={t('common.expandSection')} isLoading={collapseLoading} onClick={onCollapseToggle} />

      {!collapsed && dashboard ? (
        <>
          <div className="system-dashboard-summary">
            <article className="surface-card metric-card"><span className="metric-label">{t('system.configuredBridges')}</span><strong>{dashboard.overall.configuredSources}</strong></article>
            <article className="surface-card metric-card"><span className="metric-label">{t('system.importedMessages')}</span><strong>{dashboard.overall.totalImportedMessages}</strong></article>
            <article className="surface-card metric-card"><span className="metric-label">{t('system.sourcesWithErrors')}</span><strong>{dashboard.overall.sourcesWithErrors}</strong></article>
            <article className="surface-card metric-card"><span className="metric-label">{t('system.pollInterval')}</span><strong>{dashboard.overall.pollInterval}</strong></article>
            <article className="surface-card metric-card"><span className="metric-label">{t('system.fetchWindow')}</span><strong>{dashboard.overall.fetchWindow}</strong></article>
          </div>

          <form className="settings-grid system-polling-grid" onSubmit={onSavePollingSettings}>
            <label>
              <span className="field-label-row">
                <span>{t('system.pollingMode')}</span>
                <InfoHint text={t('system.pollingModeHelp')} />
              </span>
              <select value={pollingSettingsForm.pollEnabledMode} onChange={(event) => onPollingFormChange((current) => ({ ...current, pollEnabledMode: event.target.value }))}>
                <option value="DEFAULT">{dashboard.polling.defaultPollEnabled ? t('system.pollingDefaultEnabled') : t('system.pollingDefaultDisabled')}</option>
                <option value="ENABLED">{t('system.pollingEnabledOverride')}</option>
                <option value="DISABLED">{t('system.pollingDisabledOverride')}</option>
              </select>
            </label>
            <label>
              <span className="field-label-row">
                <span>{t('system.pollIntervalOverride')}</span>
                <InfoHint text={t('system.pollIntervalHelp')} />
              </span>
              <input
                placeholder={t('system.leaveBlank', { value: dashboard.polling.defaultPollInterval })}
                value={pollingSettingsForm.pollIntervalOverride}
                onChange={(event) => onPollingFormChange((current) => ({ ...current, pollIntervalOverride: event.target.value }))}
              />
            </label>
            <label>
              <span className="field-label-row">
                <span>{t('system.fetchWindowOverride')}</span>
                <InfoHint text={t('system.fetchWindowHelp')} />
              </span>
              <input
                min="1"
                max="500"
                placeholder={t('system.leaveBlank', { value: dashboard.polling.defaultFetchWindow })}
                type="number"
                value={pollingSettingsForm.fetchWindowOverride}
                onChange={(event) => onPollingFormChange((current) => ({ ...current, fetchWindowOverride: event.target.value }))}
              />
            </label>
            <div className="muted-box full">
              {t('system.effectivePolling', { value: dashboard.polling.effectivePollEnabled ? t('common.yes') : t('common.no') })}<br />
              {t('system.effectiveInterval', { value: dashboard.polling.effectivePollInterval })}<br />
              {t('system.effectiveFetchWindow', { value: dashboard.polling.effectiveFetchWindow })}<br />
              {t('system.intervalExamples')}
            </div>
            <div className="action-row full">
              <LoadingButton className="primary" isLoading={pollingSettingsLoading} loadingLabel={t('system.savePollSettingsLoading')} type="submit">
                {t('system.savePollSettings')}
              </LoadingButton>
              <LoadingButton className="secondary" isLoading={pollingSettingsLoading} loadingLabel={t('system.resetOverridesLoading')} onClick={onResetPollingSettings} type="button">
                {t('system.useEnvDefaults')}
              </LoadingButton>
            </div>
          </form>
        </>
      ) : null}
    </section>
  )
}

export default SystemDashboardSection
