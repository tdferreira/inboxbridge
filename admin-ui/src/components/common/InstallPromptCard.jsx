import SectionCard from './SectionCard'
import LoadingButton from './LoadingButton'

function InstallPromptCard({
  installLoading = false,
  onInstall,
  t
}) {
  return (
    <SectionCard
      actions={(
        <LoadingButton className="primary" isLoading={installLoading} loadingLabel={t('pwa.installLoading')} onClick={onInstall} type="button">
          {t('pwa.install')}
        </LoadingButton>
      )}
      className="utility-prompt-card"
      copy={t('pwa.copy')}
      title={t('pwa.title')}
    >
      <div className="utility-prompt-note">{t('pwa.note')}</div>
    </SectionCard>
  )
}

export default InstallPromptCard
