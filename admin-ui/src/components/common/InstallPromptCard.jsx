import SectionCard from './SectionCard'
import LoadingButton from './LoadingButton'

function InstallPromptCard({
  canPromptInstall = false,
  copy = '',
  dismissInBody = false,
  dismissLabel = '',
  hints = null,
  installActionLabel = '',
  installLoading = false,
  note = '',
  showInstallAction = false,
  onDismiss,
  onInstall,
  title = '',
  t
}) {
  return (
    <SectionCard
      actions={(
        <>
          {onDismiss && !dismissInBody ? (
            <button className="secondary" onClick={onDismiss} type="button">
              {dismissLabel || t('pwa.notNow')}
            </button>
          ) : null}
          {showInstallAction && canPromptInstall ? (
            <LoadingButton className="primary" isLoading={installLoading} loadingLabel={t('pwa.installLoading')} onClick={onInstall} type="button">
              {installActionLabel || t('pwa.install')}
            </LoadingButton>
          ) : null}
          {showInstallAction && !canPromptInstall ? (
            <button className="primary" onClick={onInstall} type="button">
              {installActionLabel || t('pwa.install')}
            </button>
          ) : null}
        </>
      )}
      className="utility-prompt-card"
      copy={copy || t('pwa.copy')}
      title={title || t('pwa.title')}
    >
      <div className="utility-prompt-note">{note || t('pwa.note')}</div>
      <ul className="utility-prompt-list">
        {(hints || [
          t('pwa.chromeHint'),
          t('pwa.safariIosHint'),
          t('pwa.safariMacHint'),
          t('pwa.firefoxAndroidHint'),
          t('pwa.firefoxDesktopHint')
        ]).map((hint) => (
          <li key={hint}>{hint}</li>
        ))}
      </ul>
      {onDismiss && dismissInBody ? (
        <div className="utility-prompt-footer">
          <button className="secondary" onClick={onDismiss} type="button">
            {dismissLabel || t('pwa.notNow')}
          </button>
        </div>
      ) : null}
    </SectionCard>
  )
}

export default InstallPromptCard
