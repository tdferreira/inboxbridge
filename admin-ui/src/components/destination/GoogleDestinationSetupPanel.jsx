import './GoogleDestinationSetupPanel.css'

function GoogleDestinationSetupPanel({ destinationMeta, t }) {
  const redirectUri = destinationMeta?.defaultRedirectUri || `${window.location.origin}/api/google-oauth/callback`

  return (
    <section className="surface-card google-setup-panel">
      <div className="section-title">{t('googleSetup.title')}</div>
      {destinationMeta?.sharedClientConfigured ? (
        <div className="muted-box">
          <strong>{t('googleSetup.sharedTitle')}</strong><br />
          {t('googleSetup.sharedBody')}
        </div>
      ) : null}
      <div className="muted-box">
        <p>{t('googleSetup.body')}</p>
      </div>
      <div className="muted-box">
        <strong>{t('googleSetup.whatTitle')}</strong><br />
        {t('googleSetup.step1')}<br />
        {t('googleSetup.step2')}<br />
        {t('googleSetup.step3', { value: redirectUri })}<br />
        {t('googleSetup.step4')}<br />
        {t('googleSetup.step5')}
      </div>
      <div className="muted-box">
        <strong>{t('googleSetup.errorTitle')}</strong><br />
        <code>{t('googleSetup.errorBody')}</code>
      </div>
    </section>
  )
}

export default GoogleDestinationSetupPanel
