import { fireEvent, render, screen } from '@testing-library/react'
import InstallPromptCard from './InstallPromptCard'
import { translate } from '@/lib/i18n'

describe('InstallPromptCard', () => {
  const t = (key, params) => translate('en', key, params)

  it('renders install and dismiss actions in the header by default', () => {
    const onDismiss = vi.fn()
    const onInstall = vi.fn()

    render(
      <InstallPromptCard
        canPromptInstall
        onDismiss={onDismiss}
        onInstall={onInstall}
        showInstallAction
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Not now' }))
    fireEvent.click(screen.getByRole('button', { name: 'Install App' }))

    expect(onDismiss).toHaveBeenCalledTimes(1)
    expect(onInstall).toHaveBeenCalledTimes(1)
  })

  it('moves the dismiss action into the body when requested and falls back to a plain install button', () => {
    const onDismiss = vi.fn()
    const onInstall = vi.fn()

    render(
      <InstallPromptCard
        canPromptInstall={false}
        dismissInBody
        onDismiss={onDismiss}
        onInstall={onInstall}
        showInstallAction
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Not now' }))
    fireEvent.click(screen.getByRole('button', { name: 'Install App' }))

    expect(onDismiss).toHaveBeenCalledTimes(1)
    expect(onInstall).toHaveBeenCalledTimes(1)
  })
})
