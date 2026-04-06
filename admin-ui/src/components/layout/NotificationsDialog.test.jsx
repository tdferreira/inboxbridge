import { fireEvent, render, screen } from '@testing-library/react'
import NotificationsDialog from './NotificationsDialog'
import { translate } from '../../lib/i18n'

describe('NotificationsDialog', () => {
  it('renders saved notifications and supports clearing them', () => {
    const onClearAll = vi.fn()
    const onDismissNotification = vi.fn()

    render(
      <NotificationsDialog
        notifications={[
          { id: 'n1', message: 'Mailbox check failed.', tone: 'error', repeatCount: 3 },
          { id: 'n2', message: 'Mail imported successfully.', tone: 'success' }
        ]}
        onClearAll={onClearAll}
        onClose={vi.fn()}
        onDismissNotification={onDismissNotification}
        onFocusNotification={vi.fn()}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('Mailbox check failed.')).toBeInTheDocument()
    expect(screen.getByText('Mail imported successfully.')).toBeInTheDocument()
    expect(screen.getByText('×3')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Clear All' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Dismiss notification' })[0])

    expect(onClearAll).toHaveBeenCalledTimes(1)
    expect(onDismissNotification).toHaveBeenCalledWith('n1')
  })

  it('shows an empty state when there are no saved notifications', () => {
    render(
      <NotificationsDialog
        notifications={[]}
        onClearAll={vi.fn()}
        onClose={vi.fn()}
        onDismissNotification={vi.fn()}
        onFocusNotification={vi.fn()}
        t={(key, params) => translate('en', key, params)}
      />
    )

    expect(screen.getByText('No recent notifications.')).toBeInTheDocument()
  })

  it('routes focusable notifications through the related-section action', () => {
    const onFocusNotification = vi.fn()

    render(
      <NotificationsDialog
        notifications={[
          { id: 'n1', message: 'My destination mailbox needs attention.', tone: 'error', targetId: 'destination-mailbox-section' }
        ]}
        onClearAll={vi.fn()}
        onClose={vi.fn()}
        onDismissNotification={vi.fn()}
        onFocusNotification={onFocusNotification}
        t={(key, params) => translate('en', key, params)}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Focus the related section' }))

    expect(onFocusNotification).toHaveBeenCalledWith('destination-mailbox-section')
  })
})
