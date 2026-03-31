import SectionCard from './SectionCard'
import LoadingButton from './LoadingButton'

function DeviceLocationPrompt({
  error = '',
  onDismiss,
  onRequestLocation,
  saving = false,
  success = '',
  t
}) {
  return (
    <SectionCard
      actions={(
        <div className="section-card-actions">
          <LoadingButton className="primary" isLoading={saving} loadingLabel={t('deviceLocation.requestLoading')} onClick={onRequestLocation} type="button">
            {t('deviceLocation.request')}
          </LoadingButton>
          <button className="secondary" onClick={onDismiss} type="button">
            {t('deviceLocation.notNow')}
          </button>
        </div>
      )}
      className="utility-prompt-card"
      copy={t('deviceLocation.copy')}
      title={t('deviceLocation.title')}
    >
      {success ? <div className="muted-box utility-prompt-success">{success}</div> : null}
      {error ? <div className="muted-box utility-prompt-error">{error}</div> : null}
      <div className="utility-prompt-note">{t('deviceLocation.note')}</div>
    </SectionCard>
  )
}

export default DeviceLocationPrompt
