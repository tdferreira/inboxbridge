import SectionCard from './SectionCard'
import LoadingButton from './LoadingButton'

function InstallPromptCard({
  canPromptInstall = false,
  installLoading = false,
  onInstall,
  t
}) {
  return (
    <SectionCard
      actions={canPromptInstall ? (
        <LoadingButton className="primary" isLoading={installLoading} loadingLabel={t('pwa.installLoading')} onClick={onInstall} type="button">
          {t('pwa.install')}
        </LoadingButton>
      ) : null}
      className="utility-prompt-card"
      copy={t('pwa.copy')}
      title={t('pwa.title')}
    >
      <div className="utility-prompt-note">{t('pwa.note')}</div>
      <ul className="utility-prompt-list">
        <li>{t('pwa.chromeHint')}</li>
        <li>{t('pwa.safariIosHint')}</li>
        <li>{t('pwa.safariMacHint')}</li>
        <li>{t('pwa.firefoxAndroidHint')}</li>
        <li>{t('pwa.firefoxDesktopHint')}</li>
      </ul>
    </SectionCard>
  )
}

export default InstallPromptCard
