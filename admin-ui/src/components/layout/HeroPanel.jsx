import { roleLabel } from '../../lib/formatters'
import LoadingButton from '../common/LoadingButton'
import './HeroPanel.css'

function HeroPanel({
  hasUnsavedLayoutChanges = false,
  layoutEditing = false,
  language,
  loadingData,
  onDiscardLayoutChanges,
  onExitLayoutEditing,
  onOpenNotifications,
  onOpenPreferences,
  onOpenSecurityDialog,
  onRefresh,
  onSignOut,
  notificationCount = 0,
  refreshLoading,
  session,
  signOutLoading,
  t
}) {
  return (
    <>
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
          <button className="secondary hero-notifications-button" onClick={onOpenNotifications} title={t('hero.notificationsHint')} type="button">
            <span>{t('hero.notifications')}</span>
            {notificationCount ? <span className="hero-notifications-badge">{notificationCount}</span> : null}
          </button>
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
      {layoutEditing ? (
        <div className="hero-layout-editing-dock" role="region" aria-label={t('hero.layoutEditingActions')}>
          <div className="hero-layout-editing-dock-copy">{t('hero.layoutEditingActive')}</div>
          <div className="hero-layout-editing-dock-actions">
            <button className="secondary" onClick={onDiscardLayoutChanges} title={t('hero.discardLayoutChangesHint')} type="button">
              {hasUnsavedLayoutChanges ? t('hero.discardLayoutChangesDirty') : t('hero.discardLayoutChanges')}
            </button>
            <button className="danger hero-layout-editing-exit-button" onClick={onExitLayoutEditing} title={t('hero.exitLayoutEditingHint')} type="button">
              {t('hero.exitLayoutEditing')}
            </button>
          </div>
        </div>
      ) : null}
    </>
  )
}

export default HeroPanel
