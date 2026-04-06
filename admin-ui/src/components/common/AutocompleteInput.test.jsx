import { fireEvent, render, screen } from '@testing-library/react'
import { useRef, useState } from 'react'
import AutocompleteInput from './AutocompleteInput'

function Harness(props) {
  const [value, setValue] = useState(props.initialValue || '')
  const inputRef = useRef(null)
  return (
    <AutocompleteInput
      {...props}
      inputRef={inputRef}
      onChange={(event) => setValue(event.target.value)}
      onSuggestionSelect={(suggestion, input) => {
        props.onSuggestionSelect?.(suggestion, input, setValue)
      }}
      value={value}
    />
  )
}

describe('AutocompleteInput', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows suggestions on focus and selects with keyboard', () => {
    render(
      <Harness
        inputAriaLabel="Pattern"
        onSuggestionSelect={(suggestion, input, setValue) => {
          setValue(suggestion.value)
          requestAnimationFrame(() => input?.setSelectionRange(suggestion.value.length, suggestion.value.length))
        }}
        placeholder="Pattern"
        renderSuggestion={(suggestion) => suggestion.label}
        suggestions={[
          { key: 'yyyy', label: 'YYYY - Four-digit year (2026)', value: 'YYYY' },
          { key: 'yy', label: 'YY - Two-digit year (26)', value: 'YY' }
        ]}
        suggestionToKey={(suggestion) => suggestion.key}
      />
    )

    const input = screen.getByRole('combobox', { name: 'Pattern' })
    fireEvent.focus(input)
    expect(screen.getByRole('listbox')).toBeInTheDocument()

    fireEvent.keyDown(input, { key: 'ArrowDown' })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(input).toHaveValue('YY')
  })

  it('renders the floating suggestion menu outside the flow with fixed positioning', () => {
    render(
      <Harness
        inputAriaLabel="Pattern"
        placeholder="Pattern"
        renderSuggestion={(suggestion) => suggestion.label}
        suggestions={[
          { key: 'am', label: 'A - AM or PM marker (PM)', value: 'A' }
        ]}
        suggestionToKey={(suggestion) => suggestion.key}
      />
    )

    fireEvent.focus(screen.getAllByRole('combobox', { name: 'Pattern' })[0])

    expect(screen.getByRole('listbox')).toHaveClass('autocomplete-input-suggestions')
    expect(screen.getByRole('listbox').getAttribute('style')).toContain('left:')
    expect(screen.getByRole('option', { name: 'A - AM or PM marker (PM)' })).toBeInTheDocument()
  })

  it('closes suggestions when the anchor scrolls outside the viewport', () => {
    render(
      <Harness
        inputAriaLabel="Pattern"
        placeholder="Pattern"
        renderSuggestion={(suggestion) => suggestion.label}
        suggestions={[
          { key: 'yyyy', label: 'YYYY - Four-digit year (2026)', value: 'YYYY' }
        ]}
        suggestionToKey={(suggestion) => suggestion.key}
      />
    )

    const input = screen.getByRole('combobox', { name: 'Pattern' })
    input.getBoundingClientRect = vi.fn(() => ({
      bottom: 260,
      height: 40,
      left: 24,
      right: 264,
      top: 220,
      width: 240
    }))
    fireEvent.focus(input)
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

  it('keeps the input editable on mobile and renders attached suggestions below the input without a native picker trigger', () => {
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn((query) => ({
        addEventListener: vi.fn(),
        addListener: vi.fn(),
        matches: query.includes('pointer') || query.includes('hover'),
        media: query,
        removeEventListener: vi.fn(),
        removeListener: vi.fn()
      }))
    })
    Object.defineProperty(window.navigator, 'maxTouchPoints', {
      configurable: true,
      value: 2
    })

    render(
      <Harness
        inputAriaLabel="Pattern"
        placeholder="Pattern"
        renderSuggestion={(suggestion) => suggestion.label}
        suggestions={[
          { key: 'yyyy', label: 'YYYY - Four-digit year (2026)', value: 'YYYY' }
        ]}
        suggestionToLabel={(suggestion) => suggestion.label}
        suggestionToKey={(suggestion) => suggestion.key}
      />
    )

    const input = screen.getAllByRole('combobox', { name: 'Pattern' })[0]
    fireEvent.focus(input)
    fireEvent.change(input, { target: { value: 'Y' } })

    expect(screen.getByRole('listbox')).toBeInTheDocument()
    expect(screen.getByRole('listbox')).toHaveClass('is-attached')
    expect(document.querySelector('.autocomplete-input-native-select')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Pattern suggestions' })).not.toBeInTheDocument()
    expect(input).toHaveValue('Y')
  })
})
