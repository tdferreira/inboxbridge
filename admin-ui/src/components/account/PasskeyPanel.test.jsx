import { fireEvent, render, screen } from '@testing-library/react'
import PasskeyPanel from './PasskeyPanel'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('PasskeyPanel', () => {
  it('renders registered passkeys and forwards create/remove actions', () => {
    const onOpenRegistrationDialog = vi.fn()
    const onDeletePasskey = vi.fn()

    render(
      <PasskeyPanel
        createLoading={false}
        deleteLoadingId={null}
        onDeletePasskey={onDeletePasskey}
        onOpenRegistrationDialog={onOpenRegistrationDialog}
        passwordConfigured
        passkeys={[{ id: 9, label: 'MacBook', discoverable: true, backupEligible: true, backedUp: true, createdAt: '2026-03-26T10:00:00Z', lastUsedAt: null }]}
        supported
        locale="en"
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Register Passkey' }))
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }))

    expect(onOpenRegistrationDialog).toHaveBeenCalled()
    expect(onDeletePasskey).toHaveBeenCalledWith(9)
  })

  it('blocks removing the last passkey on a passwordless account', () => {
    render(
      <PasskeyPanel
        createLoading={false}
        deleteLoadingId={null}
        onDeletePasskey={vi.fn()}
        onOpenRegistrationDialog={vi.fn()}
        passwordConfigured={false}
        passkeys={[{ id: 9, label: 'MacBook', discoverable: true, backupEligible: true, backedUp: true, createdAt: '2026-03-26T10:00:00Z', lastUsedAt: null }]}
        supported
        locale="en"
        t={t}
      />
    )

    expect(screen.getByRole('button', { name: 'Remove' })).toBeDisabled()
    expect(screen.getByText(/only sign-in method left/i)).toBeInTheDocument()
  })

  it('renders translated passkey copy in portuguese', () => {
    render(
      <PasskeyPanel
        createLoading={false}
        deleteLoadingId={null}
        onDeletePasskey={vi.fn()}
        onOpenRegistrationDialog={vi.fn()}
        passwordConfigured
        passkeys={[]}
        supported
        locale="pt-PT"
        t={(key, params) => translate('pt-PT', key, params)}
      />
    )

    expect(screen.getByText('Passkeys')).toBeInTheDocument()
    expect(screen.getByText('Registe uma passkey associada ao dispositivo para entrar sem voltar a escrever a palavra-passe.')).toBeInTheDocument()
  })
})
