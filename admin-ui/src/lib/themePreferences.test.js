import {
  applyDocumentTheme,
  normalizeThemeMode,
  resolveEffectiveTheme,
  resolveThemeVariant
} from './themePreferences'

describe('themePreferences', () => {
  it('maps legacy theme names onto the new palette variants', () => {
    expect(normalizeThemeMode('LIGHT')).toBe('LIGHT_GREEN')
    expect(normalizeThemeMode('DARK')).toBe('DARK_BLUE')
  })

  it('resolves explicit palette variants consistently', () => {
    expect(resolveEffectiveTheme('LIGHT_BLUE')).toBe('light')
    expect(resolveEffectiveTheme('DARK_GREEN')).toBe('dark')
    expect(resolveThemeVariant('LIGHT_BLUE')).toBe('blue')
    expect(resolveThemeVariant('DARK_GREEN')).toBe('green')
  })

  it('applies both theme tone and variant to the document element', () => {
    const target = { documentElement: { dataset: {} } }

    applyDocumentTheme('DARK_GREEN', target)

    expect(target.documentElement.dataset.themeMode).toBe('dark_green')
    expect(target.documentElement.dataset.theme).toBe('dark')
    expect(target.documentElement.dataset.themeVariant).toBe('green')
  })
})
