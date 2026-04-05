import { fireEvent, render, screen } from '@testing-library/react'
import EmailAccountDialog from './EmailAccountDialog'
import { translate } from '../../lib/i18n'

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
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText(/save this mail account and then launch the microsoft consent flow/i)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Save and Connect Microsoft' }))
    expect(onSaveAndConnectOAuth).toHaveBeenCalledTimes(1)
  })

  it('uses detected IMAP folders while still allowing manual entry', () => {
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
        emailAccountFolders={['INBOX', 'Archive']}
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

    expect(screen.getByRole('combobox', { name: /^Folder/i })).toHaveValue('INBOX')
    fireEvent.click(screen.getByRole('button', { name: 'Enter folder manually' }))
    fireEvent.change(screen.getByRole('textbox', { name: /^Folder/i }), { target: { value: 'Projects' } })
    expect(screen.getByRole('textbox', { name: /^Folder/i })).toHaveValue('Projects')
    fireEvent.click(screen.getByRole('button', { name: 'Use detected folders' }))
    expect(screen.getByRole('combobox', { name: /^Folder/i })).toHaveValue('INBOX')

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
          onSave={vi.fn((event) => event.preventDefault())}
          saveLoading={false}
          t={(key, params) => translate('en', key, params)}
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
    const tlsOnly = screen.getByLabelText(/TLS only/)
    const unreadOnly = screen.getByLabelText(/Unread only/)
    const enabled = screen.getByRole('checkbox', { name: /Enabled/ })

    expect(fetchMode.compareDocumentPosition(tlsOnly) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(fetchMode.compareDocumentPosition(unreadOnly) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(tlsOnly.compareDocumentPosition(enabled) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(unreadOnly.compareDocumentPosition(enabled) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
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
