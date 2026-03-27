import InfoHint from '../common/InfoHint'
import ModalDialog from '../common/ModalDialog'
import './PreferencesDialog.css'

/**
 * Centralized user preference dialog for language and layout behavior so the
 * header stays compact while preferences remain discoverable.
 */
function PreferencesDialog({
  language,
  languageOptions,
  onClose,
  onLanguageChange,
  onPersistLayoutChange,
  persistLayout,
  savingLayout,
  t
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
          <select disabled={savingLayout} value={language} onChange={(event) => onLanguageChange(event.target.value)}>
            {languageOptions.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </label>
        <label className="preferences-checkbox-row">
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
        {savingLayout ? <div className="section-copy">{t('common.savingLayoutPreference')}</div> : null}
      </div>
    </ModalDialog>
  )
}

export default PreferencesDialog
