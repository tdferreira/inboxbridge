import LanguageMenuButton from '@/shared/components/LanguageMenuButton'
import ModalDialog from '@/shared/components/ModalDialog'

/**
 * Compact preferences dialog for the `/remote` surface so users can update
 * personal display settings without switching back to the full admin UI.
 */
function RemotePreferencesDialog({
  language,
  languageOptions,
  onClose,
  onLanguageChange,
  onThemeModeChange,
  saving = false,
  t,
  themeMode
}) {
  return (
    <ModalDialog
      closeDisabled={saving}
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
            disabled={saving}
            menuAlign="start"
            onChange={onLanguageChange}
            options={languageOptions}
          />
        </label>
        <label>
          <span>{t('preferences.theme')}</span>
          <select
            disabled={saving}
            onChange={(event) => onThemeModeChange(event.target.value)}
            value={themeMode}
          >
            <option value="SYSTEM">{t('preferences.themeSystem')}</option>
            <option value="LIGHT_GREEN">{t('preferences.themeLightGreen')}</option>
            <option value="LIGHT_BLUE">{t('preferences.themeLightBlue')}</option>
            <option value="DARK_GREEN">{t('preferences.themeDarkGreen')}</option>
            <option value="DARK_BLUE">{t('preferences.themeDarkBlue')}</option>
          </select>
        </label>
        {saving ? <div className="section-copy">{t('common.savingLayoutPreference')}</div> : null}
      </div>
    </ModalDialog>
  )
}

export default RemotePreferencesDialog
