import { fireEvent, render, screen } from '@testing-library/react'

import PreferencesDialog from './PreferencesDialog'
import { DATE_FORMAT_DMY_12, describeAutomaticDateFormat, localizeCustomDateFormatPattern } from '../../lib/formatters'
import { translate } from '../../lib/i18n'

const t = (key, params) => translate('en', key, params)
const tPt = (key, params) => translate('pt-PT', key, params)

function checkboxForText(text) {
  return screen.getByText(text).closest('label')?.querySelector('input[type="checkbox"]')
}

function dateFormatSelect() {
  return screen.getByText('Date format').closest('label')?.querySelector('select')
}

function renderDialog(overrides = {}) {
  return render(
    <PreferencesDialog
      canHideQuickSetup={true}
      detectedTimeZone="Europe/Lisbon"
      language="en"
      languageOptions={[{ value: 'en', label: 'English' }]}
      layoutEditEnabled={false}
      onClose={vi.fn()}
      onDateFormatChange={vi.fn()}
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
      dateFormat="AUTO"
      timezone=""
      timezoneMode="AUTO"
      {...overrides}
    />
  )
}

describe('PreferencesDialog', () => {
  const automaticLabel = `Automatic (${describeAutomaticDateFormat('en')})`

  it('renders the edit layout action and quick setup visibility controls', () => {
    renderDialog({
      languageOptions: [
        { value: 'en', label: 'English' },
        { value: 'pt-PT', label: 'Portuguese (Portugal)' }
      ]
    })

    expect(screen.getByRole('button', { name: 'Edit Layout' })).toBeInTheDocument()
    expect(checkboxForText('Show Quick Setup Guide')).toBeInTheDocument()
    expect(screen.getByText('Show Quick Setup Guide').closest('label')).toHaveClass('checkbox-row')
    expect(screen.getByText('Remember layout on this account').closest('label')).toHaveClass('checkbox-row')
    expect(screen.getByRole('button', { name: 'Language' })).toHaveTextContent('🇬🇧')
    expect(dateFormatSelect()).toHaveDisplayValue(automaticLabel)
  })

  it('changes the preferences language through the flag menu', () => {
    const onLanguageChange = vi.fn()

    renderDialog({
      languageOptions: [
        { value: 'en', label: 'English' },
        { value: 'pt-PT', label: 'Português (Portugal)' }
      ],
      onLanguageChange
    })

    fireEvent.click(screen.getByRole('button', { name: 'Language' }))
    fireEvent.click(screen.getByRole('menuitemradio', { name: 'Português (Portugal)' }))

    expect(onLanguageChange).toHaveBeenCalledWith('pt-PT')
  })

  it('lets the user switch to a manual timezone and pick a specific zone', () => {
    const onTimeZoneModeChange = vi.fn()
    const onTimeZoneChange = vi.fn()

    renderDialog({
      onTimeZoneChange,
      onTimeZoneModeChange,
      selectableTimeZones: [
        { value: 'Europe/Lisbon', label: 'Europe/Lisbon' },
        { value: 'America/New_York', label: 'America/New_York' }
      ],
      timezone: 'Europe/Lisbon',
      timezoneMode: 'MANUAL'
    })

    fireEvent.change(screen.getByDisplayValue('Set manually'), { target: { value: 'MANUAL' } })
    fireEvent.change(screen.getByDisplayValue('Europe/Lisbon'), { target: { value: 'America/New_York' } })

    expect(onTimeZoneModeChange).toHaveBeenCalledWith('MANUAL')
    expect(onTimeZoneChange).toHaveBeenCalledWith('America/New_York')
  })

  it('renders the timezone selector before the date-format selector when timezone is manual', () => {
    renderDialog({
      selectableTimeZones: [
        { value: 'Europe/Lisbon', label: 'Europe/Lisbon' },
        { value: 'America/New_York', label: 'America/New_York' }
      ],
      timezone: 'Europe/Lisbon',
      timezoneMode: 'MANUAL'
    })

    const timezoneLabel = screen.getByText('Timezone').closest('label')
    const dateFormatLabel = screen.getByText('Date format').closest('label')
    expect(timezoneLabel.compareDocumentPosition(dateFormatLabel) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('lets the user pick a manual date format', () => {
    const onDateFormatChange = vi.fn()

    renderDialog({ onDateFormatChange })

    fireEvent.change(dateFormatSelect(), { target: { value: 'DMY_12' } })

    expect(onDateFormatChange).toHaveBeenCalledWith('DMY_12')
  })

  it('shows the predefined date format options in the requested order', () => {
    renderDialog()

    const optionValues = Array.from(dateFormatSelect().querySelectorAll('option')).map((option) => option.value)
    expect(optionValues).toEqual(['AUTO', 'DMY_24', 'DMY_12', 'MDY_24', 'MDY_12', 'YMD_24', 'YMD_12', 'CUSTOM'])
  })

  it('opens a custom date-format dialog and saves a valid custom pattern', () => {
    const onDateFormatChange = vi.fn()

    renderDialog({ onDateFormatChange })

    fireEvent.change(dateFormatSelect(), { target: { value: 'CUSTOM' } })
    expect(screen.getByRole('dialog', { name: 'Custom date format' })).toBeInTheDocument()
    fireEvent.change(screen.getByPlaceholderText('YYYY-MM-DD HH:mm:ss'), { target: { value: 'DD/MM/YYYY HH:mm:ss' } })
    expect(screen.getByText('Example: 06/04/2026 14:24:56')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(onDateFormatChange).toHaveBeenCalledWith('DD/MM/YYYY HH:mm:ss')
  })

  it('prefills the custom date-format dialog from the currently selected preset', () => {
    renderDialog({ dateFormat: DATE_FORMAT_DMY_12 })

    fireEvent.change(dateFormatSelect(), { target: { value: 'CUSTOM' } })

    expect(screen.getByPlaceholderText('YYYY-MM-DD HH:mm:ss')).toHaveValue('DD/MM/YYYY hh:mm:ss A')
  })

  it('shows token suggestions on focus and keeps the helper text visible below the example', () => {
    renderDialog({ dateFormat: DATE_FORMAT_DMY_12 })

    fireEvent.change(dateFormatSelect(), { target: { value: 'CUSTOM' } })

    const input = screen.getByPlaceholderText('YYYY-MM-DD HH:mm:ss')
    fireEvent.change(input, { target: { value: '' } })
    fireEvent.focus(input)

    expect(screen.getByRole('listbox')).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /YYYY- Four-digit year\(2026\)/i })).toBeInTheDocument()
    expect(screen.getByText('Supported tokens: YYYY, YY, MMMM, MMM, MM, DD, dddd, ddd, HH, H, hh, h, mm, M, ss, S, A. You can add separators like spaces, slashes, hyphens, commas, or colons.')).toBeInTheDocument()
  })

  it('shows all token suggestions when the caret is after a separator', () => {
    renderDialog({ dateFormat: DATE_FORMAT_DMY_12 })

    fireEvent.change(dateFormatSelect(), { target: { value: 'CUSTOM' } })

    const input = screen.getByPlaceholderText('YYYY-MM-DD HH:mm:ss')
    fireEvent.change(input, { target: { value: 'DD/' } })
    input.setSelectionRange(3, 3)
    fireEvent.focus(input)

    expect(screen.getByRole('option', { name: /YYYY- Four-digit year\(2026\)/i })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /HH- 24-hour clock with leading zero\(09\)/i })).toBeInTheDocument()
  })

  it('filters token suggestions from the token fragment at the caret position', () => {
    renderDialog({ dateFormat: DATE_FORMAT_DMY_12 })

    fireEvent.change(dateFormatSelect(), { target: { value: 'CUSTOM' } })

    const input = screen.getByPlaceholderText('YYYY-MM-DD HH:mm:ss')
    fireEvent.change(input, { target: { value: 'Y' } })
    input.setSelectionRange(0, 0)
    fireEvent.focus(input)

    expect(screen.getByRole('option', { name: /YYYY- Four-digit year\(2026\)/i })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /HH- 24-hour clock with leading zero\(09\)/i })).not.toBeInTheDocument()
  })

  it('inserts a suggested token into the custom date-format input', () => {
    renderDialog({ dateFormat: DATE_FORMAT_DMY_12 })

    fireEvent.change(dateFormatSelect(), { target: { value: 'CUSTOM' } })

    const input = screen.getByPlaceholderText('YYYY-MM-DD HH:mm:ss')
    fireEvent.change(input, { target: { value: 'ddd' } })
    fireEvent.focus(input)
    fireEvent.mouseDown(screen.getByRole('option', { name: /dddd- Full weekday name\(Monday\)/i }))

    expect(input).toHaveValue('dddd')
  })

  it('localizes the token suggestions for non-English languages', () => {
    render(
      <PreferencesDialog
        canHideQuickSetup={true}
        detectedTimeZone="Europe/Lisbon"
        language="pt-PT"
        languageOptions={[{ value: 'pt-PT', label: 'Português (Portugal)' }]}
        layoutEditEnabled={false}
        onClose={vi.fn()}
        onDateFormatChange={vi.fn()}
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
        t={tPt}
        dateFormat="AUTO"
        timezone=""
        timezoneMode="AUTO"
      />
    )

    fireEvent.change(screen.getByText('Formato da data').closest('label')?.querySelector('select'), { target: { value: 'CUSTOM' } })
    const input = screen.getByRole('combobox', { name: 'Padrão' })
    fireEvent.change(input, { target: { value: '' } })
    fireEvent.focus(input)

    expect(screen.getByRole('option', { name: /AAAA- Ano com quatro dígitos\(2026\)/i })).toBeInTheDocument()
    expect(screen.getByText('Tokens suportados: AAAA, AA, MMMM, MMM, MM, DD, dddd, ddd, HH, H, hh, h, mm, M, ss, S, A. Pode adicionar separadores como espaços, barras, hífenes, vírgulas ou dois pontos.')).toBeInTheDocument()
  })

  it('prefills the custom date-format dialog from the automatic locale format', () => {
    renderDialog({ dateFormat: 'AUTO', language: 'en-US' })

    fireEvent.change(dateFormatSelect(), { target: { value: 'CUSTOM' } })

    expect(screen.getByPlaceholderText('YYYY-MM-DD HH:mm:ss')).toHaveValue(localizeCustomDateFormatPattern(describeAutomaticDateFormat('en-US'), 'en-US'))
  })

  it('shows the effective automatic date pattern for the selected language', () => {
    renderDialog({ language: 'pt-PT', t: tPt })

    const localizedDateFormatSelect = screen.getByText('Formato da data').closest('label')?.querySelector('select')
    expect(localizedDateFormatSelect).toHaveDisplayValue(`Automático (${localizeCustomDateFormatPattern(describeAutomaticDateFormat('pt-PT'), 'pt-PT')})`)
  })

  it('shows a saved custom date format summary from the single stored preference', () => {
    renderDialog({ dateFormat: 'DD/MM/YYYY HH:mm:ss' })

    expect(screen.getByText('DD/MM/YYYY HH:mm:ss')).toBeInTheDocument()
    expect(screen.getByText('Example: 06/04/2026 14:24:56')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit custom format' })).toBeInTheDocument()
  })

  it('starts layout editing from the action button', () => {
    const onStartLayoutEditing = vi.fn()

    renderDialog({
      onStartLayoutEditing,
      persistLayout: true,
      quickSetupVisible: true
    })

    fireEvent.click(screen.getByRole('button', { name: 'Edit Layout' }))
    expect(onStartLayoutEditing).toHaveBeenCalledTimes(1)
  })

  it('disables the quick setup toggle when incomplete setup requires the guide to stay visible', () => {
    const onQuickSetupVisibilityChange = vi.fn()

    renderDialog({
      canHideQuickSetup: false,
      layoutEditEnabled: true,
      onQuickSetupVisibilityChange,
      persistLayout: true,
      quickSetupVisible: true
    })

    const quickSetupCheckbox = checkboxForText('Show Quick Setup Guide')
    expect(quickSetupCheckbox).toBeDisabled()
    expect(onQuickSetupVisibilityChange).not.toHaveBeenCalled()
  })

  it('shows a save layout action when layout editing is active', () => {
    const onExitLayoutEditing = vi.fn()

    renderDialog({
      layoutEditEnabled: true,
      onExitLayoutEditing,
      persistLayout: true,
      quickSetupVisible: true
    })

    fireEvent.click(screen.getByRole('button', { name: 'Save Layout' }))
    expect(onExitLayoutEditing).toHaveBeenCalledTimes(1)
  })
})
