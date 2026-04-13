import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import EmailAccountDialog from './EmailAccountDialog'
import { translate } from '@/lib/i18n'

describe('EmailAccountDialog', () => {
  it('supports provider presets and hides irrelevant auth fields', () => {
    let emailAccountForm = {
      emailAccountId: '',
      enabled: true,
      protocol: 'IMAP',
      host: '',
      port: 993,
      tls: true,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      username: '',
      password: '',
      oauthRefreshToken: '',
      folder: 'INBOX',
      unreadOnly: false,
      customLabel: '',
      markReadAfterPoll: false,
      postPollAction: 'NONE',
      postPollTargetFolder: ''
    }
    const onApplyPreset = vi.fn()
    const { rerender } = renderUi()

    expect(screen.getByPlaceholderText('Leave blank to keep existing')).toBeInTheDocument()
    expect(screen.queryByLabelText(/OAuth Provider/)).not.toBeInTheDocument()
    expect(screen.queryByPlaceholderText('Optional manual token')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Provider preset/), { target: { value: 'outlook' } })

    expect(onApplyPreset).toHaveBeenCalledWith('outlook')
    expect(screen.queryByPlaceholderText('Leave blank to keep existing')).not.toBeInTheDocument()
    expect(screen.getByLabelText(/OAuth Provider/)).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Optional manual token')).toBeInTheDocument()
    expect(screen.getByDisplayValue('OAuth2')).toBeDisabled()
    expect(screen.getByDisplayValue('Microsoft')).toBeDisabled()

    function renderUi() {
      return render(
        <EmailAccountDialog
          emailAccountForm={emailAccountForm}
          onApplyPreset={onApplyPreset}
          onEmailAccountFormChange={(updater) => {
            emailAccountForm = typeof updater === 'function' ? updater(emailAccountForm) : updater
            rerenderUi()
          }}
          onClose={vi.fn()}
          onSave={vi.fn((event) => event.preventDefault())}
          saveLoading={false}
          t={(key, params) => translate('en', key, params)}
        />
      )
    }

    function rerenderUi() {
      rerender(
        <EmailAccountDialog
          emailAccountForm={emailAccountForm}
          onApplyPreset={onApplyPreset}
          onEmailAccountFormChange={(updater) => {
            emailAccountForm = typeof updater === 'function' ? updater(emailAccountForm) : updater
            rerenderUi()
          }}
          onClose={vi.fn()}
          onSave={vi.fn((event) => event.preventDefault())}
          saveLoading={false}
          t={(key, params) => translate('en', key, params)}
        />
      )
    }
  })

  it('shows custom label when the destination is not configured yet', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: 'fetcher-a',
          emailAccountId: '',
          enabled: true,
          protocol: 'IMAP',
          host: '',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: '',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        destinationConfig={{ provider: 'GMAIL_API' }}
        destinationMeta={null}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('Custom Label')).toBeInTheDocument()
  })

  it('hides custom label when the current destination does not support labels', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: 'fetcher-a',
          emailAccountId: '',
          enabled: true,
          protocol: 'IMAP',
          host: '',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: '',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: 'Imported/DEI',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        destinationConfig={{ provider: 'OUTLOOK_IMAP' }}
        destinationMeta={{ provider: 'OUTLOOK_IMAP', linked: true }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.queryByLabelText('Custom Label')).not.toBeInTheDocument()
  })

  it('uses source-specific folder guidance and the updated TLS availability help', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: 'fetcher-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: 'secret',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByRole('note', { name: 'Choose one or more IMAP source folders for InboxBridge to poll, usually INBOX. Before the folder list has been loaded from the server you can type folder names directly; once the server folders are known, only those reported folders can be selected.' })).toBeInTheDocument()
    expect(translate('en', 'emailAccounts.testTlsAvailableHelp')).toBe('Whether InboxBridge could verify an encrypted path for this source host, either through implicit TLS or STARTTLS/STLS on the configured port.')
  })

  it('hides OAuth2 choices when no source OAuth provider is configured', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: '',
          enabled: true,
          protocol: 'IMAP',
          host: '',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: '',
          password: 'secret',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        availableOAuthProviders={[]}
        microsoftOAuthAvailable={false}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.queryByRole('option', { name: 'OAuth2' })).not.toBeInTheDocument()
  })

  it('rehydrates the outlook preset when editing an existing outlook source account', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: 'outlook-main',
          emailAccountId: 'outlook-main',
          enabled: true,
          protocol: 'IMAP',
          host: 'outlook.office365.com',
          port: 993,
          tls: true,
          authMethod: 'OAUTH2',
          oauthProvider: 'MICROSOFT',
          username: 'owner@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByLabelText(/Provider preset/)).toHaveValue('outlook')
  })

  it('shows duplicate id validation inline', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: 'duplicate-id',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        duplicateIdError="A source email account with ID duplicate-id already exists. Choose a different ID."
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('A source email account with ID duplicate-id already exists. Choose a different ID.')).toBeInTheDocument()
  })

  it('renders translated fetcher labels in portuguese', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: '',
          enabled: true,
          protocol: 'IMAP',
          host: '',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: '',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByRole('dialog', { name: 'Adicionar conta de email' })).toBeInTheDocument()
    expect(screen.getByLabelText(/Predefinição do fornecedor/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Método de autenticação/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Etiqueta personalizada/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Adicionar' })).toBeInTheDocument()
  })

  it('clicking the folder label focuses the pillbox input without removing pills', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: 'imap-main',
          emailAccountId: 'imap-main',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX, Projects/2026',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        emailAccountFolders={['INBOX', 'Projects/2026']}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        testResult={{ tone: 'success' }}
        t={(key, params) => translate('en', key, params)}
      />
    )

    fireEvent.click(screen.getByText('Folder'))

    expect(screen.getByRole('combobox', { name: 'Folder' })).toHaveFocus()
    expect(screen.getByText('INBOX')).toBeInTheDocument()
    expect(screen.getByText('Projects/2026')).toBeInTheDocument()
  })

  it('offers the forwarded post-poll action for IMAP accounts', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: '',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          fetchMode: 'POLLING',
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByRole('option', { name: 'Mark source message as forwarded' })).toBeInTheDocument()
  })

  it('lets IMAP users select multiple detected folders from the generic folder pillbox', () => {
    let emailAccountForm = {
      originalEmailAccountId: '',
      emailAccountId: 'source-a',
      enabled: true,
      protocol: 'IMAP',
      host: 'imap.example.com',
      port: 993,
      tls: true,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      username: 'user@example.com',
      password: '',
      oauthRefreshToken: '',
      folder: 'INBOX',
      unreadOnly: false,
      fetchMode: 'IDLE',
      customLabel: '',
      markReadAfterPoll: false,
      postPollAction: 'NONE',
      postPollTargetFolder: ''
    }
    const { rerender } = render(
      <EmailAccountDialog
        emailAccountForm={emailAccountForm}
        emailAccountFolders={['INBOX', 'Projects/2026', 'Archive']}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={(updater) => {
          emailAccountForm = typeof updater === 'function' ? updater(emailAccountForm) : updater
          rerenderUi()
        }}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        testResult={{ tone: 'success', message: 'Connection test succeeded.' }}
        t={(key, params) => translate('en', key, params)}
      />
    )

    const folderInput = screen.getByRole('combobox', { name: /^Folder/i })
    fireEvent.change(folderInput, { target: { value: 'Pro' } })
    fireEvent.keyDown(folderInput, { key: 'ArrowDown' })
    fireEvent.keyDown(folderInput, { key: 'Enter' })

    expect(emailAccountForm.folder).toBe('INBOX, Projects/2026')
    expect(screen.getByText('INBOX')).toBeInTheDocument()
    expect(screen.getByText('Projects/2026')).toBeInTheDocument()
    expect(screen.getByText('Connection test fetched folders successfully. Start typing to pick one or more folders.')).toBeInTheDocument()

    function rerenderUi() {
      rerender(
        <EmailAccountDialog
          emailAccountForm={emailAccountForm}
          emailAccountFolders={['INBOX', 'Projects/2026', 'Archive']}
          onApplyPreset={vi.fn()}
          onEmailAccountFormChange={(updater) => {
            emailAccountForm = typeof updater === 'function' ? updater(emailAccountForm) : updater
            rerenderUi()
          }}
          onClose={vi.fn()}
          onSave={vi.fn((event) => event.preventDefault())}
          saveLoading={false}
          testResult={{ tone: 'success', message: 'Connection test succeeded.' }}
          t={(key, params) => translate('en', key, params)}
        />
      )
    }
  })

  it('forwards folder typing activity so the outer dialog controller can enrich folders', () => {
    const onFolderInputActivity = vi.fn()
    const onFolderInputFocus = vi.fn()

    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: 'source-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: '',
          unreadOnly: false,
          fetchMode: 'POLLING',
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onFolderInputActivity={onFolderInputActivity}
        onFolderInputFocus={onFolderInputFocus}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    const folderInput = screen.getByRole('combobox', { name: /^Folder/i })
    fireEvent.focus(folderInput)
    fireEvent.change(folderInput, { target: { value: 'In' } })

    expect(onFolderInputFocus).toHaveBeenCalledTimes(1)
    expect(onFolderInputActivity).toHaveBeenCalledWith('In')
  })

  it('shows translated info hints for connection test results instead of a long forwarded marker paragraph', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: 'source-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: true,
          fetchMode: 'IDLE',
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'FORWARDED',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('pt-PT', key, params)}
        testResult={{
          tone: 'success',
          message: 'Ligação com sucesso.',
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          authenticated: true,
          folder: 'INBOX',
          folderAccessible: true,
          unreadFilterRequested: true,
          unreadFilterSupported: true,
          unreadFilterValidated: true,
          visibleMessageCount: 12,
          unreadMessageCount: 5,
          sampleMessageAvailable: true,
          sampleMessageMaterialized: true,
          forwardedMarkerSupported: true
        }}
      />
    )

    expect(screen.getByText('Marcador forwarded suportado')).toBeInTheDocument()
    expect(screen.getByLabelText('Se o servidor IMAP aparenta suportar o marcador opcional $Forwarded.')).toBeInTheDocument()
    expect(screen.getByLabelText('Qual o protocolo de caixa de correio que o InboxBridge conseguiu negociar durante o teste de ligação.')).toBeInTheDocument()
    expect(screen.queryByText(/verificação IMAP de melhor esforço/i)).not.toBeInTheDocument()
  })

  it('closes without confirmation when no changes were introduced', () => {
    const onClose = vi.fn()
    const confirmSpy = vi.spyOn(window, 'confirm')

    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: '',
          enabled: true,
          protocol: 'IMAP',
          host: '',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: '',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={onClose}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Close Add Email Account' }))

    expect(confirmSpy).not.toHaveBeenCalled()
    expect(onClose).toHaveBeenCalledTimes(1)

    confirmSpy.mockRestore()
  })

  it('offers a test-connection action and shows the latest test result', () => {
    const onTestConnection = vi.fn()

    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: 'fetcher-a',
          emailAccountId: 'fetcher-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        onTestEmailAccountConnection={onTestConnection}
        saveLoading={false}
        testResult={{
          message: 'Connection test succeeded for IMAP on imap.example.com:993 (folder INBOX).',
          tone: 'success',
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          authenticated: true,
          folder: 'INBOX',
          folderAccessible: true,
          unreadFilterRequested: true,
          unreadFilterSupported: true,
          unreadFilterValidated: true,
          visibleMessageCount: 12,
          unreadMessageCount: 3,
          sampleMessageAvailable: true,
          sampleMessageMaterialized: true,
          forwardedMarkerSupported: false
        }}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('Connection test succeeded for IMAP on imap.example.com:993 (folder INBOX).')).toBeInTheDocument()
    expect(screen.getByText('Endpoint')).toBeInTheDocument()
    expect(screen.getByText('imap.example.com:993')).toBeInTheDocument()
    expect(screen.getByText('Authenticated')).toBeInTheDocument()
    expect(screen.getByText('Unread filter validated')).toBeInTheDocument()
    expect(screen.getByText('Forwarded marker supported')).toBeInTheDocument()
    expect(screen.getByText('12')).toBeInTheDocument()
    expect(screen.getByLabelText('Whether the IMAP server appears to support the optional $Forwarded marker.')).toBeInTheDocument()
    expect(screen.queryByText('InboxBridge treats this as a best-effort IMAP capability probe. Servers that do not advertise user flags may still reject $Forwarded later, and InboxBridge will continue without failing the import.')).not.toBeInTheDocument()
  })

  it('disables test connection until the required connection fields are present', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: 'fetcher-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        onTestEmailAccountConnection={vi.fn()}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByRole('button', { name: 'Test Connection' })).toBeDisabled()
  })

  it('shows source folder loading failures inline under the folder field', () => {
    render(
      <EmailAccountDialog
        emailAccountFolderLoadError="Unable to load mailbox folders"
        emailAccountForm={{
          originalEmailAccountId: 'fetcher-a',
          emailAccountId: 'fetcher-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        onTestEmailAccountConnection={vi.fn()}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('Unable to load mailbox folders')).toBeInTheDocument()
  })

  it('shows the folder retrieval helper while loading IMAP folders', () => {
    render(
      <EmailAccountDialog
        emailAccountFoldersLoading
        emailAccountForm={{
          originalEmailAccountId: 'fetcher-a',
          emailAccountId: 'fetcher-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        onTestEmailAccountConnection={vi.fn()}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('Retrieving folders from server…')).toBeInTheDocument()
  })

  it('scrolls the latest test result card into view', () => {
    const scrollIntoView = vi.fn()
    window.HTMLElement.prototype.scrollIntoView = scrollIntoView

    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: 'fetcher-a',
          emailAccountId: 'fetcher-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        onTestEmailAccountConnection={vi.fn()}
        saveLoading={false}
        testResult={{ tone: 'success', message: 'Connection test succeeded.' }}
        t={(key, params) => translate('en', key, params)}
      />
    )

    return waitFor(() => {
      expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'nearest' })
    })
  })

  it('offers a save-and-connect OAuth action when a provider is selected', () => {
    const onSaveAndConnectOAuth = vi.fn()

    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: 'outlook-main',
          enabled: true,
          protocol: 'IMAP',
          host: 'outlook.office365.com',
          port: 993,
          tls: true,
          authMethod: 'OAUTH2',
          oauthProvider: 'MICROSOFT',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        onSaveAndConnectOAuth={onSaveAndConnectOAuth}
        onTestEmailAccountConnection={vi.fn()}
        saveLoading={false}
        testResult={{ tone: 'success', message: 'Connection test succeeded.' }}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText(/save this mail account and then launch the microsoft consent flow/i)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Save and Connect Microsoft' }))
    expect(onSaveAndConnectOAuth).toHaveBeenCalledTimes(1)
    expect(screen.queryByText('Save without validating this source?')).not.toBeInTheDocument()
  })

  it('lets IMAP users type custom folders directly in the pillbox before folders are loaded', () => {
    let emailAccountForm = {
      originalEmailAccountId: 'fetcher-a',
      emailAccountId: 'fetcher-a',
      enabled: true,
      protocol: 'IMAP',
      host: 'imap.example.com',
      port: 993,
      tls: true,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      username: 'user@example.com',
      password: '',
      oauthRefreshToken: '',
      folder: 'INBOX',
      unreadOnly: false,
      customLabel: ''
    }

    const { rerender } = render(
      <EmailAccountDialog
        emailAccountForm={emailAccountForm}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={(updater) => {
          emailAccountForm = typeof updater === 'function' ? updater(emailAccountForm) : updater
          rerenderUi()
        }}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    const folderInput = screen.getByRole('combobox', { name: /^Folder/i })
    fireEvent.change(folderInput, { target: { value: 'Projects' } })
    fireEvent.keyDown(folderInput, { key: 'Enter' })

    expect(emailAccountForm.folder).toBe('INBOX, Projects')
    expect(screen.getByText('Projects')).toBeInTheDocument()

    function rerenderUi() {
      rerender(
        <EmailAccountDialog
          emailAccountForm={emailAccountForm}
          onApplyPreset={vi.fn()}
          onEmailAccountFormChange={(updater) => {
            emailAccountForm = typeof updater === 'function' ? updater(emailAccountForm) : updater
            rerenderUi()
          }}
          onClose={vi.fn()}
          onSave={vi.fn((event) => event.preventDefault())}
          saveLoading={false}
          t={(key, params) => translate('en', key, params)}
        />
      )
    }
  })

  it('shows green and red folder validation pills after a successful connection test', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: 'fetcher-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX, MissingFolder',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        emailAccountFolders={['INBOX', 'Archive']}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        testResult={{ tone: 'success', message: 'Connection test succeeded.' }}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('INBOX exists on the server')).toBeInTheDocument()
    expect(screen.getByText('MissingFolder was not found on the server')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled()
  })

  it('blocks adding unknown folders after server folders were validated', () => {
    let emailAccountForm = {
      originalEmailAccountId: '',
      emailAccountId: 'fetcher-a',
      enabled: true,
      protocol: 'IMAP',
      host: 'imap.example.com',
      port: 993,
      tls: true,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      username: 'user@example.com',
      password: '',
      oauthRefreshToken: '',
      folder: 'INBOX',
      unreadOnly: false,
      customLabel: '',
      markReadAfterPoll: false,
      postPollAction: 'NONE',
      postPollTargetFolder: ''
    }
    const { rerender } = render(
      <EmailAccountDialog
        emailAccountForm={emailAccountForm}
        emailAccountFolders={['INBOX', 'Archive']}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={(updater) => {
          emailAccountForm = typeof updater === 'function' ? updater(emailAccountForm) : updater
          rerenderUi()
        }}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        testResult={{ tone: 'success', message: 'Connection test succeeded.' }}
        t={(key, params) => translate('en', key, params)}
      />
    )

    const folderInput = screen.getByRole('combobox', { name: /^Folder/i })
    fireEvent.change(folderInput, { target: { value: 'MissingFolder' } })
    fireEvent.keyDown(folderInput, { key: 'Enter' })

    expect(emailAccountForm.folder).toBe('INBOX')
    expect(screen.queryByText('MissingFolder')).not.toBeInTheDocument()

    function rerenderUi() {
      rerender(
        <EmailAccountDialog
          emailAccountForm={emailAccountForm}
          emailAccountFolders={['INBOX', 'Archive']}
          onApplyPreset={vi.fn()}
          onEmailAccountFormChange={(updater) => {
            emailAccountForm = typeof updater === 'function' ? updater(emailAccountForm) : updater
            rerenderUi()
          }}
          onClose={vi.fn()}
          onSave={vi.fn((event) => event.preventDefault())}
          saveLoading={false}
          testResult={{ tone: 'success', message: 'Connection test succeeded.' }}
          t={(key, params) => translate('en', key, params)}
        />
      )
    }
  })

  it('uses preloaded folders to lock the pillbox even before a fresh connection test succeeds', () => {
    let emailAccountForm = {
      originalEmailAccountId: 'fetcher-a',
      emailAccountId: 'fetcher-a',
      enabled: true,
      protocol: 'IMAP',
      host: 'imap.example.com',
      port: 993,
      tls: true,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      username: 'user@example.com',
      password: '',
      oauthRefreshToken: '',
      folder: 'INBOX',
      unreadOnly: false,
      customLabel: '',
      markReadAfterPoll: false,
      postPollAction: 'NONE',
      postPollTargetFolder: ''
    }
    const { rerender } = render(
      <EmailAccountDialog
        emailAccountForm={emailAccountForm}
        emailAccountFolders={['INBOX', 'Archive']}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={(updater) => {
          emailAccountForm = typeof updater === 'function' ? updater(emailAccountForm) : updater
          rerenderUi()
        }}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    const folderInput = screen.getByRole('combobox', { name: /^Folder/i })
    fireEvent.change(folderInput, { target: { value: 'MissingFolder' } })
    fireEvent.keyDown(folderInput, { key: 'Enter' })

    expect(emailAccountForm.folder).toBe('INBOX')
    expect(screen.queryByText('MissingFolder')).not.toBeInTheDocument()
    expect(screen.getByText('INBOX exists on the server')).toBeInTheDocument()

    function rerenderUi() {
      rerender(
        <EmailAccountDialog
          emailAccountForm={emailAccountForm}
          emailAccountFolders={['INBOX', 'Archive']}
          onApplyPreset={vi.fn()}
          onEmailAccountFormChange={(updater) => {
            emailAccountForm = typeof updater === 'function' ? updater(emailAccountForm) : updater
            rerenderUi()
          }}
          onClose={vi.fn()}
          onSave={vi.fn((event) => event.preventDefault())}
          saveLoading={false}
          t={(key, params) => translate('en', key, params)}
        />
      )
    }
  })

  it('warns before saving an untested source and can save it disabled', () => {
    const onSave = vi.fn((event) => event.preventDefault())
    const onSaveWithoutValidation = vi.fn()

    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: 'fetcher-a',
          emailAccountId: 'fetcher-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: 'secret',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={onSave}
        onSaveWithoutValidation={onSaveWithoutValidation}
        onTestEmailAccountConnection={vi.fn()}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(screen.getByText('Save without validating this source?')).toBeInTheDocument()
    expect(onSave).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Save Disabled Without Testing' }))

    expect(onSaveWithoutValidation).toHaveBeenCalledTimes(1)
  })

  it('marks the folder pillbox invalid only after an enabled save attempt and clears it after editing', () => {
    let emailAccountForm = {
      originalEmailAccountId: 'fetcher-a',
      emailAccountId: 'fetcher-a',
      enabled: true,
      protocol: 'IMAP',
      host: 'imap.example.com',
      port: 993,
      tls: true,
      authMethod: 'PASSWORD',
      oauthProvider: 'NONE',
      username: 'user@example.com',
      password: 'secret',
      oauthRefreshToken: '',
      folder: 'INBOX, MissingFolder',
      unreadOnly: false,
      customLabel: '',
      markReadAfterPoll: false,
      postPollAction: 'NONE',
      postPollTargetFolder: ''
    }
    const onSave = vi.fn((event) => event.preventDefault())
    const { rerender } = render(
      <EmailAccountDialog
        emailAccountFolders={['INBOX', 'Archive']}
        emailAccountForm={emailAccountForm}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={(updater) => {
          emailAccountForm = typeof updater === 'function' ? updater(emailAccountForm) : updater
          rerenderUi()
        }}
        onClose={vi.fn()}
        onSave={onSave}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
        testResult={{ message: 'Connection ok', tone: 'success' }}
      />
    )

    const folderInput = screen.getByRole('combobox', { name: 'Folder' })
    expect(folderInput).not.toHaveAttribute('aria-invalid')

    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(onSave).not.toHaveBeenCalled()
    expect(screen.getByRole('combobox', { name: 'Folder' })).toHaveAttribute('aria-invalid', 'true')

    fireEvent.click(screen.getByRole('button', { name: 'Remove selected folder MissingFolder' }))

    expect(emailAccountForm.folder).toBe('INBOX')
    expect(screen.getByRole('combobox', { name: 'Folder' })).not.toHaveAttribute('aria-invalid')

    function rerenderUi() {
      rerender(
        <EmailAccountDialog
          emailAccountFolders={['INBOX', 'Archive']}
          emailAccountForm={emailAccountForm}
          onApplyPreset={vi.fn()}
          onEmailAccountFormChange={(updater) => {
            emailAccountForm = typeof updater === 'function' ? updater(emailAccountForm) : updater
            rerenderUi()
          }}
          onClose={vi.fn()}
          onSave={onSave}
          saveLoading={false}
          t={(key, params) => translate('en', key, params)}
          testResult={{ message: 'Connection ok', tone: 'success' }}
        />
      )
    }
  })

  it('hides plain add for new Outlook accounts and locks provider changes while editing', () => {
    const { rerender } = render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: 'outlook-main',
          enabled: true,
          protocol: 'IMAP',
          host: 'outlook.office365.com',
          port: 993,
          tls: true,
          authMethod: 'OAUTH2',
          oauthProvider: 'MICROSOFT',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        onSaveAndConnectOAuth={vi.fn()}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Custom Label')).not.toBeInTheDocument()

    rerender(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: 'outlook-main',
          emailAccountId: 'outlook-main',
          enabled: true,
          protocol: 'IMAP',
          host: 'outlook.office365.com',
          port: 993,
          tls: true,
          authMethod: 'OAUTH2',
          oauthProvider: 'MICROSOFT',
          username: 'user@example.com',
          password: '',
          oauthRefreshToken: '',
          folder: 'Archive',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        onSaveAndConnectOAuth={vi.fn()}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByLabelText(/Provider preset/)).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument()
  })

  it('shows post-poll source actions for IMAP accounts', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: 'fetcher-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: 'secret',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: true,
          postPollAction: 'MOVE',
          postPollTargetFolder: 'Archive'
        }}
        emailAccountFolders={['INBOX', 'Archive']}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByLabelText(/Mark as read after polling/)).toBeInTheDocument()
    expect(screen.getByLabelText(/After polling/)).toHaveValue('MOVE')
    expect(screen.getByLabelText(/Move to folder/)).toHaveValue('Archive')
  })

  it('orders fetch mode, TLS and unread filters, and enabled as the final account toggle', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: 'fetcher-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: 'secret',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          fetchMode: 'POLLING',
          markReadAfterPoll: true,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    const fetchMode = screen.getByLabelText(/Source update mode/)
    const tlsOnly = screen.getByRole('checkbox', { name: /^TLS/i })
    const unreadOnly = screen.getByLabelText(/Unread only/)
    const enabled = screen.getByRole('checkbox', { name: /Enabled/ })

    expect(fetchMode.compareDocumentPosition(tlsOnly) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(fetchMode.compareDocumentPosition(unreadOnly) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(tlsOnly.compareDocumentPosition(enabled) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(unreadOnly.compareDocumentPosition(enabled) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('shows an unsafe transport warning when TLS is disabled', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: 'fetcher-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 143,
          tls: false,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: 'secret',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          fetchMode: 'POLLING',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('Unsafe source connection')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /^TLS/i })).not.toBeChecked()
  })

  it('locks the TLS toggle after InboxBridge verifies a secure endpoint', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: 'fetcher-a',
          enabled: true,
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: 'secret',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          fetchMode: 'POLLING',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        testResult={{
          tone: 'success',
          message: 'Connection test succeeded over TLS.',
          protocol: 'IMAP',
          host: 'imap.example.com',
          port: 993,
          tls: true,
          tlsRecommended: true,
          recommendedTlsPort: 993,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          authenticated: true,
          folder: 'INBOX',
          folderAccessible: true
        }}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('TLS was enforced automatically')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /^TLS/i })).toBeDisabled()
  })

  it('hides post-poll source actions for POP3 accounts', () => {
    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalEmailAccountId: '',
          emailAccountId: 'fetcher-a',
          enabled: true,
          protocol: 'POP3',
          host: 'pop.example.com',
          port: 995,
          tls: true,
          authMethod: 'PASSWORD',
          oauthProvider: 'NONE',
          username: 'user@example.com',
          password: 'secret',
          oauthRefreshToken: '',
          folder: 'INBOX',
          unreadOnly: false,
          customLabel: '',
          markReadAfterPoll: false,
          postPollAction: 'NONE',
          postPollTargetFolder: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.queryByLabelText(/Mark as read after polling/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/After polling/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Move to folder/)).not.toBeInTheDocument()
  })
})
