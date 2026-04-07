import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import Banner from './Banner'

describe('Banner', () => {
  it('shows a copy button for copyable error payloads', async () => {
    const writeText = vi.fn().mockResolvedValue()
    Object.assign(navigator, { clipboard: { writeText } })

    render(<Banner tone="error" copyText="payload-123">payload-123</Banner>)

    fireEvent.click(screen.getByRole('button', { name: 'Copy Error' }))

    await waitFor(() => expect(writeText).toHaveBeenCalledWith('payload-123'))
    expect(screen.getByRole('button', { name: 'Copied' })).toBeInTheDocument()
  })

  it('uses the accessible copy label as a tooltip while rendering an icon button', () => {
    render(<Banner tone="error" copyText="payload-123">payload-123</Banner>)

    const copyButton = screen.getByRole('button', { name: 'Copy Error' })
    expect(copyButton).toHaveAttribute('title', 'Copy Error')
    expect(copyButton).not.toHaveTextContent('Copy Error')
  })

  it('supports focusing and dismissing actionable notifications', () => {
    const onDismiss = vi.fn()
    const onFocus = vi.fn()

    render(
      <Banner onDismiss={onDismiss} onFocus={onFocus} tone="warning">
        Password must be updated.
      </Banner>
    )

    fireEvent.click(screen.getByRole('button', { name: 'Focus the related section' }))
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss notification' }))

    expect(onFocus).toHaveBeenCalledTimes(1)
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })

  it('renders a repeat-count badge when the same notification was grouped several times', () => {
    render(<Banner repeatCount={4} tone="warning">Repeated warning</Banner>)

    expect(screen.getByText('×4')).toBeInTheDocument()
  })

  it('makes the whole banner clickable without letting action buttons trigger focus', async () => {
    const onDismiss = vi.fn()
    const onFocus = vi.fn()
    const writeText = vi.fn().mockResolvedValue()
    Object.assign(navigator, { clipboard: { writeText } })

    render(
      <Banner copyText="payload-123" onDismiss={onDismiss} onFocus={onFocus} tone="warning">
        Password must be updated.
      </Banner>
    )

    fireEvent.click(screen.getByRole('button', { name: 'Focus the related section' }))
    fireEvent.click(screen.getByRole('button', { name: 'Copy Error' }))
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss notification' }))

    await waitFor(() => expect(writeText).toHaveBeenCalledWith('payload-123'))
    expect(onFocus).toHaveBeenCalledTimes(1)
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })
})
