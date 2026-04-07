import InfoHint from '@/shared/components/InfoHint'
import LoadingButton from '@/shared/components/LoadingButton'
import ModalDialog from '@/shared/components/ModalDialog'
import './UserPollingSettingsDialog.css'

function UserPollingSettingsDialog({
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
  const pollingDisabled = pollingSettingsForm.pollEnabledMode === 'DISABLED'

  return (
    <ModalDialog
      closeLabel={t('common.closeDialog')}
      isDirty={isDirty}
      onClose={onClose}
      title={t('userPolling.editTitle')}
      unsavedChangesMessage={t('dialogs.unsavedChanges')}
    >
      <form className="user-polling-dialog-form" onSubmit={onSavePollingSettings}>
        <div className="user-polling-dialog-sections">
          <section className="surface-card user-polling-dialog-section">
            <div className="user-polling-dialog-section-header">
              <div className="user-polling-dialog-section-title">{t('userPolling.schedulerSectionTitle')}</div>
              <p className="user-polling-dialog-section-copy">{t('userPolling.schedulerSectionCopy')}</p>
            </div>
            <div className="settings-grid user-polling-grid">
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
            disabled={pollingDisabled}
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
            </div>
          </section>

          <section className="surface-card user-polling-dialog-section">
            <div className="user-polling-dialog-section-header">
              <div className="user-polling-dialog-section-title">{t('userPolling.effectiveSectionTitle')}</div>
              <p className="user-polling-dialog-section-copy">{t('userPolling.effectiveSectionCopy')}</p>
            </div>
            <div className="muted-box">
          {t('userPolling.effectivePolling', { value: pollingSettings.effectivePollEnabled ? t('common.yes') : t('common.no') })}<br />
          {t('userPolling.effectiveInterval', { value: pollingSettings.effectivePollInterval })}<br />
          {t('userPolling.effectiveFetchWindow', { value: pollingSettings.effectiveFetchWindow })}
            </div>
          </section>
        </div>
        <div className="action-row">
          <LoadingButton className="primary" isLoading={pollingSettingsLoading} loadingLabel={t('userPolling.saveLoading')} type="submit">
            {t('userPolling.save')}
          </LoadingButton>
          <LoadingButton className="secondary" isLoading={pollingSettingsLoading} loadingLabel={t('userPolling.resetLoading')} onClick={onResetPollingSettings} type="button">
            {t('userPolling.useDefaults')}
          </LoadingButton>
        </div>
      </form>
    </ModalDialog>
  )
}

export default UserPollingSettingsDialog
