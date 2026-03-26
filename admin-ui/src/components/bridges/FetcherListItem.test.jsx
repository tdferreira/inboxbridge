import { fireEvent, render, screen } from '@testing-library/react'
import FetcherListItem from './FetcherListItem'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('FetcherListItem', () => {
  it('closes the contextual menu when clicking outside', () => {
    render(
      <div>
        <FetcherListItem
          fetcher={{
            bridgeId: 'fetcher-1',
            customLabel: '',
            managementSource: 'DATABASE',
            protocol: 'IMAP',
            host: 'imap.example.com',
            port: 993,
            authMethod: 'PASSWORD',
            oauthProvider: 'NONE',
            tls: true,
            folder: 'INBOX',
            tokenStorageMode: 'PASSWORD',
            totalImportedMessages: 0,
            lastImportedAt: null,
            effectivePollEnabled: true,
            effectivePollInterval: '5m',
            effectiveFetchWindow: 50,
            pollingState: null,
            lastEvent: null,
            canEdit: true,
            canDelete: true,
            canConnectMicrosoft: false
          }}
          locale="en"
          onConnectMicrosoft={vi.fn()}
          onDelete={vi.fn()}
          onEdit={vi.fn()}
          t={t}
        />
        <button type="button">Outside</button>
      </div>
    )

    fireEvent.click(screen.getByRole('button', { name: 'Fetcher actions' }))
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()

    fireEvent.mouseDown(screen.getByRole('button', { name: 'Outside' }))

    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
  })
})
