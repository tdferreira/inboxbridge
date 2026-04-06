import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { resolveFloatingMenuPosition } from '../../lib/floatingMenu'
import './AutocompleteInput.css'

export default function AutocompleteInput({
  autoFocus = false,
  disabled = false,
  inputAriaLabel,
  inputId,
  inputRef = null,
  onBlur,
  onChange,
  onClick,
  onFocus,
  onKeyUp,
  onSelect,
  onSuggestionSelect,
  placeholder = '',
  renderSuggestion,
  suggestions = [],
  suggestionToLabel,
  suggestionToKey,
  value = ''
}) {
  const generatedId = useId()
  const resolvedInputId = inputId || `autocomplete-input-${generatedId}`
  const listboxId = `${resolvedInputId}-listbox`
  const rootRef = useRef(null)
  const internalInputRef = useRef(null)
  const suggestionsRef = useRef(null)
  const [suggestionsOpen, setSuggestionsOpen] = useState(false)
  const [activeSuggestionIndex, setActiveSuggestionIndex] = useState(0)
  const [suggestionsStyle, setSuggestionsStyle] = useState(null)
  const [preferAttachedSuggestions, setPreferAttachedSuggestions] = useState(false)
  const resolvedInputRef = inputRef || internalInputRef

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return undefined
    }

    const pointerQuery = window.matchMedia('(pointer: coarse)')
    const hoverQuery = window.matchMedia('(hover: none)')
    const sync = () => {
      const touchCapable = typeof navigator !== 'undefined' && Number(navigator.maxTouchPoints) > 0
      setPreferAttachedSuggestions(touchCapable && (pointerQuery.matches || hoverQuery.matches))
    }
    sync()

    pointerQuery.addEventListener?.('change', sync)
    hoverQuery.addEventListener?.('change', sync)
    return () => {
      pointerQuery.removeEventListener?.('change', sync)
      hoverQuery.removeEventListener?.('change', sync)
    }
  }, [])

  useEffect(() => {
    if (!suggestions.length) {
      setActiveSuggestionIndex(0)
      return
    }
    setActiveSuggestionIndex((current) => Math.min(Math.max(current, 0), suggestions.length - 1))
  }, [suggestions.length])

  useEffect(() => {
    if (!suggestionsOpen) {
      return undefined
    }

    function closeOnPointerDown(event) {
      if (rootRef.current?.contains(event.target) || suggestionsRef.current?.contains(event.target)) {
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
    if (!suggestionsOpen || !suggestions.length || !suggestionsRef.current || !resolvedInputRef.current) {
      setSuggestionsStyle(null)
      return undefined
    }

    if (preferAttachedSuggestions) {
      setSuggestionsStyle(null)
      return undefined
    }

    function updatePosition() {
      const anchorRect = resolvedInputRef.current.getBoundingClientRect()
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

      const menuRect = suggestionsRef.current.getBoundingClientRect()
      const next = resolveFloatingMenuPosition(
        anchorRect,
        menuRect,
        window.innerWidth,
        window.innerHeight
      )
      const width = Math.min(
        Math.max(anchorRect.width, 320),
        Math.max(220, window.innerWidth - margin * 2)
      )
      const left = Math.min(
        Math.max(anchorRect.left, margin),
        Math.max(margin, window.innerWidth - width - margin)
      )
      setSuggestionsStyle({
        left: `${left}px`,
        maxHeight: `${Math.min(
          320,
          Math.max(
            120,
            next.placement === 'top'
              ? anchorRect.top - margin - 8
              : window.innerHeight - anchorRect.bottom - margin - 8
          )
        )}px`,
        top: `${next.top}px`,
        width: `${width}px`
      })
    }

    updatePosition()
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition, true)
    return () => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    }
  }, [preferAttachedSuggestions, resolvedInputRef, suggestions.length, suggestionsOpen, value])

  function commitSuggestion(index) {
    if (index < 0 || index >= suggestions.length) {
      return
    }
    onSuggestionSelect?.(suggestions[index], resolvedInputRef.current)
    setSuggestionsOpen(true)
  }

  return (
    <div className={`autocomplete-input ${preferAttachedSuggestions ? 'is-attached-mobile' : ''}`.trim()} ref={rootRef}>
      <input
        ref={resolvedInputRef}
        aria-activedescendant={suggestionsOpen && suggestions.length > 0 ? `${resolvedInputId}-option-${activeSuggestionIndex}` : undefined}
        aria-autocomplete="list"
        aria-controls={suggestionsOpen && suggestions.length > 0 ? listboxId : undefined}
        aria-expanded={suggestionsOpen && suggestions.length > 0}
        aria-label={inputAriaLabel}
        autoComplete="off"
        autoFocus={autoFocus}
        className="autocomplete-input-field"
        disabled={disabled}
        id={resolvedInputId}
        onBlur={onBlur}
        onChange={(event) => {
          setSuggestionsOpen(suggestions.length > 0)
          onChange?.(event)
        }}
        onClick={(event) => {
          if (suggestions.length > 0) {
            setSuggestionsOpen(true)
          }
          onClick?.(event)
        }}
        onFocus={(event) => {
          if (suggestions.length > 0) {
            setSuggestionsOpen(true)
          }
          onFocus?.(event)
        }}
        onKeyDown={(event) => {
          if (!suggestionsOpen || !suggestions.length) {
            return
          }
          if (event.key === 'ArrowDown') {
            event.preventDefault()
            setActiveSuggestionIndex((current) => Math.min(current + 1, suggestions.length - 1))
            return
          }
          if (event.key === 'ArrowUp') {
            event.preventDefault()
            setActiveSuggestionIndex((current) => Math.max(current - 1, 0))
            return
          }
          if (event.key === 'Enter' || event.key === 'Tab') {
            event.preventDefault()
            commitSuggestion(activeSuggestionIndex)
            return
          }
          if (event.key === 'Escape') {
            event.preventDefault()
            setSuggestionsOpen(false)
          }
        }}
        onKeyUp={onKeyUp}
        onSelect={onSelect}
        placeholder={placeholder}
        role="combobox"
        type="text"
        value={value}
      />
      {suggestionsOpen && suggestions.length > 0 ? (
        <div
          className={`autocomplete-input-suggestions ${preferAttachedSuggestions ? 'is-attached' : ''}`.trim()}
          id={listboxId}
          ref={suggestionsRef}
          role="listbox"
          style={suggestionsStyle || undefined}
        >
          {suggestions.map((suggestion, index) => (
            <button
              className={`autocomplete-input-suggestion ${index === activeSuggestionIndex ? 'is-active' : ''}`.trim()}
              id={`${resolvedInputId}-option-${index}`}
              key={suggestionToKey ? suggestionToKey(suggestion) : `${index}`}
              onMouseDown={(event) => {
                event.preventDefault()
                commitSuggestion(index)
              }}
              role="option"
              type="button"
            >
              {renderSuggestion ? renderSuggestion(suggestion) : suggestionToLabel?.(suggestion)}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  )
}
