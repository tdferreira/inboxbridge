import { fireEvent, render, screen } from '@testing-library/react'
import UserEmailAccountsSection from './UserEmailAccountsSection'
import { translate } from '@/lib/i18n'

describe('UserEmailAccountsSection', () => {
  it('renders translated source email account copy and empty state in portuguese', () => {
    const onAddFetcher = vi.fn()

    render(
      <UserEmailAccountsSection
        emailAccountForm={{
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
        collapsed={false}
        collapseLoading={false}
        connectingEmailAccountId=""
        deletingEmailAccountId=""
        duplicateIdError=""
        fetchers={[]}
        fetcherDialogOpen={false}
        fetcherPollLoadingIds={[]}
        fetcherPollingDialog={null}
        fetcherPollingForm={null}
        fetcherPollingLoading={false}
        onAddFetcher={onAddFetcher}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onCloseDialog={vi.fn()}
        onClosePollingDialog={vi.fn()}
        onCollapseToggle={vi.fn()}
        onConfigureFetcherPolling={vi.fn()}
        onConnectOAuth={vi.fn()}
        onDeleteEmailAccount={vi.fn()}
        onEditEmailAccount={vi.fn()}
        onFetcherPollingFormChange={vi.fn()}
        onResetFetcherPollingSettings={vi.fn()}
        onRunFetcherPoll={vi.fn()}
        onSaveEmailAccount={vi.fn()}
        onSaveEmailAccountAndConnectOAuth={vi.fn()}
        onSaveFetcherPollingSettings={vi.fn()}
        onTestEmailAccountConnection={vi.fn()}
        saveLoading={false}
        locale="pt-PT"
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByText('As minhas contas de email de origem')).toBeInTheDocument()
    expect(screen.getByText('Gira as caixas de email das quais o InboxBridge vai buscar mensagens.')).toBeInTheDocument()
    expect(screen.getByText('Ainda não existem contas de email de origem configuradas.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Adicionar conta de email' }))
    expect(onAddFetcher).toHaveBeenCalledTimes(1)
  })

  it('shows a refresh indicator while the section is reloading', () => {
    render(
      <UserEmailAccountsSection
        emailAccountForm={{
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
        collapsed={false}
        collapseLoading={false}
        connectingEmailAccountId=""
        deletingEmailAccountId=""
        duplicateIdError=""
        fetchers={[]}
        fetcherDialogOpen={false}
        fetcherPollLoadingIds={[]}
        fetcherPollingDialog={null}
        fetcherPollingForm={null}
        fetcherPollingLoading={false}
        fetcherRefreshLoadingId={null}
        onAddFetcher={vi.fn()}
        onApplyPreset={vi.fn()}
        onEmailAccountFormChange={vi.fn()}
        onCloseDialog={vi.fn()}
        onClosePollingDialog={vi.fn()}
        onCollapseToggle={vi.fn()}
        onConfigureFetcherPolling={vi.fn()}
        onConnectOAuth={vi.fn()}
        onDeleteEmailAccount={vi.fn()}
        onEditEmailAccount={vi.fn()}
        onFetcherPollingFormChange={vi.fn()}
        onFetcherToggleExpand={vi.fn()}
        onResetFetcherPollingSettings={vi.fn()}
        onRunFetcherPoll={vi.fn()}
        onSaveEmailAccount={vi.fn()}
        onSaveEmailAccountAndConnectOAuth={vi.fn()}
        onSaveFetcherPollingSettings={vi.fn()}
        onTestEmailAccountConnection={vi.fn()}
        saveLoading={false}
        sectionLoading
        locale="en"
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('Refreshing section…')).toBeInTheDocument()
  })
})
