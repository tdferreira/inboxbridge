import InfoHint from '../common/InfoHint'
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
  onPollingFormChange,
  onResetPollingSettings,
  onSavePollingSettings,
  pollingSettings,
  pollingSettingsForm,
  pollingSettingsLoading,
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

      {!collapsed && pollingSettings ? (
        <>
          {!hasFetchers ? (
            <div className="muted-box user-polling-empty-state">{t('userPolling.noFetchers')}</div>
          ) : null}

          <form className="settings-grid user-polling-grid" onSubmit={onSavePollingSettings}>
            <label>
              <span className="field-label-row">
                <span>{t('userPolling.pollingMode')}</span>
                <InfoHint text={t('userPolling.pollingModeHelp')} />
              </span>
              <select value={pollingSettingsForm.pollEnabledMode} onChange={(event) => onPollingFormChange((current) => ({ ...current, pollEnabledMode: event.target.value }))}>
                <option value="DEFAULT">{pollingSettings.defaultPollEnabled ? t('userPolling.pollingDefaultEnabled') : t('userPolling.pollingDefaultDisabled')}</option>
                <option value="ENABLED">{t('userPolling.pollingEnabledOverride')}</option>
                <option value="DISABLED">{t('userPolling.pollingDisabledOverride')}</option>
              </select>
            </label>
            <label>
              <span className="field-label-row">
                <span>{t('userPolling.pollIntervalOverride')}</span>
                <InfoHint text={t('userPolling.pollIntervalHelp')} />
              </span>
              <input
                placeholder={t('userPolling.leaveBlank', { value: pollingSettings.defaultPollInterval })}
                value={pollingSettingsForm.pollIntervalOverride}
                onChange={(event) => onPollingFormChange((current) => ({ ...current, pollIntervalOverride: event.target.value }))}
              />
            </label>
            <label>
              <span className="field-label-row">
                <span>{t('userPolling.fetchWindowOverride')}</span>
                <InfoHint text={t('userPolling.fetchWindowHelp')} />
              </span>
              <input
                min="1"
                max="500"
                placeholder={t('userPolling.leaveBlank', { value: pollingSettings.defaultFetchWindow })}
                type="number"
                value={pollingSettingsForm.fetchWindowOverride}
                onChange={(event) => onPollingFormChange((current) => ({ ...current, fetchWindowOverride: event.target.value }))}
              />
            </label>
            <div className="muted-box full">
              {t('userPolling.effectivePolling', { value: pollingSettings.effectivePollEnabled ? t('common.yes') : t('common.no') })}<br />
              {t('userPolling.effectiveInterval', { value: pollingSettings.effectivePollInterval })}<br />
              {t('userPolling.effectiveFetchWindow', { value: pollingSettings.effectiveFetchWindow })}<br />
              {t('userPolling.scopeNote')}
            </div>
            <div className="action-row full">
              <LoadingButton className="primary" isLoading={pollingSettingsLoading} loadingLabel={t('userPolling.saveLoading')} type="submit">
                {t('userPolling.save')}
              </LoadingButton>
              <LoadingButton className="secondary" isLoading={pollingSettingsLoading} loadingLabel={t('userPolling.resetLoading')} onClick={onResetPollingSettings} type="button">
                {t('userPolling.useDefaults')}
              </LoadingButton>
            </div>
          </form>
        </>
      ) : null}
    </section>
  )
}

export default UserPollingSettingsSection
