import { roleLabel } from '../../lib/formatters'
import LoadingButton from '../common/LoadingButton'
import './HeroPanel.css'

function HeroPanel({
  layoutEditing = false,
  language,
  loadingData,
  onExitLayoutEditing,
  onOpenPreferences,
  onOpenSecurityDialog,
  onRefresh,
  onSignOut,
  refreshLoading,
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
        <LoadingButton className="secondary" disabled={loadingData} hint={t('hero.refreshHint')} isLoading={refreshLoading} loadingLabel={t('hero.refreshLoading')} onClick={onRefresh}>
          {t('hero.refresh')}
        </LoadingButton>
        {layoutEditing ? (
          <button className="secondary" onClick={onExitLayoutEditing} title={t('hero.exitLayoutEditingHint')} type="button">
            {t('hero.exitLayoutEditing')}
          </button>
        ) : null}
        <button className="secondary" onClick={onOpenPreferences} title={t('hero.preferencesHint')} type="button">
          {t('hero.preferences')}
        </button>
        <button className="secondary" onClick={onOpenSecurityDialog} title={t('hero.showSecurityHint')} type="button">
          {t('hero.security')}
        </button>
        <LoadingButton className="secondary" hint={t('hero.signOutHint')} isLoading={signOutLoading} loadingLabel={t('hero.signOutLoading')} onClick={onSignOut}>
          {t('hero.signOut')}
        </LoadingButton>
      </div>
    </section>
  )
}

export default HeroPanel
