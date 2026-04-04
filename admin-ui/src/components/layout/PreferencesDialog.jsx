import InfoHint from '../common/InfoHint'
import LanguageMenuButton from '../common/LanguageMenuButton'
import ModalDialog from '../common/ModalDialog'
import './PreferencesDialog.css'

/**
 * Centralized user preference dialog for language and layout behavior so the
 * header stays compact while preferences remain discoverable.
 */
function PreferencesDialog({
  canHideQuickSetup,
  detectedTimeZone,
  layoutEditEnabled,
  language,
  languageOptions,
  onClose,
  onExitLayoutEditing,
  onStartLayoutEditing,
  onLanguageChange,
  onPersistLayoutChange,
  onQuickSetupVisibilityChange,
  onResetLayout,
  onTimeZoneChange,
  onTimeZoneModeChange,
  persistLayout,
  quickSetupVisible,
  savingLayout,
  selectableTimeZones,
  t,
  timezone,
  timezoneMode
}) {
  return (
    <ModalDialog
      closeLabel={t('preferences.close')}
      onClose={onClose}
      title={t('preferences.title')}
    >
      <div className="preferences-dialog">
        <label>
          <span>{t('preferences.language')}</span>
          <LanguageMenuButton
            ariaLabel={t('preferences.language')}
            currentLanguage={language}
            disabled={savingLayout}
            menuAlign="start"
            onChange={onLanguageChange}
            options={languageOptions}
          />
        </label>
        <label>
          <span>{t('preferences.timezoneMode')}</span>
          <select
            disabled={savingLayout}
            onChange={(event) => onTimeZoneModeChange(event.target.value)}
            value={timezoneMode}
          >
            <option value="AUTO">{t('preferences.timezoneAuto')}</option>
            <option value="MANUAL">{t('preferences.timezoneManual')}</option>
          </select>
        </label>
        {timezoneMode === 'AUTO' ? (
          <div className="section-copy">{t('preferences.timezoneDetected', { value: detectedTimeZone })}</div>
        ) : (
          <label>
            <span className="field-label-row">
              <span>{t('preferences.timezone')}</span>
              <InfoHint text={t('preferences.timezoneHelp')} />
            </span>
            <select
              disabled={savingLayout}
              onChange={(event) => onTimeZoneChange(event.target.value)}
              value={timezone || detectedTimeZone}
            >
              {selectableTimeZones.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </label>
        )}
        <label className="checkbox-row">
          <input
            checked={quickSetupVisible}
            disabled={savingLayout || !canHideQuickSetup}
            onChange={(event) => onQuickSetupVisibilityChange(event.target.checked)}
            type="checkbox"
          />
          <span className="field-label-row">
            <span>{t('preferences.showQuickSetup')}</span>
            <InfoHint text={canHideQuickSetup ? t('preferences.showQuickSetupHelp') : t('preferences.showQuickSetupForcedHelp')} />
          </span>
        </label>
        <div className="preferences-checkbox-row">
          <span className="field-label-row">
            <span>{t('preferences.layout')}</span>
            <InfoHint text={t('preferences.editLayoutHelp')} />
          </span>
        </div>
        <label className="checkbox-row">
          <input
            checked={persistLayout}
            disabled={savingLayout}
            onChange={(event) => onPersistLayoutChange(event.target.checked)}
            type="checkbox"
          />
          <span className="field-label-row">
            <span>{t('preferences.rememberLayout')}</span>
            <InfoHint text={t('preferences.rememberLayoutHelp')} />
          </span>
        </label>
        <div className="action-row">
          <button
            className="secondary"
            disabled={savingLayout}
            onClick={layoutEditEnabled ? onExitLayoutEditing : onStartLayoutEditing}
            title={layoutEditEnabled ? t('preferences.exitLayoutEditingHint') : t('preferences.editLayoutHint')}
            type="button"
          >
            {layoutEditEnabled ? t('preferences.exitLayoutEditing') : t('preferences.editLayout')}
          </button>
          <button className="secondary" disabled={savingLayout} onClick={onResetLayout} title={t('preferences.resetLayoutHint')} type="button">
            {t('preferences.resetLayout')}
          </button>
        </div>
        {savingLayout ? <div className="section-copy">{t('common.savingLayoutPreference')}</div> : null}
      </div>
    </ModalDialog>
  )
}

export default PreferencesDialog
