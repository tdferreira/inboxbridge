import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { resolveFloatingMenuPosition } from '../../lib/floatingMenu'
import './LanguageMenuButton.css'

const LANGUAGE_FLAGS = {
  en: '🇬🇧',
  fr: '🇫🇷',
  de: '🇩🇪',
  'pt-PT': '🇵🇹',
  'pt-BR': '🇧🇷',
  es: '🇪🇸'
}

function languageFlag(locale) {
  return LANGUAGE_FLAGS[locale] || '🌐'
}

function LanguageMenuButton({
  ariaLabel,
  className = '',
  currentLanguage,
  disabled = false,
  menuAlign = 'end',
  onChange,
  options = []
}) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [menuStyle, setMenuStyle] = useState(null)
  const [menuPlacement, setMenuPlacement] = useState('bottom')
  const containerRef = useRef(null)
  const menuButtonRef = useRef(null)
  const menuPanelRef = useRef(null)

  const optionsWithFlags = useMemo(
    () => options.map((option) => ({
      ...option,
      flag: languageFlag(option.value)
    })),
    [options]
  )
  const selectedLanguage = optionsWithFlags.find((option) => option.value === currentLanguage) || optionsWithFlags[0]

  useEffect(() => {
    if (!menuOpen) {
      return undefined
    }

    function closeOnPointerDown(event) {
      if (!containerRef.current || containerRef.current.contains(event.target)) {
        return
      }
      setMenuOpen(false)
    }

    function closeOnEscape(event) {
      if (event.key === 'Escape') {
        setMenuOpen(false)
      }
    }

    document.addEventListener('mousedown', closeOnPointerDown)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('mousedown', closeOnPointerDown)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [menuOpen])

  useLayoutEffect(() => {
    if (!menuOpen || !menuPanelRef.current || !menuButtonRef.current) {
      return undefined
    }

    function updatePosition() {
      const anchorRect = menuButtonRef.current.getBoundingClientRect()
      const next = resolveFloatingMenuPosition(
        anchorRect,
        menuPanelRef.current.getBoundingClientRect(),
        window.innerWidth,
        window.innerHeight
      )
      const menuRect = menuPanelRef.current.getBoundingClientRect()
      const unclampedLeft = menuAlign === 'start'
        ? anchorRect.left
        : anchorRect.right - menuRect.width
      const left = Math.min(
        Math.max(unclampedLeft, 12),
        Math.max(12, window.innerWidth - menuRect.width - 12)
      )
      setMenuPlacement(next.placement)
      setMenuStyle({
        left: `${left}px`,
        top: `${next.top}px`
      })
    }

    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [menuOpen])

  return (
    <div className={`language-menu-picker ${className}`.trim()} ref={containerRef}>
      <button
        aria-expanded={menuOpen ? 'true' : 'false'}
        aria-haspopup="menu"
        aria-label={ariaLabel}
        className="language-menu-button"
        disabled={disabled}
        onClick={() => setMenuOpen((current) => !current)}
        ref={menuButtonRef}
        title={ariaLabel}
        type="button"
      >
        <span aria-hidden="true" className="language-menu-button-flag">{selectedLanguage?.flag}</span>
        <span className="language-menu-button-text">{selectedLanguage?.label}</span>
        <span aria-hidden="true" className="language-menu-button-caret">▾</span>
      </button>
      {menuOpen ? (
        <div
          className="fetcher-menu language-menu-panel"
          data-placement={menuPlacement}
          ref={menuPanelRef}
          role="menu"
          style={menuStyle}
        >
          {optionsWithFlags.map((option) => (
            <button
              aria-pressed={option.value === currentLanguage}
              className={option.value === currentLanguage ? 'language-menu-option language-menu-option-selected' : 'language-menu-option'}
              key={option.value}
              onClick={() => {
                if (!disabled) {
                  onChange(option.value)
                  setMenuOpen(false)
                }
              }}
              role="menuitemradio"
              type="button"
            >
              <span aria-hidden="true" className="language-menu-option-flag">{option.flag}</span>
              <span className="language-menu-option-label">{option.label}</span>
            </button>
          ))}
        </div>
      ) : null}
    </div>
  )
}

export default LanguageMenuButton
