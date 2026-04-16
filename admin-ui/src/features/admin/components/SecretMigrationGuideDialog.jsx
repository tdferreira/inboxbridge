import ModalDialog from '@/shared/components/ModalDialog'

const EMPTY_ITEMS = []

function CheckStatusIcon({ satisfied, t }) {
  const label = satisfied
    ? t('authSecurity.secretManagementRequirementSatisfied')
    : t('authSecurity.secretManagementRequirementNotSatisfied')
  const stroke = satisfied ? 'var(--accent)' : 'var(--danger)'

  return (
    <span
      aria-label={label}
      className={`secret-reencryption-requirement-status-icon ${satisfied ? 'is-satisfied' : 'is-unsatisfied'}`}
      role="img"
      title={label}
    >
      <svg aria-hidden="true" fill="none" height="22" viewBox="0 0 24 24" width="22">
        <circle cx="12" cy="12" fill="transparent" r="9" stroke={stroke} strokeWidth="1.8" />
        {satisfied ? (
          <path d="M7.5 12.4 10.4 15.2 16.7 8.9" stroke={stroke} strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.2" />
        ) : (
          <>
            <path d="M8.6 8.6 15.4 15.4" stroke={stroke} strokeLinecap="round" strokeWidth="2.2" />
            <path d="M15.4 8.6 8.6 15.4" stroke={stroke} strokeLinecap="round" strokeWidth="2.2" />
          </>
        )}
      </svg>
    </span>
  )
}

