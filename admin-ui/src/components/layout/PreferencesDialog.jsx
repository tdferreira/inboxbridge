import { useEffect, useMemo, useRef, useState } from 'react'
import AutocompleteInput from '../common/AutocompleteInput'
import InfoHint from '../common/InfoHint'
import LanguageMenuButton from '../common/LanguageMenuButton'
import ModalDialog from '../common/ModalDialog'
import {
  DATE_FORMAT_AUTO,
  DATE_FORMAT_CUSTOM,
  DATE_FORMAT_DMY_12,
  DATE_FORMAT_DMY_24,
  DATE_FORMAT_MDY_24,
  DATE_FORMAT_MDY_12,
  DATE_FORMAT_YMD_12,
  DATE_FORMAT_YMD_24,
  dateFormatPreferenceToPattern,
  describeAutomaticDateFormat,
  formatDate,
  getLocalizedCustomDateFormatTokens,
  isCustomDateFormatPreference,
  localizeCustomDateFormatPattern,
  normalizeCustomDateFormat,
  validateLocalizedCustomDateFormat
} from '../../lib/formatters'
import './PreferencesDialog.css'

/**
 * Centralized user preference dialog for language and layout behavior so the
 * header stays compact while preferences remain discoverable.
 */
function PreferencesDialog({
  canHideQuickSetup,
  detectedTimeZone,
  layoutEditEnabled,
  language,
  languageOptions,
  onClose,
  onExitLayoutEditing,
  onStartLayoutEditing,
  onDateFormatChange,
  onLanguageChange,
  onPersistLayoutChange,
  onQuickSetupVisibilityChange,
  onResetLayout,
  onTimeZoneChange,
  onTimeZoneModeChange,
  persistLayout,
  quickSetupVisible,
  savingLayout,
  selectableTimeZones,
  t,
  dateFormat,
  timezone,
  timezoneMode
}) {
  const customDateFormatInputRef = useRef(null)
  const resolveEditableDateFormat = () => localizeCustomDateFormatPattern(dateFormatPreferenceToPattern(dateFormat, language), language)
  const [showCustomDateFormatDialog, setShowCustomDateFormatDialog] = useState(false)
  const [customDateFormatDraft, setCustomDateFormatDraft] = useState(resolveEditableDateFormat)
  const [customDateFormatSelection, setCustomDateFormatSelection] = useState({ start: 0, end: 0 })

  useEffect(() => {
    if (showCustomDateFormatDialog) {
      setCustomDateFormatDraft(resolveEditableDateFormat())
    }
  }, [dateFormat, language, showCustomDateFormatDialog])

  const automaticDateFormatLabel = useMemo(
    () => localizeCustomDateFormatPattern(describeAutomaticDateFormat(language), language),
    [language]
  )
  const dateFormatOptions = [
    { value: DATE_FORMAT_AUTO, label: t('preferences.dateFormatAuto', { value: automaticDateFormatLabel }) },
    { value: DATE_FORMAT_DMY_24, label: t('preferences.dateFormatDmy24') },
    { value: DATE_FORMAT_DMY_12, label: t('preferences.dateFormatDmy12') },
    { value: DATE_FORMAT_MDY_24, label: t('preferences.dateFormatMdy24') },
    { value: DATE_FORMAT_MDY_12, label: t('preferences.dateFormatMdy12') },
    { value: DATE_FORMAT_YMD_24, label: t('preferences.dateFormatYmd24') },
    { value: DATE_FORMAT_YMD_12, label: t('preferences.dateFormatYmd12') },
    { value: DATE_FORMAT_CUSTOM, label: t('preferences.dateFormatCustom') }
  ]
  const customDateFormatValidation = validateLocalizedCustomDateFormat(customDateFormatDraft, language)
  const customDateFormatPreview = useMemo(
    () => customDateFormatValidation.valid
      ? formatDate('2026-04-06T13:24:56Z', language, timezoneMode === 'MANUAL' ? (timezone || detectedTimeZone) : detectedTimeZone, customDateFormatValidation.value)
      : '',
    [customDateFormatValidation.valid, customDateFormatValidation.value, detectedTimeZone, language, timezone, timezoneMode]
  )
  const normalizedCustomDateFormat = isCustomDateFormatPreference(dateFormat) ? localizeCustomDateFormatPattern(normalizeCustomDateFormat(dateFormat), language) : ''
  const dateFormatSelectValue = isCustomDateFormatPreference(dateFormat) ? DATE_FORMAT_CUSTOM : dateFormat
  const customDateFormatTokenGuide = useMemo(
    () => getLocalizedCustomDateFormatTokens(language, (locale, key, params) => t(key, params)).map((entry) => ({
      ...entry,
      token: entry.alias
    })),
    [language, t]
  )
  const customDateFormatTokenSummary = useMemo(
    () => t('preferences.customDateFormatTokens', { tokens: customDateFormatTokenGuide.map((entry) => entry.token).join(', ') }),
    [customDateFormatTokenGuide, t]
  )
  function findTokenInsertionRange(value, selectionStart, selectionEnd) {
    const text = String(value || '')
    const startIndex = Number.isInteger(selectionStart) ? selectionStart : text.length
    const endIndex = Number.isInteger(selectionEnd) ? selectionEnd : startIndex
    let start = startIndex
    let end = endIndex
    while (start > 0 && /[A-Za-z]/.test(text[start - 1])) {
      start -= 1
    }
    while (end < text.length && /[A-Za-z]/.test(text[end])) {
      end += 1
    }
    return {
      end,
      query: text.slice(start, end),
      start
    }
  }

  const currentTokenInsertionRange = useMemo(
    () => findTokenInsertionRange(customDateFormatDraft, customDateFormatSelection.start, customDateFormatSelection.end),
    [customDateFormatDraft, customDateFormatSelection.end, customDateFormatSelection.start]
  )
  const visibleTokenSuggestions = useMemo(() => {
    if (!currentTokenInsertionRange.query) {
      return customDateFormatTokenGuide
    }
    const normalizedQuery = currentTokenInsertionRange.query.toLowerCase()
    return customDateFormatTokenGuide.filter((entry) => (
      entry.token.toLowerCase().startsWith(normalizedQuery)
      || entry.canonical.toLowerCase().startsWith(normalizedQuery)
    ))
  }, [currentTokenInsertionRange.query, customDateFormatDraft, customDateFormatTokenGuide])

  function handleDateFormatSelect(nextValue) {
    if (nextValue === DATE_FORMAT_CUSTOM) {
      setShowCustomDateFormatDialog(true)
      return
    }
    onDateFormatChange(nextValue)
  }

  function saveCustomDateFormat() {
    if (!customDateFormatValidation.valid) {
      return
    }
    onDateFormatChange(customDateFormatValidation.value)
    setShowCustomDateFormatDialog(false)
  }

  function syncCustomDateFormatSelection(target) {
    setCustomDateFormatSelection({
      end: target.selectionEnd ?? target.value.length,
      start: target.selectionStart ?? target.value.length
    })
  }

  function applyTokenSuggestion(token) {
    const range = findTokenInsertionRange(
      customDateFormatDraft,
      customDateFormatInputRef.current?.selectionStart ?? customDateFormatSelection.start,
      customDateFormatInputRef.current?.selectionEnd ?? customDateFormatSelection.end
    )
    const nextValue = `${customDateFormatDraft.slice(0, range.start)}${token}${customDateFormatDraft.slice(range.end)}`
    const nextCaret = range.start + token.length
    setCustomDateFormatDraft(nextValue)
    setCustomDateFormatSelection({ start: nextCaret, end: nextCaret })
    requestAnimationFrame(() => {
      customDateFormatInputRef.current?.focus()
      customDateFormatInputRef.current?.setSelectionRange(nextCaret, nextCaret)
    })
  }

  return (
    <>
      <ModalDialog
        closeLabel={t('preferences.close')}
        onClose={onClose}
        title={t('preferences.title')}
      >
        <div className="preferences-dialog">
          <label>
            <span>{t('preferences.language')}</span>
            <LanguageMenuButton
              ariaLabel={t('preferences.language')}
              currentLanguage={language}
              disabled={savingLayout}
              menuAlign="start"
              onChange={onLanguageChange}
              options={languageOptions}
            />
          </label>
          <label>
            <span>{t('preferences.timezoneMode')}</span>
            <select
              disabled={savingLayout}
              onChange={(event) => onTimeZoneModeChange(event.target.value)}
              value={timezoneMode}
            >
              <option value="AUTO">{t('preferences.timezoneAuto')}</option>
              <option value="MANUAL">{t('preferences.timezoneManual')}</option>
            </select>
          </label>
          {timezoneMode === 'AUTO' ? (
            <div className="section-copy">{t('preferences.timezoneDetected', { value: detectedTimeZone })}</div>
          ) : (
            <label>
              <span className="field-label-row">
                <span>{t('preferences.timezone')}</span>
                <InfoHint text={t('preferences.timezoneHelp')} />
              </span>
              <select
                disabled={savingLayout}
                onChange={(event) => onTimeZoneChange(event.target.value)}
                value={timezone || detectedTimeZone}
              >
                {selectableTimeZones.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>
          )}
          <label>
            <span className="field-label-row">
              <span>{t('preferences.dateFormat')}</span>
              <InfoHint text={t('preferences.dateFormatHelp')} />
            </span>
            <select
              disabled={savingLayout}
              onChange={(event) => handleDateFormatSelect(event.target.value)}
              value={dateFormatSelectValue}
            >
              {dateFormatOptions.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </label>
          {normalizedCustomDateFormat ? (
            <div className="preferences-custom-format-summary muted-box">
              <div><strong>{normalizedCustomDateFormat}</strong></div>
              <div>{t('preferences.customDateFormatExample', { value: customDateFormatPreview })}</div>
              <div className="preferences-custom-format-actions">
                <button className="secondary" onClick={() => setShowCustomDateFormatDialog(true)} type="button">
                  {t('preferences.customDateFormatEdit')}
                </button>
              </div>
            </div>
          ) : null}
          <label className="checkbox-row">
            <input
              checked={quickSetupVisible}
              disabled={savingLayout || !canHideQuickSetup}
              onChange={(event) => onQuickSetupVisibilityChange(event.target.checked)}
              type="checkbox"
            />
            <span className="field-label-row">
              <span>{t('preferences.showQuickSetup')}</span>
              <InfoHint text={canHideQuickSetup ? t('preferences.showQuickSetupHelp') : t('preferences.showQuickSetupForcedHelp')} />
            </span>
          </label>
          <div className="preferences-checkbox-row">
            <span className="field-label-row">
              <span>{t('preferences.layout')}</span>
              <InfoHint text={t('preferences.editLayoutHelp')} />
            </span>
          </div>
          <label className="checkbox-row">
            <input
              checked={persistLayout}
              disabled={savingLayout}
              onChange={(event) => onPersistLayoutChange(event.target.checked)}
              type="checkbox"
            />
            <span className="field-label-row">
              <span>{t('preferences.rememberLayout')}</span>
              <InfoHint text={t('preferences.rememberLayoutHelp')} />
            </span>
          </label>
          <div className="action-row">
            <button
              className="secondary"
              disabled={savingLayout}
              onClick={layoutEditEnabled ? onExitLayoutEditing : onStartLayoutEditing}
              title={layoutEditEnabled ? t('preferences.exitLayoutEditingHint') : t('preferences.editLayoutHint')}
              type="button"
            >
              {layoutEditEnabled ? t('preferences.exitLayoutEditing') : t('preferences.editLayout')}
            </button>
            <button className="secondary" disabled={savingLayout} onClick={onResetLayout} title={t('preferences.resetLayoutHint')} type="button">
              {t('preferences.resetLayout')}
            </button>
          </div>
          {savingLayout ? <div className="section-copy">{t('common.savingLayoutPreference')}</div> : null}
        </div>
      </ModalDialog>
      {showCustomDateFormatDialog ? (
        <ModalDialog
          closeLabel={t('preferences.customDateFormatClose')}
          onClose={() => setShowCustomDateFormatDialog(false)}
          title={t('preferences.customDateFormatTitle')}
        >
          <div className="preferences-dialog">
            <label className="preferences-custom-format-field">
              <span className="field-label-row">
                <span>{t('preferences.customDateFormatPattern')}</span>
                <InfoHint text={customDateFormatTokenSummary} />
              </span>
              <AutocompleteInput
                autoFocus
                inputAriaLabel={t('preferences.customDateFormatPattern')}
                inputRef={customDateFormatInputRef}
                onChange={(event) => {
                  setCustomDateFormatDraft(event.target.value)
                  syncCustomDateFormatSelection(event.target)
                }}
                onClick={(event) => syncCustomDateFormatSelection(event.target)}
                onFocus={(event) => syncCustomDateFormatSelection(event.target)}
                onKeyUp={(event) => syncCustomDateFormatSelection(event.target)}
                onSuggestionSelect={(entry) => applyTokenSuggestion(entry.token)}
                onSelect={(event) => syncCustomDateFormatSelection(event.target)}
                placeholder="YYYY-MM-DD HH:mm:ss"
                renderSuggestion={(entry) => (
                  <span>
                    <span className="preferences-token-suggestion-token">{entry.token}</span>
                    <span>{` - ${entry.description} `}</span>
                    <span className="preferences-token-suggestion-sample">({entry.sample})</span>
                  </span>
                )}
                suggestions={visibleTokenSuggestions}
                suggestionToKey={(entry) => entry.token}
                value={customDateFormatDraft}
              />
            </label>
            {customDateFormatValidation.valid ? (
              <div className="muted-box">{t('preferences.customDateFormatExample', { value: customDateFormatPreview })}</div>
            ) : (
              <div className="muted-box tone-error">{t(`preferences.customDateFormatError.${customDateFormatValidation.reason || 'invalidToken'}`)}</div>
            )}
            <div className="section-copy">{customDateFormatTokenSummary}</div>
            <div className="action-row">
              <button className="secondary" onClick={() => setShowCustomDateFormatDialog(false)} type="button">
                {t('common.cancel')}
              </button>
              <button className="primary" disabled={!customDateFormatValidation.valid} onClick={saveCustomDateFormat} type="button">
                {t('common.save')}
              </button>
            </div>
          </div>
        </ModalDialog>
      ) : null}
    </>
  )
}

export default PreferencesDialog
