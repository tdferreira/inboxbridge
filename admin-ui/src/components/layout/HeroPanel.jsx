import { roleLabel } from '../../lib/formatters'
import LoadingButton from '../common/LoadingButton'
import './HeroPanel.css'

function HeroPanel({
  language,
  languageOptions,
  loadingData,
  onLanguageChange,
  onToggleSecurityPanel,
  onRefresh,
  onSignOut,
  refreshLoading,
  securityPanelVisible,
  session,
  signOutLoading,
  t
}) {
  return (
    <section className="hero-panel">
      <div>
        <div className="eyebrow">{t('hero.eyebrow')}</div>
        <h1>{t('auth.brand')}</h1>
        <p className="section-copy">{t('hero.signedInAs', { username: session.username, role: roleLabel(session.role, language) })}</p>
      </div>
      <div className="action-row">
        <label className="hero-language-select">
          <span>{t('hero.language')}</span>
          <select value={language} onChange={(event) => onLanguageChange(event.target.value)}>
            {languageOptions.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </label>
        <LoadingButton className="secondary" disabled={loadingData} hint={t('hero.refreshHint')} isLoading={refreshLoading} loadingLabel={t('hero.refreshLoading')} onClick={onRefresh}>
          {t('hero.refresh')}
        </LoadingButton>
        <button className="secondary" onClick={onToggleSecurityPanel} title={securityPanelVisible ? t('hero.hideSecurityHint') : t('hero.showSecurityHint')} type="button">
          {securityPanelVisible ? t('hero.hideSecurity') : t('hero.security')}
        </button>
        <LoadingButton className="secondary" hint={t('hero.signOutHint')} isLoading={signOutLoading} loadingLabel={t('hero.signOutLoading')} onClick={onSignOut}>
          {t('hero.signOut')}
        </LoadingButton>
      </div>
    </section>
  )
}

export default HeroPanel
