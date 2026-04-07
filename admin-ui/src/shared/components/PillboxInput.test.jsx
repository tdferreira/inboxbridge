import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import PillboxInput from './PillboxInput'

function Harness(props) {
  const [values, setValues] = useState(props.initialValues || [])
  return (
    <PillboxInput
      {...props}
      onChange={setValues}
      values={values}
    />
  )
}

describe('PillboxInput', () => {
  const originalMatchMedia = window.matchMedia
  const originalMaxTouchPoints = navigator.maxTouchPoints

  afterEach(() => {
    window.matchMedia = originalMatchMedia
    Object.defineProperty(navigator, 'maxTouchPoints', {
      configurable: true,
      value: originalMaxTouchPoints
    })
  })

  it('allows free-text entries before options are locked', () => {
    render(
      <Harness
        allowCustomValues
        inputAriaLabel="Folders"
        placeholder="Type a folder"
      />
    )

    const input = screen.getByRole('combobox', { name: 'Folders' })
    fireEvent.change(input, { target: { value: 'Projects' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(screen.getByText('Projects')).toBeInTheDocument()
  })

  it('emits input activity when the user starts typing', () => {
    const onInputActivity = vi.fn()

    render(
      <Harness
        allowCustomValues
        inputAriaLabel="Folders"
        onInputActivity={onInputActivity}
        placeholder="Type a folder"
      />
    )

    const input = screen.getByRole('combobox', { name: 'Folders' })
    fireEvent.change(input, { target: { value: 'Pro' } })

    expect(onInputActivity).toHaveBeenCalledWith('Pro')
  })

  it('emits focus activity when the user focuses the input', () => {
    const onInputFocus = vi.fn()

    render(
      <Harness
        allowCustomValues
        inputAriaLabel="Folders"
        onInputFocus={onInputFocus}
        placeholder="Type a folder"
      />
    )

    fireEvent.focus(screen.getByRole('combobox', { name: 'Folders' }))

    expect(onInputFocus).toHaveBeenCalledTimes(1)
  })

  it('supports arrow-key navigation and enter selection for suggestions', () => {
    render(
      <Harness
        allowCustomValues={false}
        inputAriaLabel="Folders"
        options={['INBOX', 'Projects/2026', 'Archive']}
        placeholder="Select folders"
      />
    )

    const input = screen.getByRole('combobox', { name: 'Folders' })
    fireEvent.focus(input)
    fireEvent.change(input, { target: { value: 'Pro' } })

    expect(screen.getByRole('listbox')).toBeInTheDocument()

    fireEvent.keyDown(input, { key: 'ArrowDown' })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(screen.getByText('Projects/2026')).toBeInTheDocument()
  })

  it('refuses arbitrary free-text entries once options are locked', () => {
    render(
      <Harness
        allowCustomValues={false}
        inputAriaLabel="Folders"
        options={['INBOX', 'Archive']}
      />
    )

    const input = screen.getByRole('combobox', { name: 'Folders' })
    fireEvent.change(input, { target: { value: 'MissingFolder' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(screen.queryByText('MissingFolder')).not.toBeInTheDocument()
  })

  it('renders validation pills with caller-provided tones and labels', () => {
    render(
      <PillboxInput
        allowCustomValues={false}
        inputAriaLabel="Folders"
        onChange={() => {}}
        options={['INBOX']}
        validationActive
        valueTone={(value) => (value === 'INBOX' ? 'success' : 'error')}
        valueValidationLabel={(value, tone) => tone === 'success' ? `${value} exists` : `${value} missing`}
        values={['INBOX', 'MissingFolder']}
      />
    )

    expect(screen.getByText('INBOX exists')).toBeInTheDocument()
    expect(screen.getByText('MissingFolder missing')).toBeInTheDocument()
  })

  it('marks the field as invalid when the caller requests error styling', () => {
    render(
      <PillboxInput
        allowCustomValues
        inputAriaLabel="Folders"
        invalid
        onChange={() => {}}
        values={['INBOX']}
      />
    )

    expect(screen.getByRole('combobox', { name: 'Folders' })).toHaveAttribute('aria-invalid', 'true')
  })

  it('uses a native multi-select picker on coarse-pointer devices when options are locked', () => {
    window.matchMedia = vi.fn().mockImplementation((query) => ({
      addEventListener: vi.fn(),
      matches: query === '(pointer: coarse)',
      removeEventListener: vi.fn()
    }))
    Object.defineProperty(navigator, 'maxTouchPoints', {
      configurable: true,
      value: 5
    })

    render(
      <Harness
        allowCustomValues={false}
        inputAriaLabel="Folders"
        options={['INBOX', 'Projects/2026', 'Archive']}
        placeholder="Select folders"
      />
    )

    expect(screen.queryByRole('combobox', { name: 'Folders' })).not.toBeInTheDocument()

    const nativeSelect = screen.getByRole('listbox', { name: 'Folders' })
    const optionElements = nativeSelect.querySelectorAll('option')
    optionElements[0].selected = true
    optionElements[2].selected = true
    fireEvent.change(nativeSelect)

    expect(screen.getByRole('button', { name: 'Remove INBOX' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Remove Archive' })).toBeInTheDocument()
  })

  it('keeps the floating combobox on desktop mobile emulation without touch capability', () => {
    window.matchMedia = vi.fn().mockImplementation((query) => ({
      addEventListener: vi.fn(),
      matches: query === '(pointer: coarse)',
      removeEventListener: vi.fn()
    }))
    Object.defineProperty(navigator, 'maxTouchPoints', {
      configurable: true,
      value: 0
    })

    render(
      <Harness
        allowCustomValues={false}
        inputAriaLabel="Folders"
        options={['INBOX', 'Projects/2026', 'Archive']}
        placeholder="Select folders"
      />
    )

    const input = screen.getByRole('combobox', { name: 'Folders' })
    fireEvent.focus(input)
    fireEvent.change(input, { target: { value: 'Pro' } })

    expect(screen.getByRole('listbox')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove INBOX' })).not.toBeInTheDocument()
  })

  it('opens the floating suggestions on shell click without auto-selecting an option on desktop emulation', () => {
    window.matchMedia = vi.fn().mockImplementation((query) => ({
      addEventListener: vi.fn(),
      matches: query === '(pointer: coarse)',
      removeEventListener: vi.fn()
    }))
    Object.defineProperty(navigator, 'maxTouchPoints', {
      configurable: true,
      value: 0
    })

    render(
      <Harness
        allowCustomValues={false}
        inputAriaLabel="Folders"
        options={['INBOX', 'Projects/2026', 'Archive']}
        placeholder="Select folders"
      />
    )

    fireEvent.click(screen.getByRole('presentation'))

    expect(screen.getByRole('combobox', { name: 'Folders' })).toHaveFocus()
    expect(screen.getByRole('listbox')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove INBOX' })).not.toBeInTheDocument()
  })

  it('removes a selected value without accidentally selecting a new option', () => {
    render(
      <Harness
        allowCustomValues={false}
        initialValues={['INBOX']}
        inputAriaLabel="Folders"
        options={['INBOX', 'Projects/2026', 'Archive']}
      />
    )

    fireEvent.click(screen.getByRole('button', { name: 'Remove INBOX' }))

    expect(screen.queryByRole('button', { name: 'Remove INBOX' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove Projects/2026' })).not.toBeInTheDocument()
  })

  it('closes the floating suggestions when the anchor leaves the viewport', () => {
    window.matchMedia = vi.fn().mockImplementation(() => ({
      addEventListener: vi.fn(),
      matches: false,
      removeEventListener: vi.fn()
    }))

    render(
      <Harness
        allowCustomValues={false}
        inputAriaLabel="Folders"
        options={['INBOX', 'Projects/2026', 'Archive']}
        placeholder="Select folders"
      />
    )

    const input = screen.getByRole('combobox', { name: 'Folders' })
    input.getBoundingClientRect = vi.fn(() => ({
      bottom: 260,
      height: 40,
      left: 24,
      right: 264,
      top: 220,
      width: 240
    }))

    fireEvent.focus(input)
    fireEvent.change(input, { target: { value: 'Pro' } })

    expect(screen.getByRole('listbox')).toBeInTheDocument()

    input.getBoundingClientRect = vi.fn(() => ({
      bottom: -40,
      height: 40,
      left: 24,
      right: 264,
      top: -80,
      width: 240
    }))

    fireEvent.scroll(window)

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  })
})
