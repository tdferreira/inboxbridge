import InfoHint from '../common/InfoHint'
import LoadingButton from '../common/LoadingButton'
import PaneToggleButton from '../common/PaneToggleButton'
import GoogleDestinationSetupPanel from './GoogleDestinationSetupPanel'
import { DESTINATION_PROVIDER_PRESETS, findDestinationProviderPreset, normalizeDestinationProviderConfig } from '../../lib/emailProviderPresets'
import './DestinationMailboxSection.css'

function LabeledHint({ helpText, label }) {
  return (
    <span className="field-label-row">
      <span>{label}</span>
      <InfoHint text={helpText} />
    </span>
  )
}

/**
 * Renders the destination mailbox area in two modes: admins can edit the
 * destination settings, while regular users only see connection status plus
 * the provider OAuth action when it applies.
 */
function DestinationMailboxSection({
  collapsed,
  collapseLoading,
  destinationConfig,
  destinationMeta,
  isAdmin,
  oauthLoading,
  unlinkLoading,
  locale,
  onCollapseToggle,
  onConnectOAuth,
  onUnlinkOAuth,
  onSave,
  saveLoading,
  sectionLoading = false,
  setDestinationConfig,
  t
}) {
  const resolvedDestinationConfig = normalizeDestinationProviderConfig(destinationConfig)
  const provider = resolvedDestinationConfig.provider || 'GMAIL_API'
  const isGmailProvider = provider === 'GMAIL_API'
  const isOutlookProvider = provider === 'OUTLOOK_IMAP'
  const savedProvider = destinationMeta?.provider || 'GMAIL_API'
  const savedProviderPreset = findDestinationProviderPreset(savedProvider)
  const savedIsGmailProvider = savedProvider === 'GMAIL_API'
  const usesMicrosoftOAuth = !isGmailProvider && resolvedDestinationConfig.authMethod === 'OAUTH2' && resolvedDestinationConfig.oauthProvider === 'MICROSOFT'
  const usesPassword = !isGmailProvider && !usesMicrosoftOAuth
  const hasLinkedDestination = Boolean(destinationMeta?.linked)
  const currentProviderConnected = hasLinkedDestination && savedProvider === provider
  const connectLabel = isGmailProvider
    ? t(currentProviderConnected ? 'destination.gmailReconnect' : 'destination.gmailConnect')
    : t(currentProviderConnected ? 'destination.microsoftReconnect' : 'destination.microsoftConnect')
  const connectLoadingLabel = isGmailProvider
    ? t(currentProviderConnected ? 'destination.gmailReconnectLoading' : 'destination.gmailConnectLoading')
    : t(currentProviderConnected ? 'destination.microsoftReconnectLoading' : 'destination.microsoftConnectLoading')
  const providerPreset = findDestinationProviderPreset(provider)
  const showOAuthButton = isGmailProvider || usesMicrosoftOAuth
  const hasSidebar = !collapsed && isAdmin && isGmailProvider

  function updateProvider(nextProvider) {
    const preset = findDestinationProviderPreset(nextProvider)
    setDestinationConfig((current) => normalizeDestinationProviderConfig({
      ...current,
      provider: nextProvider,
      host: preset.values?.host ?? '',
      port: preset.values?.port ?? 993,
      tls: preset.values?.tls ?? true,
      authMethod: preset.values?.authMethod ?? 'PASSWORD',
      oauthProvider: preset.values?.oauthProvider ?? 'NONE',
      username: preset.values?.username ?? '',
      password: '',
      folder: preset.values?.folder ?? 'INBOX'
    }))
  }

  return (
    <section className={`app-columns ${hasSidebar ? '' : 'app-columns-single'}`.trim()}>
      <section className="surface-card destination-mailbox-panel section-with-corner-toggle" id="destination-mailbox-section" tabIndex="-1">
        <div className="panel-header">
          <div>
            <div className="section-title">{t('destination.title')}</div>
            <p className="section-copy">{t(isGmailProvider ? (isAdmin ? 'destination.gmailAdminCopy' : 'destination.gmailUserCopy') : 'destination.imapCopy')}</p>
          </div>
          <div className="panel-header-actions">
            {showOAuthButton ? (
              <LoadingButton className="primary" isLoading={oauthLoading} loadingLabel={connectLoadingLabel} onClick={onConnectOAuth} type="button">
                {connectLabel}
              </LoadingButton>
            ) : null}
            {hasLinkedDestination ? (
              <LoadingButton className="secondary" isLoading={unlinkLoading} loadingLabel={t('destination.unlinkLoading')} onClick={onUnlinkOAuth} type="button">
                {t('destination.unlink')}
              </LoadingButton>
            ) : null}
          </div>
        </div>
        <PaneToggleButton className="pane-toggle-button-corner" collapseLabel={t('common.collapseSection')} collapsed={collapsed} disabled={collapseLoading} expandLabel={t('common.expandSection')} isLoading={collapseLoading} onClick={onCollapseToggle} />
        {sectionLoading ? (
          <div className="section-refresh-indicator" role="status">
            <span aria-hidden="true" className="section-refresh-spinner" />
            {t('common.refreshingSection')}
          </div>
        ) : null}
        {!collapsed ? (
          <>
            <div className="destination-credential-status muted-box">
              <strong>{t('destination.savedStatus')}</strong><br />
              {t('destination.providerValue', { value: savedProviderPreset.label })}<br />
              {t('destination.connectionStatus', { value: t(hasLinkedDestination ? 'common.yes' : 'common.no') })}<br />
              {savedIsGmailProvider ? (
                <>
                  {t('gmail.sharedClient', { value: t(destinationMeta?.sharedGoogleClientConfigured ? 'common.yes' : 'common.no') })}<br />
                  {t('gmail.redirectEffective', { value: destinationMeta?.googleRedirectUri || `${window.location.origin}/api/google-oauth/callback` })}
                </>
              ) : (
                <>
                  {t('destination.passwordStored', { value: t(destinationMeta?.passwordConfigured ? 'common.yes' : 'common.no') })}<br />
                  {t('destination.oauthConnected', { value: t(destinationMeta?.oauthConnected ? 'common.yes' : 'common.no') })}
                </>
              )}
            </div>

            <form className="settings-grid" onSubmit={onSave}>
              <label>
                <LabeledHint helpText={t('destination.providerHelp')} label={t('destination.provider')} />
                <select
                  aria-label={t('destination.provider')}
                  value={provider}
                  onChange={(event) => updateProvider(event.target.value)}
                >
                  {DESTINATION_PROVIDER_PRESETS.map((preset) => (
                    <option key={preset.id} value={preset.id}>{preset.label}</option>
                  ))}
                </select>
              </label>
              <div className="full muted-box">{providerPreset.description}</div>

              {isGmailProvider ? (
                <>
                  <div className="muted-box gmail-simplified-status full">
                    <strong>{t(currentProviderConnected ? 'destination.gmailReadyTitle' : 'destination.gmailPendingTitle')}</strong><br />
                    {t(currentProviderConnected ? 'destination.gmailReadyBody' : 'destination.gmailPendingBody')}
                    {!destinationMeta?.sharedGoogleClientConfigured ? (
                      <>
                        <br />
                        {t('destination.gmailAdminRequired')}
                      </>
                    ) : null}
                  </div>
                </>
              ) : (
                <>
                  <label>
                    <LabeledHint helpText={t('bridges.folderHelp')} label={t('bridges.folder')} />
                    <input
                      aria-label={t('bridges.folder')}
                      value={resolvedDestinationConfig.folder}
                      onChange={(event) => setDestinationConfig((current) => ({ ...current, folder: event.target.value }))}
                    />
                  </label>
                  {isOutlookProvider ? (
                    <div className="muted-box full">{t('destination.microsoftOauthHelp')}</div>
                  ) : null}
                  {!isOutlookProvider ? (
                    <label>
                      <LabeledHint helpText={t('bridges.hostHelp')} label={t('bridges.host')} />
                      <input
                        aria-label={t('bridges.host')}
                        value={resolvedDestinationConfig.host}
                        onChange={(event) => setDestinationConfig((current) => ({ ...current, host: event.target.value }))}
                      />
                    </label>
                  ) : null}
                  {!isOutlookProvider ? (
                    <label>
                      <LabeledHint helpText={t('bridges.portHelp')} label={t('bridges.port')} />
                      <input
                        aria-label={t('bridges.port')}
                        type="number"
                        value={resolvedDestinationConfig.port}
                        onChange={(event) => setDestinationConfig((current) => ({ ...current, port: Number(event.target.value) || '' }))}
                      />
                    </label>
                  ) : null}
                  {!isOutlookProvider ? (
                    <label>
                      <LabeledHint helpText={t('bridges.usernameHelp')} label={t('bridges.username')} />
                      <input
                        aria-label={t('bridges.username')}
                        value={resolvedDestinationConfig.username}
                        onChange={(event) => setDestinationConfig((current) => ({ ...current, username: event.target.value }))}
                      />
                    </label>
                  ) : null}
                  {!isOutlookProvider ? (
                    <label>
                      <LabeledHint helpText={t('bridges.authMethodHelp')} label={t('bridges.authMethod')} />
                      {usesMicrosoftOAuth ? (
                        <input aria-label={t('bridges.authMethod')} disabled value={t('authMethod.oauth2')} />
                      ) : (
                        <input aria-label={t('bridges.authMethod')} disabled value={t('authMethod.password')} />
                      )}
                    </label>
                  ) : null}
                  {!isOutlookProvider && usesMicrosoftOAuth ? (
                    <label>
                      <LabeledHint helpText={t('bridges.oauthProviderHelp')} label={t('bridges.oauthProvider')} />
                      <input aria-label={t('bridges.oauthProvider')} disabled value={t('oauthProvider.microsoft')} />
                    </label>
                  ) : null}
                  {usesPassword ? (
                    <label className="full">
                      <LabeledHint helpText={t('bridges.passwordHelp')} label={t('bridges.password')} />
                      <input
                        aria-label={t('bridges.password')}
                        type="password"
                        value={resolvedDestinationConfig.password}
                        onChange={(event) => setDestinationConfig((current) => ({ ...current, password: event.target.value }))}
                        placeholder={destinationMeta?.passwordConfigured ? t('gmail.storedSecurely') : ''}
                      />
                    </label>
                  ) : !isOutlookProvider ? (
                    <div className="muted-box full">{t('destination.microsoftOauthHelp')}</div>
                  ) : null}
                  <label className="checkbox-row">
                    <input
                      type="checkbox"
                      checked={resolvedDestinationConfig.tls}
                      onChange={(event) => setDestinationConfig((current) => ({ ...current, tls: event.target.checked }))}
                    />
                    <LabeledHint helpText={t('bridges.tlsOnlyHelp')} label={t('bridges.tlsOnly')} />
                  </label>
                </>
              )}

              <div className="full action-row">
                <LoadingButton className="primary" isLoading={saveLoading} loadingLabel={t('destination.saveLoading')} type="submit">
                  {t('destination.save')}
                </LoadingButton>
              </div>
            </form>
          </>
        ) : null}
      </section>

      {hasSidebar ? (
        <aside className="sidebar-stack">
          <GoogleDestinationSetupPanel destinationMeta={destinationMeta} locale={locale} t={t} />
        </aside>
      ) : null}
    </section>
  )
}

export default DestinationMailboxSection
