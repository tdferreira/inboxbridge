import { fireEvent, render, screen } from '@testing-library/react'
import FetcherDialog from './FetcherDialog'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

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
          t={t}
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
          t={t}
        />
      )
    }
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
        duplicateIdError="A mail fetcher with ID duplicate-id already exists. Choose a different ID."
        onApplyPreset={vi.fn()}
        onBridgeFormChange={vi.fn()}
        onClose={vi.fn()}
        onSave={vi.fn((event) => event.preventDefault())}
        saveLoading={false}
        t={t}
      />
    )

    expect(screen.getByText('A mail fetcher with ID duplicate-id already exists. Choose a different ID.')).toBeInTheDocument()
  })
})
