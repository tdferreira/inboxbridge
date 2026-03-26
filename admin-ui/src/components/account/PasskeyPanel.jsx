import LoadingButton from '../common/LoadingButton'
import { formatDate } from '../../lib/formatters'
import './PasskeyPanel.css'

/**
 * Browser-facing WebAuthn controls for registering and removing passkeys on the
 * authenticated account.
 */
function PasskeyPanel({
  createLoading,
  deleteLoadingId,
  onCreatePasskey,
  onDeletePasskey,
  onPasskeyLabelChange,
  passwordConfigured,
  passkeyLabel,
  passkeys,
  supported,
  t,
  locale
}) {
  return (
    <section className="surface-card passkey-panel" id="passkey-panel-section" tabIndex="-1">
      <div className="panel-header">
        <div>
          <div className="section-title">{t('passkey.title')}</div>
          <p className="section-copy">
            {t('passkey.copy')}
          </p>
        </div>
        <div className={`status-pill ${supported ? 'tone-success' : 'tone-error'}`}>
          {supported ? t('common.browserReady') : t('common.unavailable')}
        </div>
      </div>

      <form className="stack-form" onSubmit={onCreatePasskey}>
        <label>
          <span>{t('passkey.label')}</span>
          <input
            placeholder={t('passkey.placeholder')}
            value={passkeyLabel}
            onChange={(event) => onPasskeyLabelChange(event.target.value)}
          />
        </label>
        <LoadingButton className="primary" disabled={!supported} isLoading={createLoading} loadingLabel={t('passkey.registerLoading')} type="submit">
          {t('passkey.register')}
        </LoadingButton>
      </form>

      {!supported ? (
        <div className="muted-box">
          {t('passkey.unsupported')}
        </div>
      ) : null}

      <div className="detail-stack">
        {passkeys.length > 0 ? passkeys.map((passkey) => (
          <div key={passkey.id} className="passkey-card">
            <div>
              <strong>{passkey.label}</strong><br />
              {t('passkey.created', { value: formatDate(passkey.createdAt, locale) })}<br />
              {t('passkey.lastUsed', { value: formatDate(passkey.lastUsedAt, locale) })}<br />
              {t('passkey.discoverable', { value: t(passkey.discoverable ? 'common.yes' : 'common.no') })} · {t('passkey.backupEligible', { value: t(passkey.backupEligible ? 'common.yes' : 'common.no') })} · {t('passkey.backedUp', { value: t(passkey.backedUp ? 'common.yes' : 'common.no') })}
            </div>
            <LoadingButton
              className="secondary"
              disabled={!passwordConfigured && passkeys.length === 1}
              isLoading={deleteLoadingId === passkey.id}
              loadingLabel={t('passkey.removeLoading')}
              onClick={() => onDeletePasskey(passkey.id)}
              type="button"
            >
              {t('passkey.remove')}
            </LoadingButton>
          </div>
        )) : (
          <div className="muted-box">{t('passkey.none')}</div>
        )}
      </div>
      {!passwordConfigured && passkeys.length === 1 ? (
        <div className="muted-box">
          {t('passkey.onlyMethod')}
        </div>
      ) : null}
    </section>
  )
}

export default PasskeyPanel
