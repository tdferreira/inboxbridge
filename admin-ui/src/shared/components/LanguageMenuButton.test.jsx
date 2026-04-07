import { fireEvent, render, screen } from '@testing-library/react'
import LanguageMenuButton from './LanguageMenuButton'

vi.mock('@/lib/floatingMenu', () => ({
  resolveFloatingMenuPosition: () => ({
    left: 12,
    top: 20,
    placement: 'bottom'
  })
}))

describe('LanguageMenuButton', () => {
  beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      width: 120,
      height: 32,
      top: 24,
      left: 24,
      right: 144,
      bottom: 56
    })
  })

  it('renders the current language and changes it through the menu', () => {
    const onChange = vi.fn()

    render(
      <LanguageMenuButton
        ariaLabel="Language"
        currentLanguage="en"
        onChange={onChange}
        options={[
          { value: 'en', label: 'English' },
          { value: 'pt-PT', label: 'Português (Portugal)' }
        ]}
      />
    )

    expect(screen.getByRole('button', { name: 'Language' })).toHaveTextContent('English')

    fireEvent.click(screen.getByRole('button', { name: 'Language' }))
    fireEvent.click(screen.getByRole('menuitemradio', { name: /Português \(Portugal\)/ }))

    expect(onChange).toHaveBeenCalledWith('pt-PT')
    expect(screen.queryByRole('menuitemradio', { name: /English/ })).not.toBeInTheDocument()
  })

  it('stays inert while disabled', () => {
    const onChange = vi.fn()

    render(
      <LanguageMenuButton
        ariaLabel="Language"
        currentLanguage="en"
        disabled
        onChange={onChange}
        options={[
          { value: 'en', label: 'English' },
          { value: 'pt-PT', label: 'Português (Portugal)' }
        ]}
      />
    )

    expect(screen.getByRole('button', { name: 'Language' })).toBeDisabled()
    expect(onChange).not.toHaveBeenCalled()
  })
})
