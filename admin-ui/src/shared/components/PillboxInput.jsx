import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { resolveFloatingMenuPosition } from '@/lib/floatingMenu'
import './PillboxInput.css'

function normalizeValues(values) {
  return Array.isArray(values) ? values : []
}

function toneClass(tone) {
  switch (tone) {
    case 'success':
      return 'tone-success'
    case 'error':
      return 'tone-error'
    default:
      return 'tone-neutral'
  }
}

export default function PillboxInput({
  allowCustomValues = true,
  disabled = false,
  helperText = '',
  invalid = false,
  inputAriaLabel,
  inputId,
  loading = false,
  onChange,
  onInputActivity,
  onInputFocus,
  options = [],
  placeholder = '',
  removeLabel,
  values = [],
  validationActive = false,
  valueTone,
  valueValidationLabel
}) {
  const resolvedValues = useMemo(() => normalizeValues(values), [values])
  const resolvedOptions = useMemo(
    () => normalizeValues(options)
      .map((option) => String(option).trim())
      .filter(Boolean),
    [options]
  )
  const generatedInputId = useId()
  const resolvedInputId = inputId || `pillbox-input-${generatedInputId}`
  const listboxId = `${resolvedInputId}-listbox`
  const rootRef = useRef(null)
  const inputRef = useRef(null)
  const nativeSelectRef = useRef(null)
  const suggestionsRef = useRef(null)
  const [inputValue, setInputValue] = useState('')
  const [suggestionsOpen, setSuggestionsOpen] = useState(false)
  const [activeSuggestionIndex, setActiveSuggestionIndex] = useState(-1)
  const [suggestionsStyle, setSuggestionsStyle] = useState(null)
  const [suggestionsPlacement, setSuggestionsPlacement] = useState('bottom')
  const [preferNativePicker, setPreferNativePicker] = useState(false)

  const normalizedSelectedValues = useMemo(
    () => resolvedValues.map((value) => ({ label: value, lower: value.toLowerCase() })),
    [resolvedValues]
  )
  const availableSuggestions = useMemo(() => {
    const selectedLowers = new Set(normalizedSelectedValues.map((value) => value.lower))
    const query = inputValue.trim().toLowerCase()
    return resolvedOptions
      .filter((option) => !selectedLowers.has(option.toLowerCase()))
      .filter((option) => !query || option.toLowerCase().includes(query))
  }, [inputValue, normalizedSelectedValues, resolvedOptions])

  useEffect(() => {
    if (!availableSuggestions.length) {
      setActiveSuggestionIndex(-1)
      return
    }
    setActiveSuggestionIndex((current) => {
      if (current < 0) {
        return 0
      }
      return Math.min(current, availableSuggestions.length - 1)
    })
  }, [availableSuggestions])

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return undefined
    }
    const pointerQuery = window.matchMedia('(pointer: coarse)')
    const hoverQuery = window.matchMedia('(hover: none)')
    const updatePreference = () => {
      const touchCapable = typeof navigator !== 'undefined' && Number(navigator.maxTouchPoints) > 0
      setPreferNativePicker(
        touchCapable && (pointerQuery.matches || hoverQuery.matches)
      )
    }
    updatePreference()
    pointerQuery.addEventListener?.('change', updatePreference)
    hoverQuery.addEventListener?.('change', updatePreference)
    return () => {
      pointerQuery.removeEventListener?.('change', updatePreference)
      hoverQuery.removeEventListener?.('change', updatePreference)
    }
  }, [])

  useEffect(() => {
    if (!suggestionsOpen) {
      return undefined
    }

    function closeOnPointerDown(event) {
      if (!rootRef.current || rootRef.current.contains(event.target)) {
        return
      }
      setSuggestionsOpen(false)
    }

    function closeOnEscape(event) {
      if (event.key === 'Escape') {
        setSuggestionsOpen(false)
      }
    }

    document.addEventListener('mousedown', closeOnPointerDown)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('mousedown', closeOnPointerDown)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [suggestionsOpen])

  useLayoutEffect(() => {
    if (!suggestionsOpen || preferNativePicker || !suggestionsRef.current || !inputRef.current) {
      return undefined
    }

    function updatePosition() {
      const anchorRect = inputRef.current.getBoundingClientRect()
      const margin = 12
      const hasMeasuredAnchor = anchorRect.width > 0 || anchorRect.height > 0

      if (hasMeasuredAnchor && (
        anchorRect.bottom < margin ||
        anchorRect.top > window.innerHeight - margin ||
        anchorRect.right < margin ||
        anchorRect.left > window.innerWidth - margin
      )) {
        setSuggestionsOpen(false)
        return
      }

      const next = resolveFloatingMenuPosition(
        anchorRect,
        suggestionsRef.current.getBoundingClientRect(),
        window.innerWidth,
        window.innerHeight
      )

      setSuggestionsPlacement(next.placement)
      setSuggestionsStyle({
        left: `${Math.min(
          Math.max(anchorRect.left, margin),
          Math.max(margin, window.innerWidth - Math.min(480, suggestionsRef.current.getBoundingClientRect().width || 320) - margin)
        )}px`,
        maxHeight: `${Math.min(320, Math.max(120, next.placement === 'top' ? anchorRect.top - margin - 8 : window.innerHeight - anchorRect.bottom - margin - 8))}px`,
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
  }, [preferNativePicker, suggestionsOpen, availableSuggestions.length])

  const useNativeMultiSelect = preferNativePicker && !allowCustomValues && resolvedOptions.length > 0
  const showTextInput = !useNativeMultiSelect

  function focusInput() {
    if (useNativeMultiSelect) {
      nativeSelectRef.current?.focus()
      return
    }
    inputRef.current?.focus()
  }

  function commitValue(nextValue) {
    const trimmedValue = nextValue.trim()
    if (!trimmedValue) {
      return false
    }
    if (normalizedSelectedValues.some((value) => value.lower === trimmedValue.toLowerCase())) {
      setInputValue('')
      return true
    }
    const matchingOption = resolvedOptions.find((option) => option.toLowerCase() === trimmedValue.toLowerCase())
    // Callers can switch the same component from free-text mode into
    // "server-validated options only" mode once remote options have loaded.
    if (!allowCustomValues && !matchingOption) {
      return false
    }
    onChange?.([...resolvedValues, matchingOption || trimmedValue])
    setInputValue('')
    setSuggestionsOpen(true)
    return true
  }

  function removeValue(targetValue) {
    onChange?.(resolvedValues.filter((value) => value.toLowerCase() !== targetValue.toLowerCase()))
    focusInput()
  }

  function commitHighlightedSuggestion() {
    if (activeSuggestionIndex < 0 || activeSuggestionIndex >= availableSuggestions.length) {
      return false
    }
    return commitValue(availableSuggestions[activeSuggestionIndex])
  }

  return (
    <div className={`pillbox-input ${validationActive ? 'is-validation-active' : ''} ${invalid ? 'is-invalid' : ''}`.trim()} ref={rootRef}>
      <div
        className={`pillbox-input-shell ${useNativeMultiSelect ? 'pillbox-input-shell-native' : ''}`.trim()}
        onClick={focusInput}
        role="presentation"
      >
        {resolvedValues.map((value) => {
          const tone = valueTone?.(value) || 'neutral'
          return (
            <span className={`status-pill pillbox-input-pill ${toneClass(tone)}`} key={value}>
              <span>{value}</span>
              <button
                aria-label={removeLabel?.(value) || `Remove ${value}`}
                className="pillbox-input-pill-remove"
                disabled={disabled}
                onClick={() => removeValue(value)}
                type="button"
              >
                ×
              </button>
            </span>
          )
        })}
        {showTextInput ? (
          <input
            ref={inputRef}
            aria-activedescendant={activeSuggestionIndex >= 0 ? `${resolvedInputId}-option-${activeSuggestionIndex}` : undefined}
            aria-autocomplete="list"
            aria-controls={availableSuggestions.length > 0 ? listboxId : undefined}
            aria-expanded={suggestionsOpen && availableSuggestions.length > 0}
            aria-invalid={invalid || undefined}
            aria-label={inputAriaLabel}
            autoComplete="off"
            className="pillbox-input-field"
            disabled={disabled}
            id={resolvedInputId}
            onBlur={() => {
              window.setTimeout(() => setSuggestionsOpen(false), 120)
            }}
            onChange={(event) => {
              onInputActivity?.(event.target.value)
              setInputValue(event.target.value)
              setSuggestionsOpen(true)
            }}
            onFocus={() => {
              onInputFocus?.()
              setSuggestionsOpen(true)
            }}
            onKeyDown={(event) => {
              if (event.key === 'ArrowDown') {
                if (!availableSuggestions.length) {
                  return
                }
                event.preventDefault()
                setSuggestionsOpen(true)
                setActiveSuggestionIndex((current) => {
                  if (current < 0) {
                    return 0
                  }
                  return (current + 1) % availableSuggestions.length
                })
                return
              }
              if (event.key === 'ArrowUp') {
                if (!availableSuggestions.length) {
                  return
                }
                event.preventDefault()
                setSuggestionsOpen(true)
                setActiveSuggestionIndex((current) => {
                  if (current < 0) {
                    return availableSuggestions.length - 1
                  }
                  return current === 0 ? availableSuggestions.length - 1 : current - 1
                })
                return
              }
              if (event.key === 'Enter') {
                const hasTypedValue = Boolean(inputValue.trim())
                if (!hasTypedValue && activeSuggestionIndex < 0) {
                  return
                }
                event.preventDefault()
                if (suggestionsOpen && availableSuggestions.length > 0 && activeSuggestionIndex >= 0) {
                  if (commitHighlightedSuggestion()) {
                    return
                  }
                }
                if (allowCustomValues) {
                  commitValue(inputValue)
                }
                return
              }
              if (event.key === ',') {
                if (!allowCustomValues || !inputValue.trim()) {
                  return
                }
                event.preventDefault()
                commitValue(inputValue)
                return
              }
              if (event.key === 'Tab' && allowCustomValues && inputValue.trim()) {
                commitValue(inputValue)
                return
              }
              if (event.key === 'Backspace' && !inputValue && resolvedValues.length > 0) {
                event.preventDefault()
                removeValue(resolvedValues[resolvedValues.length - 1])
              }
            }}
            placeholder={resolvedValues.length === 0 ? placeholder : ''}
            role="combobox"
            type="text"
            value={inputValue}
          />
        ) : (
          <span className={`pillbox-input-placeholder ${resolvedValues.length > 0 ? 'is-hidden' : ''}`.trim()}>
            {placeholder}
          </span>
        )}
        {useNativeMultiSelect ? (
          <select
            aria-label={inputAriaLabel}
            aria-invalid={invalid || undefined}
            className="pillbox-input-native-select"
            disabled={disabled}
            id={resolvedInputId}
            multiple
            ref={nativeSelectRef}
            onChange={(event) => {
              onChange?.(Array.from(event.target.selectedOptions).map((option) => option.value))
            }}
            value={resolvedValues}
          >
            {resolvedOptions.map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </select>
        ) : null}
      </div>
      <div className="pillbox-input-meta">
        {loading ? <span className="pillbox-input-status">{helperText || ''}</span> : null}
        {!loading && helperText ? <span className="pillbox-input-status pillbox-input-status-accent">{helperText}</span> : null}
      </div>
      {suggestionsOpen && !useNativeMultiSelect && availableSuggestions.length > 0 ? (
        <div
          className="pillbox-input-suggestions"
          data-placement={suggestionsPlacement}
          id={listboxId}
          ref={suggestionsRef}
          role="listbox"
          style={suggestionsStyle}
        >
          {availableSuggestions.slice(0, 8).map((option, index) => (
            <button
              aria-selected={index === activeSuggestionIndex}
              className={`pillbox-input-suggestion ${index === activeSuggestionIndex ? 'is-active' : ''}`.trim()}
              id={`${resolvedInputId}-option-${index}`}
              key={option}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => commitValue(option)}
              role="option"
              type="button"
            >
              {option}
            </button>
          ))}
        </div>
      ) : null}
      {validationActive && resolvedValues.length > 0 && valueValidationLabel ? (
        <div className="pillbox-input-validation-summary">
          {resolvedValues.map((value) => {
            const tone = valueTone?.(value) || 'neutral'
            return (
              <span className={`status-pill pillbox-input-validation-pill ${toneClass(tone)}`} key={`${value}-validation`}>
                {valueValidationLabel(value, tone)}
              </span>
            )
          })}
        </div>
      ) : null}
    </div>
  )
}
