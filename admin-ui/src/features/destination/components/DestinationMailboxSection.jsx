import { useState } from 'react'

import CollapsibleSection from '@/shared/components/CollapsibleSection'
import DestinationMailboxDialog from './DestinationMailboxDialog'
import GoogleDestinationSetupPanel from './GoogleDestinationSetupPanel'
import { findDestinationProviderPreset, normalizeDestinationProviderConfig } from '@/lib/emailProviderPresets'
import './DestinationMailboxSection.css'

/**
 * Renders the destination mailbox area in two modes: admins can edit the
 * destination settings, while regular users only see connection status plus
 * the provider OAuth action when it applies.
 */
function DestinationMailboxSection({
  collapsed,
  collapseLoading,
  destinationConfig,
  destinationFolders = [],
  destinationFoldersLoading = false,
  destinationMeta,
  isAdmin,
  unlinkLoading,
  locale,
  onCollapseToggle,
  onUnlinkOAuth,
  onSave,
  onSaveAndAuthenticate,
  saveLoading,
  sectionLoading = false,
  testConnectionLoading = false,
  onTestConnection,
  t
}) {
  const resolvedDestinationConfig = normalizeDestinationProviderConfig(destinationConfig)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [dialogDraftConfig, setDialogDraftConfig] = useState(null)
  const savedProvider = destinationMeta?.provider || 'GMAIL_API'
  const savedProviderPreset = findDestinationProviderPreset(savedProvider)
  const savedIsGmailProvider = savedProvider === 'GMAIL_API'
  const configured = Boolean(destinationMeta?.configured)
  const hasLinkedDestination = Boolean(destinationMeta?.linked)
  const savedProviderConnected = configured && hasLinkedDestination
  const hasSidebar = !collapsed && isAdmin && savedIsGmailProvider

  function openDialog() {
    setDialogDraftConfig(normalizeDestinationProviderConfig(destinationConfig))
    setDialogOpen(true)
  }

  function closeDialog() {
    setDialogOpen(false)
    setDialogDraftConfig(null)
  }

  return (
    <section className={`app-columns ${hasSidebar ? '' : 'app-columns-single'}`.trim()}>
      <CollapsibleSection
        actions={configured ? (
          <button className="secondary" onClick={openDialog} type="button">
            {t('destination.edit')}
          </button>
        ) : (
          <button className="primary" onClick={openDialog} type="button">
            {t('destination.add')}
          </button>
        )}
        className="destination-mailbox-panel"
        collapsed={collapsed}
        collapseLoading={collapseLoading}
        copy={t(savedIsGmailProvider ? (isAdmin ? 'destination.gmailAdminCopy' : 'destination.gmailUserCopy') : 'destination.imapCopy')}
        id="destination-mailbox-section"
        onCollapseToggle={onCollapseToggle}
        sectionLoading={sectionLoading}
        t={t}
        title={t('destination.title')}
      >
            {configured ? (
              <div className="destination-credential-status muted-box">
                <strong>{t('destination.savedStatus')}</strong><br />
                {t('destination.providerValue', { value: t(savedProviderPreset.labelKey) })}<br />
                {t('destination.connectionStatus', { value: t(hasLinkedDestination ? 'common.yes' : 'common.no') })}<br />
                {savedIsGmailProvider ? (
                  <>
                    {t('gmail.sharedClient', { value: t(destinationMeta?.sharedGoogleClientConfigured ? 'common.yes' : 'common.no') })}<br />
                    {t('gmail.redirectEffective', { value: destinationMeta?.googleRedirectUri || `${window.location.origin}/api/google-oauth/callback` })}
                  </>
                ) : (
                  <>
                    {t('destination.hostValue', { value: resolvedDestinationConfig.host || '—' })}<br />
                    {t('destination.portValue', { value: resolvedDestinationConfig.port || '—' })}<br />
                    {t('destination.usernameValue', { value: resolvedDestinationConfig.username || '—' })}<br />
                    {t('destination.folderValue', { value: resolvedDestinationConfig.folder || 'INBOX' })}<br />
                    {t('destination.passwordStored', { value: t(destinationMeta?.passwordConfigured ? 'common.yes' : 'common.no') })}<br />
                    {t('destination.oauthConnected', { value: t(destinationMeta?.oauthConnected ? 'common.yes' : 'common.no') })}
                  </>
                )}
              </div>
            ) : (
              <div className="muted-box destination-empty-state">
                <strong>{t('destination.emptyTitle')}</strong><br />
                {t('destination.emptyBody')}
              </div>
            )}

            {savedIsGmailProvider ? (
              <>
                <>
                  <div className="muted-box gmail-simplified-status full">
                    <strong>{t(savedProviderConnected ? 'destination.gmailReadyTitle' : 'destination.gmailPendingTitle')}</strong><br />
                    {t(savedProviderConnected ? 'destination.gmailReadyBody' : 'destination.gmailPendingBody')}
                    {!destinationMeta?.sharedGoogleClientConfigured ? (
                      <>
                        <br />
                        {t('destination.gmailAdminRequired')}
                      </>
                    ) : null}
                  </div>
                </>
              </>
            ) : null}

            {dialogOpen ? (
              <DestinationMailboxDialog
                destinationConfig={dialogDraftConfig || destinationConfig}
                destinationFolders={destinationFolders}
                destinationFoldersLoading={destinationFoldersLoading}
                destinationMeta={destinationMeta}
                onClose={closeDialog}
                onSave={() => onSave(dialogDraftConfig || destinationConfig)}
                onSaveAndAuthenticate={() => onSaveAndAuthenticate(dialogDraftConfig || destinationConfig)}
                onTestConnection={() => onTestConnection(dialogDraftConfig || destinationConfig)}
                onUnlink={onUnlinkOAuth}
                saveLoading={saveLoading}
                setDestinationConfig={setDialogDraftConfig}
                t={t}
                testConnectionLoading={testConnectionLoading}
                unlinkLoading={unlinkLoading}
              />
            ) : null}
      </CollapsibleSection>

      {hasSidebar ? (
        <aside className="sidebar-stack">
          <GoogleDestinationSetupPanel destinationMeta={destinationMeta} locale={locale} t={t} />
        </aside>
      ) : null}
    </section>
  )
}

export default DestinationMailboxSection
