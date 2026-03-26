import { render, screen } from '@testing-library/react'
import SetupGuidePanel from './SetupGuidePanel'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('SetupGuidePanel', () => {
  it('renders role-aware bootstrap guidance and live setup status', () => {
    render(
      <SetupGuidePanel
        collapsed={false}
        onFocusSection={vi.fn()}
        onPersistLayoutChange={vi.fn()}
        onToggleCollapse={vi.fn()}
        persistLayout={false}
        savingLayout={false}
        steps={[
          {
            title: '1. Secure your session',
            description: 'Change your password.',
            status: 'pending',
            targetId: 'password-panel-section'
          },
          {
            title: '2. Configure the Gmail destination',
            description: 'Ready to continue.',
            status: 'complete',
            targetId: 'gmail-destination-section'
          },
          {
            title: '4. Complete provider OAuth',
            description: 'Bridge failed.',
            status: 'error',
            targetId: 'source-bridges-section'
          }
        ]}
        t={t}
      />
    )

    expect(screen.getByText('Quick Setup Guide')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /secure your session/i })).toHaveAttribute('href', '#password-panel-section')
    expect(screen.getByRole('link', { name: /configure the gmail destination/i })).toHaveClass('setup-guide-complete')
    expect(screen.getByRole('link', { name: /complete provider oauth/i })).toHaveClass('setup-guide-error')
    expect(screen.getByLabelText(/remember layout on this account/i)).toBeInTheDocument()
  })
})
