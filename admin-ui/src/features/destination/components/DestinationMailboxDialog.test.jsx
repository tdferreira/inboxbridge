import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import DestinationMailboxDialog from './DestinationMailboxDialog'
import { translate } from '@/lib/i18n'

function baseConfig(overrides = {}) {
  return {
    provider: 'OUTLOOK_IMAP',
    host: 'outlook.office365.com',
    port: 993,
    tls: true,
    authMethod: 'OAUTH2',
    oauthProvider: 'MICROSOFT',
    username: 'me@example.com',
    password: '',
    folder: '',
    ...overrides
  }
}

function baseMeta(overrides = {}) {
  return {
    configured: true,
    provider: 'OUTLOOK_IMAP',
    linked: true,
    oauthConnected: true,
    passwordConfigured: false,
    ...overrides
  }
}

function DestinationMailboxDialogHarness({
  config = baseConfig(),
  destinationFolders = ['Archive', 'INBOX'],
  destinationMeta = baseMeta(),
  onClose = vi.fn(),
  onSave = vi.fn(async () => {}),
  onSaveAndAuthenticate = vi.fn(async () => {}),
  onTestConnection = vi.fn(async () => ({ message: 'Connection test succeeded.' })),
  ...overrides
}) {
  const [destinationConfig, setDestinationConfig] = useState(config)

  return (
    <DestinationMailboxDialog
      destinationConfig={destinationConfig}
      destinationFolders={destinationFolders}
      destinationMeta={destinationMeta}
      onClose={onClose}
      onSave={onSave}
      onSaveAndAuthenticate={onSaveAndAuthenticate}
      onTestConnection={onTestConnection}
      onUnlink={vi.fn()}
      setDestinationConfig={setDestinationConfig}
      t={(key, params) => translate('en', key, params)}
      {...overrides}
    />
  )
}

describe('DestinationMailboxDialog', () => {
  it('defaults to the detected INBOX folder and can switch to manual entry', async () => {
    render(<DestinationMailboxDialogHarness />)

    await waitFor(() => {
      expect(screen.getByRole('combobox', { name: 'Folder' })).toHaveValue('INBOX')
    })

    fireEvent.click(screen.getByRole('button', { name: 'Enter folder manually' }))

    expect(screen.getByRole('textbox', { name: 'Folder' })).toHaveValue('INBOX')
    expect(screen.getByRole('button', { name: 'Use detected folders' })).toBeInTheDocument()
  })

  it('switches destination provider presets and rewrites the destination defaults', () => {
    render(<DestinationMailboxDialogHarness config={baseConfig({ folder: 'INBOX' })} destinationFolders={[]} destinationMeta={baseMeta({ linked: false })} />)

    fireEvent.change(screen.getByLabelText('Destination Provider'), { target: { value: 'YAHOO_IMAP' } })

    expect(screen.getByLabelText('Server hostname')).toHaveValue('imap.mail.yahoo.com')
    expect(screen.getByLabelText('Port')).toHaveValue(993)
    expect(screen.getByLabelText('Folder')).toHaveValue('INBOX')
    expect(screen.getByLabelText('Username')).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Show Password' })).toBeInTheDocument()
  })

  it('only enables connection testing for already-authenticated Microsoft OAuth destinations', () => {
    const { rerender } = render(
      <DestinationMailboxDialogHarness destinationMeta={baseMeta({ oauthConnected: false })} />
    )

    expect(screen.getByRole('button', { name: 'Test Connection' })).toBeDisabled()

    rerender(<DestinationMailboxDialogHarness destinationMeta={baseMeta({ oauthConnected: true })} />)

    expect(screen.getByRole('button', { name: 'Test Connection' })).toBeEnabled()
  })

  it('renders connection test failures from the dialog', async () => {
    const onTestConnection = vi.fn(async () => {
      throw new Error('Destination test failed')
    })

    render(<DestinationMailboxDialogHarness onTestConnection={onTestConnection} />)

    fireEvent.click(screen.getByRole('button', { name: 'Test Connection' }))

    expect(onTestConnection).toHaveBeenCalledTimes(1)
    expect(await screen.findByText('Destination test failed')).toBeInTheDocument()
  })

  it('shows Save only for folder-only Outlook edits and saves through the plain path', async () => {
    const onSave = vi.fn(async () => {})
    const onClose = vi.fn()

    render(
      <DestinationMailboxDialogHarness
        config={baseConfig({ folder: 'INBOX' })}
        destinationFolders={['INBOX', 'Archive']}
        onClose={onClose}
        onSave={onSave}
      />
    )

    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Enter folder manually' }))
    fireEvent.change(screen.getByRole('textbox', { name: 'Folder' }), { target: { value: 'Archive' } })

    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(onSave).toHaveBeenCalledTimes(1)
      expect(onClose).toHaveBeenCalledTimes(1)
    })
  })
})
