import InfoHint from '../common/InfoHint'
import LoadingButton from '../common/LoadingButton'
import ModalDialog from '../common/ModalDialog'

function SystemPollingSettingsDialog({
  isDirty,
  onClose,
  onPollingFormChange,
  onResetPollingSettings,
  onSavePollingSettings,
  pollingSettings,
  pollingSettingsForm,
  pollingSettingsLoading,
  t
}) {
  return (
    <ModalDialog
      closeLabel={t('common.closeDialog')}
      isDirty={isDirty}
      onClose={onClose}
      title={t('system.editTitle')}
      unsavedChangesMessage={t('dialogs.unsavedChanges')}
    >
      <form className="settings-grid system-polling-grid" onSubmit={onSavePollingSettings}>
        <label>
          <span className="field-label-row">
            <span>{t('system.pollingMode')}</span>
            <InfoHint text={t('system.pollingModeHelp')} />
          </span>
          <select value={pollingSettingsForm.pollEnabledMode} onChange={(event) => onPollingFormChange((current) => ({ ...current, pollEnabledMode: event.target.value }))}>
            <option value="DEFAULT">{pollingSettings.defaultPollEnabled ? t('system.pollingDefaultEnabled') : t('system.pollingDefaultDisabled')}</option>
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
            placeholder={t('system.leaveBlank', { value: pollingSettings.defaultPollInterval })}
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
            placeholder={t('system.leaveBlank', { value: pollingSettings.defaultFetchWindow })}
            type="number"
            value={pollingSettingsForm.fetchWindowOverride}
            onChange={(event) => onPollingFormChange((current) => ({ ...current, fetchWindowOverride: event.target.value }))}
          />
        </label>
        <div className="muted-box full">
          {t('system.effectivePolling', { value: pollingSettings.effectivePollEnabled ? t('common.yes') : t('common.no') })}<br />
          {t('system.effectiveInterval', { value: pollingSettings.effectivePollInterval })}<br />
          {t('system.effectiveFetchWindow', { value: pollingSettings.effectiveFetchWindow })}<br />
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
    </ModalDialog>
  )
}

export default SystemPollingSettingsDialog