export default function SecretMigrationGuideDialog({
  guide,
  loading = false,
  onClose,
  onContinueReencryption,
  secretManagementStatus,
  t
}) {
  const checks = Array.isArray(guide?.checks) ? guide.checks : EMPTY_ITEMS
  const beforeSwitchSteps = Array.isArray(guide?.beforeSwitchSteps) ? guide.beforeSwitchSteps : EMPTY_ITEMS
  const switchSteps = Array.isArray(guide?.switchSteps) ? guide.switchSteps : EMPTY_ITEMS
  const afterSwitchSteps = Array.isArray(guide?.afterSwitchSteps) ? guide.afterSwitchSteps : EMPTY_ITEMS
  const reencryptionRequirements = guide?.current && Array.isArray(guide?.postSwitchRequirements)
    ? guide.postSwitchRequirements
    : EMPTY_ITEMS
  const canContinueToReencryption = Boolean(guide?.current && guide?.continueReady)

  function focusTarget(targetId) {
    if (!targetId || typeof document === 'undefined') {
      return
    }
    const target = document.getElementById(targetId)
    if (!target) {
      return
    }
    target.scrollIntoView({ behavior: 'smooth', block: 'center' })
    if (typeof target.focus === 'function') {
      target.focus({ preventScroll: true })
    }
  }

  function handleContinueReencryption() {
    onClose?.()
    onContinueReencryption?.()
  }

  return (
    <ModalDialog closeDisabled={loading} onClose={onClose} size="wide" title={guide?.title || t('authSecurity.secretManagementMigrationGuideTitle')}>
      <div className="detail-stack">
        <p className="section-copy">{guide?.summary || t('authSecurity.secretManagementMigrationGuideLoading')}</p>

        <div className="muted-box detail-stack">
          <strong>{t('authSecurity.secretManagementMigrationExecutionTitle')}</strong>
          <span>{guide?.executionMethod || t('authSecurity.secretManagementMigrationGuideLoading')}</span>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementMigrationChecksTitle')}</strong>
          {checks.length === 0 ? (
            <span>{t('authSecurity.secretManagementMigrationGuideLoading')}</span>
          ) : (
            <div className="secret-reencryption-requirements-grid">
              {checks.map((check) => (
                <article className="muted-box secret-reencryption-requirement-card expanded" key={check.checkId}>
                  <div className="secret-reencryption-requirement-toggle">
                    <div className="secret-reencryption-requirement-copy">
                      <strong>{check.title}</strong>
                      <span>{check.detail}</span>
                    </div>
                    <div className="secret-reencryption-requirement-meta">
                      <CheckStatusIcon satisfied={check.satisfied} t={t} />
                      <span className={`status-pill ${check.satisfied ? 'status-ok' : 'tone-bad'}`}>
                        {check.satisfied ? t('authSecurity.secretManagementRequirementSatisfied') : t('authSecurity.secretManagementRequirementNotSatisfied')}
                      </span>
                    </div>
                  </div>
                  {Array.isArray(check.configReferences) && check.configReferences.length > 0 ? (
                    <div className="secret-reencryption-requirement-body detail-stack">
                      <strong>{t('authSecurity.secretManagementRequirementConfigTitle')}</strong>
                      <div className="secret-reencryption-config-list">
                        {check.configReferences.map((reference) => (
                          <code className="secret-reencryption-config-chip" key={`${check.checkId}:${reference}`}>{reference}</code>
                        ))}
                      </div>
                    </div>
                  ) : null}
                </article>
              ))}
            </div>
          )}
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementMigrationBeforeSwitchTitle')}</strong>
          <ol className="detail-stack secret-reencryption-procedure-list">
            {beforeSwitchSteps.map((step) => <li key={`before:${step}`}>{step}</li>)}
          </ol>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementMigrationSwitchTitle')}</strong>
          <ol className="detail-stack secret-reencryption-procedure-list">
            {switchSteps.map((step) => <li key={`switch:${step}`}>{step}</li>)}
          </ol>
        </div>

        <div className="detail-stack">
          <strong>{t('authSecurity.secretManagementMigrationAfterSwitchTitle')}</strong>
          <ol className="detail-stack secret-reencryption-procedure-list">
            {afterSwitchSteps.map((step) => <li key={`after:${step}`}>{step}</li>)}
          </ol>
        </div>

        {guide?.current ? (
          <div className="detail-stack">
            <strong>{t('authSecurity.secretManagementMigrationPostSwitchChecksTitle')}</strong>
            {reencryptionRequirements.length > 0 ? (
              <div className="secret-reencryption-requirements-grid">
                {reencryptionRequirements.map((requirement) => (
                  <article className="muted-box secret-reencryption-requirement-card expanded" key={requirement.requirementId}>
                    <div className="secret-reencryption-requirement-toggle">
                      <div className="secret-reencryption-requirement-copy">
                        <strong>{requirement.title}</strong>
                        <span>{requirement.detail}</span>
                      </div>
                      <div className="secret-reencryption-requirement-meta">
                        <CheckStatusIcon satisfied={requirement.satisfied} t={t} />
                        <span className={`status-pill ${requirement.satisfied ? 'status-ok' : 'tone-bad'}`}>
                          {requirement.satisfied ? t('authSecurity.secretManagementRequirementSatisfied') : t('authSecurity.secretManagementRequirementNotSatisfied')}
                        </span>
                      </div>
                    </div>
                    <div className="secret-reencryption-requirement-body detail-stack">
                      {Array.isArray(requirement.remediationSteps) && requirement.remediationSteps.length > 0 ? (
                        <div className="detail-stack">
                          <strong>{t('authSecurity.secretManagementRequirementStepsTitle')}</strong>
                          <ul className="detail-stack secret-reencryption-detail-list">
                            {requirement.remediationSteps.map((step) => <li key={step}>{step}</li>)}
                          </ul>
                        </div>
                      ) : null}
                      {Array.isArray(requirement.configReferences) && requirement.configReferences.length > 0 ? (
                        <div className="detail-stack">
                          <strong>{t('authSecurity.secretManagementRequirementConfigTitle')}</strong>
                          <div className="secret-reencryption-config-list">
                            {requirement.configReferences.map((reference) => (
                              <code className="secret-reencryption-config-chip" key={`${requirement.requirementId}:${reference}`}>{reference}</code>
                            ))}
                          </div>
                        </div>
                      ) : null}
                      {requirement.actionTargetId && requirement.actionLabel ? (
                        <div className="secret-reencryption-requirement-actions">
                          <button
                            className="secondary"
                            onClick={() => focusTarget(requirement.actionTargetId)}
                            type="button"
                          >
                            {requirement.actionLabel}
                          </button>
                        </div>
                      ) : null}
                    </div>
                  </article>
                ))}
              </div>
            ) : (
              <span>{t('authSecurity.secretManagementMigrationGuideLoading')}</span>
            )}
            <p className="section-copy">
              {canContinueToReencryption
                ? t('authSecurity.secretManagementMigrationContinueReady')
                : t('authSecurity.secretManagementMigrationContinueBlocked')}
            </p>
            {canContinueToReencryption ? (
              <div className="action-row">
                <button className="secondary" onClick={handleContinueReencryption} type="button">
                  {t('authSecurity.secretManagementMigrationContinueAction')}
                </button>
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </ModalDialog>
  )
}
