import ModalDialog from '../common/ModalDialog'
import LoadingButton from '../common/LoadingButton'
import InfoHint from '../common/InfoHint'
import './FetcherPollingDialog.css'

function FetcherPollingDialog({
  fetcher,
  form,
  loading,
  onChange,
  onClose,
  onReset,
  onSave,
  t
}) {
  if (!fetcher) {
    return null
  }

  return (
    <ModalDialog
      closeLabel={t('sourcePolling.close')}
      isDirty={form.isDirty}
      onClose={onClose}
      size="wide"
      title={t('sourcePolling.title', { bridgeId: fetcher.bridgeId })}
      unsavedChangesMessage={t('sourcePolling.unsavedChanges')}
    >
      <form className="fetcher-polling-dialog-form" onSubmit={onSave}>
        <div className="settings-grid fetcher-polling-grid">
          <label>
            <span className="field-label-row">
              <span>{t('sourcePolling.pollingMode')}</span>
              <InfoHint text={t('sourcePolling.pollingModeHelp')} />
            </span>
            <select value={form.pollEnabledMode} onChange={(event) => onChange((current) => ({ ...current, pollEnabledMode: event.target.value, isDirty: true }))}>
              <option value="DEFAULT">{form.basePollEnabled ? t('sourcePolling.pollingDefaultEnabled') : t('sourcePolling.pollingDefaultDisabled')}</option>
              <option value="ENABLED">{t('sourcePolling.pollingEnabledOverride')}</option>
              <option value="DISABLED">{t('sourcePolling.pollingDisabledOverride')}</option>
            </select>
          </label>
          <label>
            <span className="field-label-row">
              <span>{t('sourcePolling.pollIntervalOverride')}</span>
              <InfoHint text={t('sourcePolling.pollIntervalHelp')} />
            </span>
            <input
              placeholder={t('sourcePolling.leaveBlank', { value: form.basePollInterval })}
              value={form.pollIntervalOverride}
              onChange={(event) => onChange((current) => ({ ...current, pollIntervalOverride: event.target.value, isDirty: true }))}
            />
          </label>
          <label>
            <span className="field-label-row">
              <span>{t('sourcePolling.fetchWindowOverride')}</span>
              <InfoHint text={t('sourcePolling.fetchWindowHelp')} />
            </span>
            <input
              min="1"
              max="500"
              placeholder={t('sourcePolling.leaveBlank', { value: form.baseFetchWindow })}
              type="number"
              value={form.fetchWindowOverride}
              onChange={(event) => onChange((current) => ({ ...current, fetchWindowOverride: event.target.value, isDirty: true }))}
            />
          </label>
          <div className="muted-box full">
            {t('sourcePolling.effectivePolling', { value: form.effectivePollEnabled ? t('common.yes') : t('common.no') })}<br />
            {t('sourcePolling.effectiveInterval', { value: form.effectivePollInterval })}<br />
            {t('sourcePolling.effectiveFetchWindow', { value: form.effectiveFetchWindow })}<br />
            {t('sourcePolling.scopeNote')}
          </div>
          <div className="action-row full">
            <LoadingButton className="primary" isLoading={loading} loadingLabel={t('sourcePolling.saveLoading')} type="submit">
              {t('sourcePolling.save')}
            </LoadingButton>
            <LoadingButton className="secondary" isLoading={loading} loadingLabel={t('sourcePolling.resetLoading')} onClick={onReset} type="button">
              {t('sourcePolling.useInheritedDefaults')}
            </LoadingButton>
          </div>
        </div>
      </form>
    </ModalDialog>
  )
}

export default FetcherPollingDialog
