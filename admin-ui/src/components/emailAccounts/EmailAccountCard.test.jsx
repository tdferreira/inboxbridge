import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import EmailAccountCard from './EmailAccountCard'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('EmailAccountCard', () => {
  it('shows bridge metadata and emits edit/delete/oauth actions', () => {
    const onEdit = vi.fn()
    const onDelete = vi.fn()
    const onConnectMicrosoft = vi.fn()
    const emailAccount = {
      emailAccountId: 'outlook-main',
      protocol: 'IMAP',
      authMethod: 'OAUTH2',
      oauthProvider: 'MICROSOFT',
      host: 'outlook.office365.com',
      port: 993,
      tls: true,
      tokenStorageMode: 'DATABASE',
      totalImportedMessages: 42,
      lastImportedAt: '2026-03-26T12:00:00Z',
      folder: 'INBOX',
      lastEvent: {
        status: 'SUCCESS',
        finishedAt: '2026-03-26T12:00:00Z',
        trigger: 'manual',
        fetched: 10,
        imported: 9,
        duplicates: 1,
        error: ''
      }
    }

    render(
      <EmailAccountCard
        emailAccount={emailAccount}
        onConnectMicrosoft={onConnectMicrosoft}
        onDelete={onDelete}
        onEdit={onEdit}
        showDelete
        showEdit
        locale="en"
        t={t}
      />
    )

    expect(screen.getByText('outlook-main')).toBeInTheDocument()
    expect(screen.getByText('Encrypted DB')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
    fireEvent.click(screen.getByRole('button', { name: 'Connect Microsoft OAuth' }))
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }))

    expect(onEdit).toHaveBeenCalledWith(emailAccount)
    expect(onConnectMicrosoft).toHaveBeenCalledWith('outlook-main')
    expect(onDelete).toHaveBeenCalledWith('outlook-main')
  })

  it('offers one-click copy for bridge error payloads', async () => {
    const writeText = vi.fn().mockResolvedValue()
    Object.assign(navigator, { clipboard: { writeText } })

    render(
      <EmailAccountCard
        emailAccount={{
          id: 'bridge-with-error',
          protocol: 'IMAP',
          authMethod: 'OAUTH2',
          oauthProvider: 'MICROSOFT',
          host: 'outlook.office365.com',
          port: 993,
          tls: true,
          tokenStorageMode: 'DATABASE',
          totalImportedMessages: 0,
          lastImportedAt: null,
          folder: 'INBOX',
          lastEvent: {
            status: 'ERROR',
            finishedAt: '2026-03-26T12:00:00Z',
            trigger: 'scheduler',
            fetched: 0,
            imported: 0,
            duplicates: 0,
            error: 'AADSTS65001 consent_required'
          }
        }}
        onConnectMicrosoft={vi.fn()}
        locale="en"
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Copy Error' }))

    await waitFor(() => expect(writeText).toHaveBeenCalledWith('AADSTS65001 consent_required'))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Copied' })).toBeInTheDocument())
  })

  it('renders translated bridge labels in portuguese', () => {
    render(
      <EmailAccountCard
        emailAccount={{
          emailAccountId: 'outlook-main',
          protocol: 'IMAP',
          authMethod: 'OAUTH2',
          oauthProvider: 'MICROSOFT',
          host: 'outlook.office365.com',
          port: 993,
          tls: true,
          tokenStorageMode: 'DATABASE',
          totalImportedMessages: 3,
          lastImportedAt: null,
          folder: 'INBOX',
          lastEvent: null
        }}
        locale="pt-PT"
        onConnectMicrosoft={vi.fn()}
        onDelete={vi.fn()}
        onEdit={vi.fn()}
        showDelete
        showEdit
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByText('Anfitrião')).toBeInTheDocument()
    expect(screen.getByText('Armazenamento do token')).toBeInTheDocument()
    expect(screen.getByText('BD encriptada')).toBeInTheDocument()
    expect(screen.getByText('Ainda não existe atividade de polling registada.')).toBeInTheDocument()
    expect(screen.getByText('Pasta')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Editar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Apagar' })).toBeInTheDocument()
  })
})
