import { fireEvent, render, screen } from '@testing-library/react'
import EmailAccountDialog from './EmailAccountDialog'
import { translate } from '../../lib/i18n'

describe('EmailAccountDialog', () => {
  it('supports provider presets and hides irrelevant auth fields', () => {
    let emailAccountForm = {
      bridgeId: '',
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
      customLabel: ''
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
          originalBridgeId: '',
          bridgeId: '',
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
          customLabel: ''
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
          originalBridgeId: 'outlook-main',
          bridgeId: 'outlook-main',
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
          customLabel: ''
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
          originalBridgeId: '',
          bridgeId: 'duplicate-id',
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
          originalBridgeId: '',
          bridgeId: '',
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
          customLabel: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByRole('dialog', { name: 'Adicionar conta de email de origem' })).toBeInTheDocument()
    expect(screen.getByLabelText(/Predefinição do fornecedor/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Método de autenticação/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Etiqueta personalizada/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Adicionar conta de email de origem' })).toBeInTheDocument()
  })

  it('closes without confirmation when no changes were introduced', () => {
    const onClose = vi.fn()
    const confirmSpy = vi.spyOn(window, 'confirm')

    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalBridgeId: '',
          bridgeId: '',
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
          customLabel: ''
        }}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onClose={onClose}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Close Add Source Email Account' }))

    expect(confirmSpy).not.toHaveBeenCalled()
    expect(onClose).toHaveBeenCalledTimes(1)

    confirmSpy.mockRestore()
  })

  it('offers a test-connection action and shows the latest test result', () => {
    const onTestConnection = vi.fn()

    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalBridgeId: '',
          bridgeId: 'fetcher-a',
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
          sampleMessageMaterialized: true
        }}
        t={(key, params) => translate('en', key, params)}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Test Connection' }))

    expect(onTestConnection).toHaveBeenCalledTimes(1)
    expect(screen.getByText('Connection test succeeded for IMAP on imap.example.com:993 (folder INBOX).')).toBeInTheDocument()
    expect(screen.getByText('Endpoint')).toBeInTheDocument()
    expect(screen.getByText('imap.example.com:993')).toBeInTheDocument()
    expect(screen.getByText('Authenticated')).toBeInTheDocument()
    expect(screen.getByText('Unread filter validated')).toBeInTheDocument()
    expect(screen.getByText('12')).toBeInTheDocument()
  })

  it('offers a save-and-connect OAuth action when a provider is selected', () => {
    const onSaveAndConnectOAuth = vi.fn()

    render(
      <EmailAccountDialog
        emailAccountForm={{
          originalBridgeId: '',
          bridgeId: 'outlook-main',
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
})
