import { act, fireEvent, render, screen } from '@testing-library/react'
import CopyButton from './CopyButton'
import { copyText } from '@/lib/clipboard'

vi.mock('@/lib/clipboard', () => ({
  copyText: vi.fn()
}))

describe('CopyButton', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('copies text and temporarily swaps the accessible label', async () => {
    copyText.mockResolvedValue(undefined)

    render(<CopyButton copiedLabel="Copied" label="Copy" text="hello" />)

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Copy' }))
    })

    expect(copyText).toHaveBeenCalledWith('hello')
    expect(screen.getByRole('button', { name: 'Copied' })).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(1500)
    })

    expect(screen.getByRole('button', { name: 'Copy' })).toBeInTheDocument()
  })

  it('keeps the default label when copying fails', async () => {
    copyText.mockRejectedValue(new Error('Clipboard unavailable'))

    render(<CopyButton copiedLabel="Copied" label="Copy" text="hello" />)

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Copy' }))
    })

    expect(screen.getByRole('button', { name: 'Copy' })).toBeInTheDocument()
  })
})
