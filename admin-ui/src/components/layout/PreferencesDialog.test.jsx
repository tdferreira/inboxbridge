import { fireEvent, render, screen } from '@testing-library/react'

import PreferencesDialog from './PreferencesDialog'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)

function checkboxForText(text) {
  return screen.getByText(text).closest('label')?.querySelector('input[type="checkbox"]')
}

describe('PreferencesDialog', () => {
  it('renders the edit layout action and quick setup visibility controls', () => {
    render(
      <PreferencesDialog
        canHideQuickSetup={true}
        language="en"
        languageOptions={[
          { value: 'en', label: 'English' },
          { value: 'pt-PT', label: 'Portuguese (Portugal)' }
        ]}
        layoutEditEnabled={false}
        onClose={vi.fn()}
        onExitLayoutEditing={vi.fn()}
        onLanguageChange={vi.fn()}
        onPersistLayoutChange={vi.fn()}
        onQuickSetupVisibilityChange={vi.fn()}
        onResetLayout={vi.fn()}
        onStartLayoutEditing={vi.fn()}
        persistLayout={false}
        quickSetupVisible={false}
        savingLayout={false}
        t={t}
      />
    )

    expect(screen.getByRole('button', { name: 'Edit Layout' })).toBeInTheDocument()
    expect(checkboxForText('Show Quick Setup Guide')).toBeInTheDocument()
  })

  it('starts layout editing from the action button', () => {
    const onStartLayoutEditing = vi.fn()

    render(
      <PreferencesDialog
        canHideQuickSetup={true}
        language="en"
        languageOptions={[{ value: 'en', label: 'English' }]}
        layoutEditEnabled={false}
        onClose={vi.fn()}
        onExitLayoutEditing={vi.fn()}
        onLanguageChange={vi.fn()}
        onPersistLayoutChange={vi.fn()}
        onQuickSetupVisibilityChange={vi.fn()}
        onResetLayout={vi.fn()}
        onStartLayoutEditing={onStartLayoutEditing}
        persistLayout={true}
        quickSetupVisible={true}
        savingLayout={false}
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Edit Layout' }))
    expect(onStartLayoutEditing).toHaveBeenCalledTimes(1)
  })

  it('disables the quick setup toggle when incomplete setup requires the guide to stay visible', () => {
    const onQuickSetupVisibilityChange = vi.fn()

    render(
      <PreferencesDialog
        canHideQuickSetup={false}
        language="en"
        languageOptions={[{ value: 'en', label: 'English' }]}
        layoutEditEnabled={true}
        onClose={vi.fn()}
        onExitLayoutEditing={vi.fn()}
        onLanguageChange={vi.fn()}
        onPersistLayoutChange={vi.fn()}
        onQuickSetupVisibilityChange={onQuickSetupVisibilityChange}
        onResetLayout={vi.fn()}
        onStartLayoutEditing={vi.fn()}
        persistLayout={true}
        quickSetupVisible={true}
        savingLayout={false}
        t={t}
      />
    )

    const quickSetupCheckbox = checkboxForText('Show Quick Setup Guide')
    expect(quickSetupCheckbox).toBeDisabled()
    expect(onQuickSetupVisibilityChange).not.toHaveBeenCalled()
  })

  it('shows an exit layout editing action when layout editing is active', () => {
    const onExitLayoutEditing = vi.fn()

    render(
      <PreferencesDialog
        canHideQuickSetup={true}
        language="en"
        languageOptions={[{ value: 'en', label: 'English' }]}
        layoutEditEnabled={true}
        onClose={vi.fn()}
        onExitLayoutEditing={onExitLayoutEditing}
        onLanguageChange={vi.fn()}
        onPersistLayoutChange={vi.fn()}
        onQuickSetupVisibilityChange={vi.fn()}
        onResetLayout={vi.fn()}
        onStartLayoutEditing={vi.fn()}
        persistLayout={true}
        quickSetupVisible={true}
        savingLayout={false}
        t={t}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Exit Layout Editing' }))
    expect(onExitLayoutEditing).toHaveBeenCalledTimes(1)
  })
})
