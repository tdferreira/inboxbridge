import { useEffect, useMemo, useState } from 'react'
import { apiErrorText } from './api'
import { languageOptions, normalizeLocale, translate } from './i18n'
import { pollErrorNotification, translatedNotification } from './notifications'
import {
  applyLayoutPreferences,
  captureLayoutPreferences,
  DEFAULT_UI_PREFERENCES,
  hasLayoutPreferenceChanges,
  normalizeUiPreferences
} from './workspacePreferences'

export function useWorkspacePreferencesController({ language, pushNotification, session, setLanguage, withPending }) {
  const [uiPreferences, setUiPreferences] = useState(DEFAULT_UI_PREFERENCES)
  const [uiPreferencesLoadedForUserId, setUiPreferencesLoadedForUserId] = useState(null)
  const [showPreferencesDialog, setShowPreferencesDialog] = useState(false)
  const [showNotificationsDialog, setShowNotificationsDialog] = useState(false)
  const [dragState, setDragState] = useState(null)
  const [layoutEditSnapshot, setLayoutEditSnapshot] = useState(null)

  const selectableLanguages = useMemo(() => languageOptions.map((value) => ({
    value,
    label: translate(language, `language.${value}`)
  })), [language])

  async function persistUiPreferences(nextPreferences) {
    if (!session) {
      return
    }
    await withPending('uiPreferences', async () => {
      try {
        const response = await fetch('/api/app/ui-preferences', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(nextPreferences)
        })
        if (!response.ok) {
          throw new Error(await apiErrorText(response, translate(language, 'errors.saveLayoutPreference')))
        }
        const payload = await response.json()
        const normalized = normalizeUiPreferences(payload)
        setUiPreferences(normalized)
        setLanguage(normalized.language)
      } catch (err) {
        pushNotification({
          autoCloseMs: null,
          copyText: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveLayoutPreference'),
          message: err.message ? pollErrorNotification(err.message) : translatedNotification('errors.saveLayoutPreference'),
          tone: 'error'
        })
      }
    })
  }

  function applyLoadedUiPreferences(payload, userId) {
    if (uiPreferencesLoadedForUserId === userId) {
      return
    }
    const nextUiPreferences = normalizeUiPreferences(payload)
    setUiPreferences(nextUiPreferences)
    setLanguage(nextUiPreferences.language)
    setUiPreferencesLoadedForUserId(userId)
  }

  async function updateUiPreferencesLocally(nextPreferences) {
    const normalized = normalizeUiPreferences(nextPreferences)
    setUiPreferences(normalized)
    setLanguage(normalized.language)
    if (normalized.persistLayout) {
      await persistUiPreferences(normalized)
    }
  }

  async function moveSection(workspaceKey, sectionId, direction) {
    const preferenceKey = workspaceKey === 'admin' ? 'adminSectionOrder' : 'userSectionOrder'
    const currentOrder = [...uiPreferences[preferenceKey]]
    const currentIndex = currentOrder.indexOf(sectionId)
    if (currentIndex < 0) {
      return
    }
    const targetIndex = direction === 'up' ? currentIndex - 1 : currentIndex + 1
    if (targetIndex < 0 || targetIndex >= currentOrder.length) {
      return
    }
    const nextOrder = [...currentOrder]
    ;[nextOrder[currentIndex], nextOrder[targetIndex]] = [nextOrder[targetIndex], nextOrder[currentIndex]]
    await updateUiPreferencesLocally({
      ...uiPreferences,
      [preferenceKey]: nextOrder
    })
  }

  async function reorderSections(workspaceKey, draggedId, targetIndex) {
    const preferenceKey = workspaceKey === 'admin' ? 'adminSectionOrder' : 'userSectionOrder'
    const currentOrder = [...uiPreferences[preferenceKey]]
    const currentIndex = currentOrder.indexOf(draggedId)
    if (currentIndex < 0) {
      return
    }
    const boundedTargetIndex = Math.max(0, Math.min(targetIndex, currentOrder.length))
    const [movedSection] = currentOrder.splice(currentIndex, 1)
    const adjustedTargetIndex = currentIndex < boundedTargetIndex ? boundedTargetIndex - 1 : boundedTargetIndex
    currentOrder.splice(adjustedTargetIndex, 0, movedSection)
    await updateUiPreferencesLocally({
      ...uiPreferences,
      [preferenceKey]: currentOrder
    })
  }

  async function resetLayoutPreferences() {
    await updateUiPreferencesLocally({
      ...uiPreferences,
      ...DEFAULT_UI_PREFERENCES,
      persistLayout: uiPreferences.persistLayout,
      language
    })
    pushNotification({ message: translatedNotification('notifications.layoutReset'), tone: 'success' })
  }

  async function toggleSection(sectionKey, onExpand) {
    const wasCollapsed = uiPreferences[sectionKey]
    const nextPreferences = {
      ...uiPreferences,
      [sectionKey]: !uiPreferences[sectionKey]
    }
    setUiPreferences(nextPreferences)
    if (nextPreferences.persistLayout) {
      await persistUiPreferences(nextPreferences)
    }
    if (wasCollapsed && typeof onExpand === 'function') {
      await onExpand()
    }
  }

  async function expandSection(sectionKey) {
    if (!uiPreferences[sectionKey]) {
      return
    }
    const nextPreferences = {
      ...uiPreferences,
      [sectionKey]: false
    }
    setUiPreferences(nextPreferences)
    if (nextPreferences.persistLayout) {
      await persistUiPreferences(nextPreferences)
    }
  }

  function handlePersistLayoutChange(enabled) {
    const nextPreferences = {
      ...uiPreferences,
      persistLayout: enabled
    }
    setUiPreferences(nextPreferences)
    void persistUiPreferences(nextPreferences)
  }

  function handleLayoutEditChange(enabled) {
    if (enabled && !layoutEditSnapshot) {
      setLayoutEditSnapshot(captureLayoutPreferences(uiPreferences))
    }
    const nextPreferences = {
      ...uiPreferences,
      layoutEditEnabled: enabled
    }
    setUiPreferences(nextPreferences)
    if (!enabled) {
      void persistUiPreferences(nextPreferences)
    }
  }

  function startLayoutEditingFromPreferences() {
    setShowPreferencesDialog(false)
    handleLayoutEditChange(true)
  }

  async function commitLayoutEditingChanges() {
    const nextPreferences = {
      ...uiPreferences,
      layoutEditEnabled: false
    }
    setDragState(null)
    setLayoutEditSnapshot(null)
    setUiPreferences(nextPreferences)
    await persistUiPreferences(nextPreferences)
  }

  async function discardLayoutEditingChanges() {
    const restoredPreferences = applyLayoutPreferences({
      ...uiPreferences,
      layoutEditEnabled: false
    }, layoutEditSnapshot)
    setDragState(null)
    setLayoutEditSnapshot(null)
    setUiPreferences(restoredPreferences)
    await persistUiPreferences(restoredPreferences)
  }

  function handleQuickSetupVisibilityChange(visible, allStepsComplete) {
    const nextPreferences = {
      ...uiPreferences,
      quickSetupPinnedVisible: visible,
      quickSetupDismissed: visible ? false : allStepsComplete,
      quickSetupCollapsed: visible ? false : true
    }
    setUiPreferences(nextPreferences)
    if (nextPreferences.persistLayout) {
      void persistUiPreferences(nextPreferences)
    }
  }

  function handleLanguageChange(nextLanguage) {
    const normalizedLanguage = normalizeLocale(nextLanguage)
    const nextPreferences = {
      ...uiPreferences,
      language: normalizedLanguage
    }
    setUiPreferences(nextPreferences)
    setLanguage(normalizedLanguage)
    void persistUiPreferences(nextPreferences)
  }

  function resetLayoutState() {
    setUiPreferences(DEFAULT_UI_PREFERENCES)
    setUiPreferencesLoadedForUserId(null)
    setShowPreferencesDialog(false)
    setShowNotificationsDialog(false)
    setDragState(null)
    setLayoutEditSnapshot(null)
  }

  useEffect(() => {
    window.localStorage.setItem('inboxbridge.language', language)
  }, [language])

  useEffect(() => {
    if (!uiPreferences.layoutEditEnabled && dragState) {
      setDragState(null)
    }
  }, [dragState, uiPreferences.layoutEditEnabled])

  return {
    applyLoadedUiPreferences,
    closeNotificationsDialog: () => setShowNotificationsDialog(false),
    closePreferencesDialog: () => setShowPreferencesDialog(false),
    commitLayoutEditingChanges,
    discardLayoutEditingChanges,
    dragState,
    expandSection,
    handleLanguageChange,
    handleLayoutEditChange,
    handlePersistLayoutChange,
    handleQuickSetupVisibilityChange,
    hasUnsavedLayoutEdits: hasLayoutPreferenceChanges(uiPreferences, layoutEditSnapshot),
    language,
    moveSection,
    openNotificationsDialog: () => setShowNotificationsDialog(true),
    openPreferencesDialog: () => setShowPreferencesDialog(true),
    reorderSections,
    resetLayoutPreferences,
    resetLayoutState,
    selectableLanguages,
    setDragState,
    showNotificationsDialog,
    showPreferencesDialog,
    startLayoutEditingFromPreferences,
    toggleSection,
    uiPreferencesLoadedForUserId,
    uiPreferences
  }
}
