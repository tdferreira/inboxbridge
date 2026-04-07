import InfoHint from '@/shared/components/InfoHint'
import LoadingButton from '@/shared/components/LoadingButton'
import ModalDialog from '@/shared/components/ModalDialog'
import DurationValue from '@/shared/components/DurationValue'
import { formatDurationHint } from '@/lib/formatters'
import './SystemPollingSettingsDialog.css'

function SystemPollingSettingsDialog({
  isDirty,
  onClose,
  onPollingFormChange,
  onResetPollingSettings,
  onSavePollingSettings,
  locale = 'en',
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
      title={t('system.editTitle')}
      unsavedChangesMessage={t('dialogs.unsavedChanges')}
    >
      <form className="system-polling-form" onSubmit={onSavePollingSettings}>
        <div className="system-polling-sections">
          <section className="surface-card system-polling-section">
            <div className="system-polling-section-header">
              <div className="system-polling-section-title">{t('system.schedulerSectionTitle')}</div>
              <p className="system-polling-section-copy">{t('system.schedulerSectionCopy')}</p>
            </div>
            <div className="settings-grid system-polling-grid">
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
            disabled={pollingDisabled}
            placeholder={t('system.leaveBlank', { value: pollingSettings.defaultPollInterval })}
            title={formatDurationHint(pollingSettingsForm.pollIntervalOverride || pollingSettings.defaultPollInterval, locale) || undefined}
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
            </div>
          </section>

          <section className="surface-card system-polling-section">
            <div className="system-polling-section-header">
              <div className="system-polling-section-title">{t('system.manualRunsSectionTitle')}</div>
              <p className="system-polling-section-copy">{t('system.manualRunsSectionCopy')}</p>
            </div>
            <div className="settings-grid system-polling-grid">
        <div className="form-field-pair full">
          <label>
            <span className="field-label-row">
              <span>{t('system.manualTriggerLimitCount')}</span>
              <InfoHint text={t('system.manualTriggerLimitCountHelp')} />
            </span>
            <input
              min="1"
              max="100"
              placeholder={t('system.leaveBlank', { value: pollingSettings.defaultManualTriggerLimitCount })}
              type="number"
              value={pollingSettingsForm.manualTriggerLimitCountOverride}
              onChange={(event) => onPollingFormChange((current) => ({ ...current, manualTriggerLimitCountOverride: event.target.value }))}
            />
          </label>
          <label>
            <span className="field-label-row">
              <span>{t('system.manualTriggerLimitWindowSeconds')}</span>
              <InfoHint text={t('system.manualTriggerLimitWindowSecondsHelp')} />
            </span>
            <input
              min="10"
              max="3600"
              placeholder={t('system.leaveBlank', { value: pollingSettings.defaultManualTriggerLimitWindowSeconds })}
              type="number"
              value={pollingSettingsForm.manualTriggerLimitWindowSecondsOverride}
              onChange={(event) => onPollingFormChange((current) => ({ ...current, manualTriggerLimitWindowSecondsOverride: event.target.value }))}
            />
          </label>
        </div>
            </div>
          </section>

          <section className="surface-card system-polling-section">
            <div className="system-polling-section-header">
              <div className="system-polling-section-title">{t('system.sourceThrottleSectionTitle')}</div>
              <p className="system-polling-section-copy">{t('system.sourceThrottleSectionCopy')}</p>
            </div>
            <div className="settings-grid system-polling-grid">
        <div className="form-field-pair full">
          <label>
            <span className="field-label-row">
              <span>{t('system.sourceHostMinSpacing')}</span>
              <InfoHint text={t('system.sourceHostMinSpacingHelp')} />
            </span>
            <input
              placeholder={t('system.leaveBlank', { value: pollingSettings.defaultSourceHostMinSpacing })}
              title={formatDurationHint(pollingSettingsForm.sourceHostMinSpacingOverride || pollingSettings.defaultSourceHostMinSpacing, locale) || undefined}
              value={pollingSettingsForm.sourceHostMinSpacingOverride}
              onChange={(event) => onPollingFormChange((current) => ({ ...current, sourceHostMinSpacingOverride: event.target.value }))}
            />
          </label>
          <label>
            <span className="field-label-row">
              <span>{t('system.sourceHostMaxConcurrency')}</span>
              <InfoHint text={t('system.sourceHostMaxConcurrencyHelp')} />
            </span>
            <input
              min="1"
              max="100"
              placeholder={t('system.leaveBlank', { value: pollingSettings.defaultSourceHostMaxConcurrency })}
              type="number"
              value={pollingSettingsForm.sourceHostMaxConcurrencyOverride}
              onChange={(event) => onPollingFormChange((current) => ({ ...current, sourceHostMaxConcurrencyOverride: event.target.value }))}
            />
          </label>
        </div>
            </div>
          </section>

          <section className="surface-card system-polling-section">
            <div className="system-polling-section-header">
              <div className="system-polling-section-title">{t('system.destinationThrottleSectionTitle')}</div>
              <p className="system-polling-section-copy">{t('system.destinationThrottleSectionCopy')}</p>
            </div>
            <div className="settings-grid system-polling-grid">
        <div className="form-field-pair full">
          <label>
            <span className="field-label-row">
              <span>{t('system.destinationProviderMinSpacing')}</span>
              <InfoHint text={t('system.destinationProviderMinSpacingHelp')} />
            </span>
            <input
              placeholder={t('system.leaveBlank', { value: pollingSettings.defaultDestinationProviderMinSpacing })}
              title={formatDurationHint(pollingSettingsForm.destinationProviderMinSpacingOverride || pollingSettings.defaultDestinationProviderMinSpacing, locale) || undefined}
              value={pollingSettingsForm.destinationProviderMinSpacingOverride}
              onChange={(event) => onPollingFormChange((current) => ({ ...current, destinationProviderMinSpacingOverride: event.target.value }))}
            />
          </label>
          <label>
            <span className="field-label-row">
              <span>{t('system.destinationProviderMaxConcurrency')}</span>
              <InfoHint text={t('system.destinationProviderMaxConcurrencyHelp')} />
            </span>
            <input
              min="1"
              max="100"
              placeholder={t('system.leaveBlank', { value: pollingSettings.defaultDestinationProviderMaxConcurrency })}
              type="number"
              value={pollingSettingsForm.destinationProviderMaxConcurrencyOverride}
              onChange={(event) => onPollingFormChange((current) => ({ ...current, destinationProviderMaxConcurrencyOverride: event.target.value }))}
            />
          </label>
        </div>
            </div>
          </section>

          <section className="surface-card system-polling-section">
            <div className="system-polling-section-header">
              <div className="system-polling-section-title">{t('system.adaptiveSectionTitle')}</div>
              <p className="system-polling-section-copy">{t('system.adaptiveSectionCopy')}</p>
            </div>
            <div className="settings-grid system-polling-grid">
        <div className="form-field-pair full">
          <label>
            <span className="field-label-row">
              <span>{t('system.throttleLeaseTtl')}</span>
              <InfoHint text={t('system.throttleLeaseTtlHelp')} />
            </span>
            <input
              placeholder={t('system.leaveBlank', { value: pollingSettings.defaultThrottleLeaseTtl })}
              title={formatDurationHint(pollingSettingsForm.throttleLeaseTtlOverride || pollingSettings.defaultThrottleLeaseTtl, locale) || undefined}
              value={pollingSettingsForm.throttleLeaseTtlOverride}
              onChange={(event) => onPollingFormChange((current) => ({ ...current, throttleLeaseTtlOverride: event.target.value }))}
            />
          </label>
          <label>
            <span className="field-label-row">
              <span>{t('system.adaptiveThrottleMaxMultiplier')}</span>
              <InfoHint text={t('system.adaptiveThrottleMaxMultiplierHelp')} />
            </span>
            <input
              min="1"
              max="100"
              placeholder={t('system.leaveBlank', { value: pollingSettings.defaultAdaptiveThrottleMaxMultiplier })}
              type="number"
              value={pollingSettingsForm.adaptiveThrottleMaxMultiplierOverride}
              onChange={(event) => onPollingFormChange((current) => ({ ...current, adaptiveThrottleMaxMultiplierOverride: event.target.value }))}
            />
          </label>
        </div>
        <div className="muted-box full">
          <strong>{t('system.throttleSectionTitle')}</strong><br />
          {t('system.throttleSectionCopy')}<br />
          {t('system.throttleExplanation')}
        </div>
        <div className="form-field-pair full">
          <label>
            <span className="field-label-row">
              <span>{t('system.successJitterRatio')}</span>
              <InfoHint text={t('system.successJitterRatioHelp')} />
            </span>
            <input
              min="0"
              max="1"
              step="0.01"
              placeholder={t('system.leaveBlank', { value: pollingSettings.defaultSuccessJitterRatio })}
              type="number"
              value={pollingSettingsForm.successJitterRatioOverride}
              onChange={(event) => onPollingFormChange((current) => ({ ...current, successJitterRatioOverride: event.target.value }))}
            />
          </label>
          <label>
            <span className="field-label-row">
              <span>{t('system.maxSuccessJitter')}</span>
              <InfoHint text={t('system.maxSuccessJitterHelp')} />
            </span>
            <input
              placeholder={t('system.leaveBlank', { value: pollingSettings.defaultMaxSuccessJitter })}
              title={formatDurationHint(pollingSettingsForm.maxSuccessJitterOverride || pollingSettings.defaultMaxSuccessJitter, locale) || undefined}
              value={pollingSettingsForm.maxSuccessJitterOverride}
              onChange={(event) => onPollingFormChange((current) => ({ ...current, maxSuccessJitterOverride: event.target.value }))}
            />
          </label>
        </div>
            </div>
          </section>

          <section className="surface-card system-polling-section system-polling-summary-card">
            <div className="system-polling-section-header">
              <div className="system-polling-section-title">{t('system.effectiveSectionTitle')}</div>
              <p className="system-polling-section-copy">{t('system.effectiveSectionCopy')}</p>
            </div>
            <div className="muted-box">
          {t('system.effectivePolling', { value: pollingSettings.effectivePollEnabled ? t('common.yes') : t('common.no') })}<br />
          {t('system.effectiveInterval', { value: pollingSettings.effectivePollInterval })}<br />
          {t('system.effectiveFetchWindow', { value: pollingSettings.effectiveFetchWindow })}<br />
          {t('system.effectiveManualTriggerLimit', {
            count: pollingSettings.effectiveManualTriggerLimitCount,
            seconds: pollingSettings.effectiveManualTriggerLimitWindowSeconds
          })}<br />
          {t('system.sourceHostMinSpacing')}: <DurationValue locale={locale} value={pollingSettings.effectiveSourceHostMinSpacing} /><br />
          {t('system.sourceHostMaxConcurrency')}: {pollingSettings.effectiveSourceHostMaxConcurrency}<br />
          {t('system.destinationProviderMinSpacing')}: <DurationValue locale={locale} value={pollingSettings.effectiveDestinationProviderMinSpacing} /><br />
          {t('system.destinationProviderMaxConcurrency')}: {pollingSettings.effectiveDestinationProviderMaxConcurrency}<br />
          {t('system.throttleLeaseTtl')}: <DurationValue locale={locale} value={pollingSettings.effectiveThrottleLeaseTtl} /><br />
          {t('system.adaptiveThrottleMaxMultiplier')}: {pollingSettings.effectiveAdaptiveThrottleMaxMultiplier}<br />
          {t('system.successJitterRatio')}: {pollingSettings.effectiveSuccessJitterRatio}<br />
          {t('system.maxSuccessJitter')}: <DurationValue locale={locale} value={pollingSettings.effectiveMaxSuccessJitter} /><br />
          {t('system.intervalExamples')}
        </div>
          </section>
        </div>
        <div className="action-row">
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
