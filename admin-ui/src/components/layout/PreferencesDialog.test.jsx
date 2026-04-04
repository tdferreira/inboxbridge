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
        detectedTimeZone="Europe/Lisbon"
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
        onTimeZoneChange={vi.fn()}
        onTimeZoneModeChange={vi.fn()}
        persistLayout={false}
        quickSetupVisible={false}
        savingLayout={false}
        selectableTimeZones={[{ value: 'Europe/Lisbon', label: 'Europe/Lisbon' }]}
        t={t}
        timezone=""
        timezoneMode="AUTO"
      />
    )

    expect(screen.getByRole('button', { name: 'Edit Layout' })).toBeInTheDocument()
    expect(checkboxForText('Show Quick Setup Guide')).toBeInTheDocument()
    expect(screen.getByText('Show Quick Setup Guide').closest('label')).toHaveClass('checkbox-row')
    expect(screen.getByText('Remember layout on this account').closest('label')).toHaveClass('checkbox-row')
    expect(screen.getByRole('button', { name: 'Language' })).toHaveTextContent('🇬🇧')
  })

  it('changes the preferences language through the flag menu', () => {
    const onLanguageChange = vi.fn()

    render(
      <PreferencesDialog
        canHideQuickSetup={true}
        detectedTimeZone="Europe/Lisbon"
        language="en"
        languageOptions={[
          { value: 'en', label: 'English' },
          { value: 'pt-PT', label: 'Português (Portugal)' }
        ]}
        layoutEditEnabled={false}
        onClose={vi.fn()}
        onExitLayoutEditing={vi.fn()}
        onLanguageChange={onLanguageChange}
        onPersistLayoutChange={vi.fn()}
        onQuickSetupVisibilityChange={vi.fn()}
        onResetLayout={vi.fn()}
        onStartLayoutEditing={vi.fn()}
        onTimeZoneChange={vi.fn()}
        onTimeZoneModeChange={vi.fn()}
        persistLayout={false}
        quickSetupVisible={false}
        savingLayout={false}
        selectableTimeZones={[{ value: 'Europe/Lisbon', label: 'Europe/Lisbon' }]}
        t={t}
        timezone=""
        timezoneMode="AUTO"
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Language' }))
    fireEvent.click(screen.getByRole('menuitemradio', { name: 'Português (Portugal)' }))

    expect(onLanguageChange).toHaveBeenCalledWith('pt-PT')
  })

  it('lets the user switch to a manual timezone and pick a specific zone', () => {
    const onTimeZoneModeChange = vi.fn()
    const onTimeZoneChange = vi.fn()

    render(
      <PreferencesDialog
        canHideQuickSetup={true}
        detectedTimeZone="Europe/Lisbon"
        language="en"
        languageOptions={[{ value: 'en', label: 'English' }]}
        layoutEditEnabled={false}
        onClose={vi.fn()}
        onExitLayoutEditing={vi.fn()}
        onLanguageChange={vi.fn()}
        onPersistLayoutChange={vi.fn()}
        onQuickSetupVisibilityChange={vi.fn()}
        onResetLayout={vi.fn()}
        onStartLayoutEditing={vi.fn()}
        onTimeZoneChange={onTimeZoneChange}
        onTimeZoneModeChange={onTimeZoneModeChange}
        persistLayout={false}
        quickSetupVisible={false}
        savingLayout={false}
        selectableTimeZones={[
          { value: 'Europe/Lisbon', label: 'Europe/Lisbon' },
          { value: 'America/New_York', label: 'America/New_York' }
        ]}
        t={t}
        timezone="Europe/Lisbon"
        timezoneMode="MANUAL"
      />
    )

    fireEvent.change(screen.getByDisplayValue('Set manually'), { target: { value: 'MANUAL' } })
    fireEvent.change(screen.getByDisplayValue('Europe/Lisbon'), { target: { value: 'America/New_York' } })

    expect(onTimeZoneModeChange).toHaveBeenCalledWith('MANUAL')
    expect(onTimeZoneChange).toHaveBeenCalledWith('America/New_York')
  })

  it('starts layout editing from the action button', () => {
    const onStartLayoutEditing = vi.fn()

    render(
      <PreferencesDialog
        canHideQuickSetup={true}
        detectedTimeZone="Europe/Lisbon"
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
        onTimeZoneChange={vi.fn()}
        onTimeZoneModeChange={vi.fn()}
        persistLayout={true}
        quickSetupVisible={true}
        savingLayout={false}
        selectableTimeZones={[{ value: 'Europe/Lisbon', label: 'Europe/Lisbon' }]}
        t={t}
        timezone=""
        timezoneMode="AUTO"
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
        detectedTimeZone="Europe/Lisbon"
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
        onTimeZoneChange={vi.fn()}
        onTimeZoneModeChange={vi.fn()}
        persistLayout={true}
        quickSetupVisible={true}
        savingLayout={false}
        selectableTimeZones={[{ value: 'Europe/Lisbon', label: 'Europe/Lisbon' }]}
        t={t}
        timezone=""
        timezoneMode="AUTO"
      />
    )

    const quickSetupCheckbox = checkboxForText('Show Quick Setup Guide')
    expect(quickSetupCheckbox).toBeDisabled()
    expect(onQuickSetupVisibilityChange).not.toHaveBeenCalled()
  })

  it('shows a save layout action when layout editing is active', () => {
    const onExitLayoutEditing = vi.fn()

    render(
      <PreferencesDialog
        canHideQuickSetup={true}
        detectedTimeZone="Europe/Lisbon"
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
        onTimeZoneChange={vi.fn()}
        onTimeZoneModeChange={vi.fn()}
        persistLayout={true}
        quickSetupVisible={true}
        savingLayout={false}
        selectableTimeZones={[{ value: 'Europe/Lisbon', label: 'Europe/Lisbon' }]}
        t={t}
        timezone=""
        timezoneMode="AUTO"
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Save Layout' }))
    expect(onExitLayoutEditing).toHaveBeenCalledTimes(1)
  })
})
