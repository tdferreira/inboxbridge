import { fireEvent, render, screen } from '@testing-library/react'
import FetcherDialog from './FetcherDialog'
import { translate } from '../../lib/i18n'

describe('FetcherDialog', () => {
  it('supports provider presets and hides irrelevant auth fields', () => {
    let bridgeForm = {
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
    fireEvent.change(screen.getByLabelText(/Auth Method/), { target: { value: 'OAUTH2' } })

    expect(onApplyPreset).toHaveBeenCalledWith('outlook')
    expect(screen.queryByPlaceholderText('Leave blank to keep existing')).not.toBeInTheDocument()
    expect(screen.getByLabelText(/OAuth Provider/)).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Optional manual token')).toBeInTheDocument()

    function renderUi() {
      return render(
        <FetcherDialog
          bridgeForm={bridgeForm}
          onApplyPreset={onApplyPreset}
          onBridgeFormChange={(updater) => {
            bridgeForm = typeof updater === 'function' ? updater(bridgeForm) : updater
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
        <FetcherDialog
          bridgeForm={bridgeForm}
          onApplyPreset={onApplyPreset}
          onBridgeFormChange={(updater) => {
            bridgeForm = typeof updater === 'function' ? updater(bridgeForm) : updater
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

  it('hides Microsoft OAuth choices when Microsoft OAuth is not configured', () => {
    render(
      <FetcherDialog
        bridgeForm={{
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
        microsoftOAuthAvailable={false}
        onApplyPreset={vi.fn()}
        onBridgeFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.queryByRole('option', { name: 'Outlook / Hotmail / Live' })).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'OAuth2' })).not.toBeInTheDocument()
  })

  it('shows duplicate id validation inline', () => {
    render(
      <FetcherDialog
        bridgeForm={{
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
        onBridgeFormChange={vi.fn()}
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
      <FetcherDialog
        bridgeForm={{
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
        onBridgeFormChange={vi.fn()}
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
      <FetcherDialog
        bridgeForm={{
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
        onBridgeFormChange={vi.fn()}
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
      <FetcherDialog
        bridgeForm={{
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
        onBridgeFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        onTestConnection={onTestConnection}
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
})
