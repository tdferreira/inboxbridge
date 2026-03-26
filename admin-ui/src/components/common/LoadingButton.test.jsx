import { render, screen } from '@testing-library/react'
import LoadingButton from './LoadingButton'

describe('LoadingButton', () => {
  it('shows a spinner label and disables itself while loading', () => {
    render(
      <LoadingButton className="primary" isLoading loadingLabel="Saving…">
        Save
      </LoadingButton>
    )

    const button = screen.getByRole('button', { name: 'Saving…' })
    expect(button).toBeDisabled()
    expect(button.querySelector('.loading-button-spinner')).not.toBeNull()
  })
})
