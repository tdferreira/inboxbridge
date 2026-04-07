import { fireEvent, render, screen } from '@testing-library/react'
import ConfirmationDialog from './ConfirmationDialog'

describe('ConfirmationDialog', () => {
  it('renders confirmation copy and forwards confirm/cancel actions', () => {
    const onConfirm = vi.fn()
    const onCancel = vi.fn()

    render(
      <ConfirmationDialog
        body="This action affects account access."
        cancelLabel="Cancel"
        confirmLabel="Confirm action"
        confirmLoading={false}
        confirmLoadingLabel="Applying…"
        onCancel={onCancel}
        onConfirm={onConfirm}
        title="Suspend user?"
      />
    )

    expect(screen.getByText('This action affects account access.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Confirm action' }))
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onConfirm).toHaveBeenCalledTimes(1)
    expect(onCancel).toHaveBeenCalledTimes(1)
  })
})
