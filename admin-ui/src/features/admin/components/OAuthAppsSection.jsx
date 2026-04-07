import { useEffect, useState } from 'react'
import LoadingButton from '@/shared/components/LoadingButton'
import CollapsibleSection from '@/shared/components/CollapsibleSection'

function providerExpandedState(settings) {
  return {
    google: !(settings?.googleClientId && settings?.googleClientSecretConfigured && settings?.googleRedirectUri),
    microsoft: !(settings?.microsoftClientId && settings?.microsoftClientSecretConfigured && settings?.microsoftRedirectUri)
  }
}

function OAuthProviderCard({
  children,
  expanded,
  onEdit,
  onToggle,
  summary,
  t,
  title
}) {
  return (
    <article className={`surface-card oauth-app-card ${expanded ? 'expanded' : ''}`}>
      <div className="oauth-app-card-summary">
        <button
          className="oauth-app-card-main"
          onClick={onToggle}
          title={t('system.oauthCardToggle', { provider: title })}
          type="button"
        >
          <div>
            <div className="oauth-app-card-title-row">
              <strong>{title}</strong>
            </div>
            <div className="section-copy">{summary}</div>
          </div>
        </button>
      </div>

      {expanded ? (
        <div className="detail-stack">
          {children}
          <div className="action-row">
            <LoadingButton className="secondary" onClick={onEdit} type="button">
              {t('system.oauthProviderEdit', { provider: title })}
            </LoadingButton>
          </div>
        </div>
      ) : null}
    </article>
  )
}

function OAuthAppsSection({
  collapsed,
  collapseLoading,
  oauthSettings,
  onCollapseToggle,
  onEditGoogle,
  onEditMicrosoft,
  sectionLoading = false,
  t
}) {
  const [expandedCards, setExpandedCards] = useState(() => providerExpandedState(oauthSettings))

  useEffect(() => {
    setExpandedCards(providerExpandedState(oauthSettings))
  }, [
    oauthSettings?.googleClientId,
    oauthSettings?.googleClientSecretConfigured,
    oauthSettings?.googleRedirectUri,
    oauthSettings?.microsoftClientId,
    oauthSettings?.microsoftClientSecretConfigured,
    oauthSettings?.microsoftRedirectUri
  ])

  function toggleCard(provider) {
    setExpandedCards((current) => ({ ...current, [provider]: !current[provider] }))
  }

  return (
    <CollapsibleSection
      className="system-dashboard-section"
      collapsed={collapsed}
      collapseLoading={collapseLoading}
      copy={t('system.oauthAppsSectionCopy')}
      id="oauth-apps-section"
      onCollapseToggle={onCollapseToggle}
      sectionLoading={sectionLoading}
      t={t}
      title={t('system.oauthAppsSectionTitle')}
    >
      <div className="oauth-apps-stack">
        <OAuthProviderCard
          expanded={expandedCards.google}
          onEdit={onEditGoogle}
          onToggle={() => toggleCard('google')}
          summary={t('system.googleClientSetupSummary')}
          t={t}
          title={t('system.oauthGoogleTitle')}
        >
          <div className="polling-statistics-breakdown">
            <div><span>{t('system.googleClientId')}</span><strong>{t(oauthSettings?.googleClientId ? 'common.yes' : 'common.no')}</strong></div>
            <div><span>{t('system.googleClientSecret')}</span><strong>{t(oauthSettings?.googleClientSecretConfigured ? 'common.yes' : 'common.no')}</strong></div>
            <div><span>{t('system.googleRedirectUri')}</span><strong>{oauthSettings?.googleRedirectUri || t('common.unavailable')}</strong></div>
          </div>
          <p className="section-copy">{t('system.googleClientUsageHelp')}</p>
        </OAuthProviderCard>

        <OAuthProviderCard
          expanded={expandedCards.microsoft}
          onEdit={onEditMicrosoft}
          onToggle={() => toggleCard('microsoft')}
          summary={t('system.microsoftClientSetupSummary')}
          t={t}
          title={t('system.oauthMicrosoftTitle')}
        >
          <div className="polling-statistics-breakdown">
            <div><span>{t('system.microsoftClientId')}</span><strong>{t(oauthSettings?.microsoftClientId ? 'common.yes' : 'common.no')}</strong></div>
            <div><span>{t('system.microsoftClientSecret')}</span><strong>{t(oauthSettings?.microsoftClientSecretConfigured ? 'common.yes' : 'common.no')}</strong></div>
            <div><span>{t('system.microsoftRedirectUri')}</span><strong>{oauthSettings?.microsoftRedirectUri || t('common.unavailable')}</strong></div>
          </div>
        </OAuthProviderCard>
      </div>
    </CollapsibleSection>
  )
}

export default OAuthAppsSection
