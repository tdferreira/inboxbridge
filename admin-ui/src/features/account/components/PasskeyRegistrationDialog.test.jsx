import { fireEvent, render, screen } from '@testing-library/react'
import PasskeyRegistrationDialog from './PasskeyRegistrationDialog'
import { translate } from '@/lib/i18n'

describe('PasskeyRegistrationDialog', () => {
  it('submits the label and confirms before cancelling dirty changes', () => {
    const onClose = vi.fn()
    const onPasskeyLabelChange = vi.fn()
    const onSubmit = vi.fn((event) => event.preventDefault())
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)

    render(
      <PasskeyRegistrationDialog
        isLoading={false}
        onClose={onClose}
        onPasskeyLabelChange={onPasskeyLabelChange}
        onSubmit={onSubmit}
        passkeyLabel="Laptop key"
        t={(key, params) => translate('en', key, params)}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    fireEvent.change(screen.getByLabelText('Passkey Label'), { target: { value: 'Office key' } })
    fireEvent.click(screen.getByRole('button', { name: 'Register Passkey' }))

    expect(confirmSpy).toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
    expect(onPasskeyLabelChange).toHaveBeenCalled()
    expect(onSubmit).toHaveBeenCalled()

    confirmSpy.mockRestore()
  })
})
