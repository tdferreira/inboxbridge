import { useMemo, useState } from 'react'
import Banner from '@/shared/components/Banner'
import LoadingButton from '@/shared/components/LoadingButton'
import ModalDialog from '@/shared/components/ModalDialog'

function SecretReencryptionDialog({
  onClose,
  onConfirm,
  onOptionsChange,
  pending = false,
  secretManagementStatus,
  secretReencryptOptions,
  t
}) {
  const [acknowledgements, setAcknowledgements] = useState({
    verifiedNewKey: false,
    preservedLegacyKeys: false,
    understoodRisk: false
  })

  const allAcknowledged = useMemo(
    () => Object.values(acknowledgements).every(Boolean),
    [acknowledgements]
  )

  function updateAcknowledgement(key, checked) {
    setAcknowledgements((current) => ({ ...current, [key]: checked }))
  }

  return (
    <ModalDialog closeDisabled={pending} onClose={onClose} title={t('authSecurity.secretManagementReencryptDialogTitle')}>
      <div className="detail-stack">
        <p className="section-copy">{t('authSecurity.secretManagementReencryptDialogIntro')}</p>

        <Banner tone="warning">
          <div className="detail-stack">
            <strong>{t('authSecurity.secretManagementReencryptDialogRiskTitle')}</strong>
            <span>{t('authSecurity.secretManagementReencryptDialogRiskBody')}</span>
          </div>
        </Banner>

        <div className="muted-box detail-stack">
          <strong>{t('authSecurity.secretManagementReencryptDialogStatusTitle')}</strong>
          <div className="polling-statistics-breakdown">
            <div><span>{t('authSecurity.secretManagementActiveKey')}</span><strong>{secretManagementStatus?.activeKeyId || secretManagementStatus?.activeKeyVersion || t('common.unavailable')}</strong></div>
            <div><span>{t('authSecurity.secretManagementLegacyKeys')}</span><strong>{Array.isArray(secretManagementStatus?.configuredLegacyKeyIds) && secretManagementStatus.configuredLegacyKeyIds.length > 0 ? secretManagementStatus.configuredLegacyKeyIds.join(', ') : t('authSecurity.secretManagementNoLegacyKeys')}</strong></div>
            <div><span>{t('authSecurity.secretManagementUnavailableRecords')}</span><strong>{secretManagementStatus?.unavailableKeyRecordCount ?? 0}</strong></div>
          </div>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementReencryptDialogStepsTitle')}</strong>
          <ol className="detail-stack">
            <li>{t('authSecurity.secretManagementReencryptDialogStep1')}</li>
            <li>{t('authSecurity.secretManagementReencryptDialogStep2')}</li>
            <li>{t('authSecurity.secretManagementReencryptDialogStep3')}</li>
            <li>{t('authSecurity.secretManagementReencryptDialogStep4')}</li>
          </ol>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementReencryptDialogFollowUpTitle')}</strong>
          <div className="polling-statistics-breakdown">
            <label className="checkbox-row">
              <input
                checked={Boolean(secretReencryptOptions?.revokeBrowserExtensionSessions)}
                onChange={(event) => onOptionsChange?.((current) => ({
                  ...current,
                  revokeBrowserExtensionSessions: event.target.checked
                }))}
                type="checkbox"
              />
              <span>{t('authSecurity.secretManagementReencryptRevokeExtensionSessions')}</span>
            </label>
            <label className="checkbox-row">
              <input
                checked={Boolean(secretReencryptOptions?.revokeRemoteSessions)}
                onChange={(event) => onOptionsChange?.((current) => ({
                  ...current,
                  revokeRemoteSessions: event.target.checked
                }))}
                type="checkbox"
              />
              <span>{t('authSecurity.secretManagementReencryptRevokeRemoteSessions')}</span>
            </label>
            <label className="checkbox-row">
              <input
                checked={Boolean(secretReencryptOptions?.clearCachedOAuthAccessTokens)}
                onChange={(event) => onOptionsChange?.((current) => ({
                  ...current,
                  clearCachedOAuthAccessTokens: event.target.checked
                }))}
                type="checkbox"
              />
              <span>{t('authSecurity.secretManagementReencryptClearCachedOAuthAccessTokens')}</span>
            </label>
          </div>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementReencryptDialogChecklistTitle')}</strong>
          <div className="detail-stack">
            <label className="checkbox-row">
              <input
                checked={acknowledgements.verifiedNewKey}
                onChange={(event) => updateAcknowledgement('verifiedNewKey', event.target.checked)}
                type="checkbox"
              />
              <span>{t('authSecurity.secretManagementReencryptDialogAcknowledgeNewKey')}</span>
            </label>
            <label className="checkbox-row">
              <input
                checked={acknowledgements.preservedLegacyKeys}
                onChange={(event) => updateAcknowledgement('preservedLegacyKeys', event.target.checked)}
                type="checkbox"
              />
              <span>{t('authSecurity.secretManagementReencryptDialogAcknowledgeLegacyKeys')}</span>
            </label>
            <label className="checkbox-row">
              <input
                checked={acknowledgements.understoodRisk}
                onChange={(event) => updateAcknowledgement('understoodRisk', event.target.checked)}
                type="checkbox"
              />
              <span>{t('authSecurity.secretManagementReencryptDialogAcknowledgeRisk')}</span>
            </label>
          </div>
        </div>

        <div className="action-row">
          <button className="secondary" disabled={pending} onClick={onClose} type="button">
            {t('common.cancel')}
          </button>
          <LoadingButton
            className="danger"
            disabled={!allAcknowledged}
            isLoading={pending}
            loadingLabel={t('authSecurity.secretManagementReencryptLoading')}
            onClick={onConfirm}
            type="button"
          >
            {t('authSecurity.secretManagementReencrypt')}
          </LoadingButton>
        </div>
      </div>
    </ModalDialog>
  )
}

export default SecretReencryptionDialog
