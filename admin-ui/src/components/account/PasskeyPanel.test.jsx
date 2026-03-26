import { fireEvent, render, screen } from '@testing-library/react'
import PasskeyPanel from './PasskeyPanel'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('PasskeyPanel', () => {
  it('renders registered passkeys and forwards create/remove actions', () => {
    const onCreatePasskey = vi.fn((event) => event.preventDefault())
    const onDeletePasskey = vi.fn()
    const onPasskeyLabelChange = vi.fn()

    render(
      <PasskeyPanel
        createLoading={false}
        deleteLoadingId={null}
        onCreatePasskey={onCreatePasskey}
        onDeletePasskey={onDeletePasskey}
        onPasskeyLabelChange={onPasskeyLabelChange}
        passwordConfigured
        passkeyLabel=""
        passkeys={[{ id: 9, label: 'MacBook', discoverable: true, backupEligible: true, backedUp: true, createdAt: '2026-03-26T10:00:00Z', lastUsedAt: null }]}
        supported
        locale="en"
        t={t}
      />
    )

    fireEvent.change(screen.getByLabelText('Passkey Label'), { target: { value: 'Office Key' } })
    fireEvent.click(screen.getByRole('button', { name: 'Register Passkey' }))
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }))

    expect(onPasskeyLabelChange).toHaveBeenCalled()
    expect(onCreatePasskey).toHaveBeenCalled()
    expect(onDeletePasskey).toHaveBeenCalledWith(9)
  })

  it('blocks removing the last passkey on a passwordless account', () => {
    render(
      <PasskeyPanel
        createLoading={false}
        deleteLoadingId={null}
        onCreatePasskey={vi.fn()}
        onDeletePasskey={vi.fn()}
        onPasskeyLabelChange={vi.fn()}
        passwordConfigured={false}
        passkeyLabel=""
        passkeys={[{ id: 9, label: 'MacBook', discoverable: true, backupEligible: true, backedUp: true, createdAt: '2026-03-26T10:00:00Z', lastUsedAt: null }]}
        supported
        locale="en"
        t={t}
      />
    )

    expect(screen.getByRole('button', { name: 'Remove' })).toBeDisabled()
    expect(screen.getByText(/only sign-in method left/i)).toBeInTheDocument()
  })
})
