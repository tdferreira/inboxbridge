import { useEffect, useMemo, useRef, useState } from 'react'
import { apiErrorText } from './api'
import { languageOptions, normalizeLocale, translate } from './i18n'
import { pollErrorNotification, translatedNotification } from './notifications'
import {
    applyLayoutPreferences,
  applyOrderedSectionIds,
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
  const uiPreferencesRef = useRef(DEFAULT_UI_PREFERENCES)
  const layoutEditSnapshotRef = useRef(null)

  function commitUiPreferences(nextPreferences) {
    uiPreferencesRef.current = nextPreferences
    setUiPreferences(nextPreferences)
  }

  function commitLayoutEditSnapshot(nextSnapshot) {
    layoutEditSnapshotRef.current = nextSnapshot
    setLayoutEditSnapshot(nextSnapshot)
  }

  function normalizeUiPreferencesWithTransientState(nextPreferences) {
    const normalized = normalizeUiPreferences(nextPreferences)
    return {
      ...normalized,
      layoutEditEnabled: nextPreferences.layoutEditEnabled ?? uiPreferencesRef.current.layoutEditEnabled
    }
  }

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
        const normalized = normalizeUiPreferencesWithTransientState({
          ...payload,
          layoutEditEnabled: nextPreferences.layoutEditEnabled
        })
        commitUiPreferences(normalized)
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
    if (layoutEditSnapshotRef.current) {
      nextUiPreferences.layoutEditEnabled = uiPreferencesRef.current.layoutEditEnabled
    }
    commitUiPreferences(nextUiPreferences)
    setLanguage(nextUiPreferences.language)
    setUiPreferencesLoadedForUserId(userId)
  }

  async function updateUiPreferencesLocally(nextPreferences) {
    const normalized = normalizeUiPreferencesWithTransientState(nextPreferences)
    commitUiPreferences(normalized)
    setLanguage(normalized.language)
    if (normalized.persistLayout) {
      await persistUiPreferences(normalized)
    }
  }

  async function moveSection(workspaceKey, sectionId, direction, availableSectionIds = null) {
    const currentPreferences = uiPreferencesRef.current
    const currentLayoutEditSnapshot = layoutEditSnapshotRef.current
    const preferenceKey = workspaceKey === 'admin' ? 'adminSectionOrder' : 'userSectionOrder'
    const baselineOrder = Array.isArray(availableSectionIds) && availableSectionIds.length
      ? availableSectionIds
      : DEFAULT_UI_PREFERENCES[preferenceKey]
    const currentOrder = applyOrderedSectionIds(baselineOrder, currentPreferences[preferenceKey])
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
      ...currentPreferences,
      layoutEditEnabled: currentPreferences.layoutEditEnabled || currentLayoutEditSnapshot != null,
      [preferenceKey]: nextOrder
    })
  }

  async function reorderSections(workspaceKey, draggedId, targetIndex, availableSectionIds = null) {
    const currentPreferences = uiPreferencesRef.current
    const currentLayoutEditSnapshot = layoutEditSnapshotRef.current
    const preferenceKey = workspaceKey === 'admin' ? 'adminSectionOrder' : 'userSectionOrder'
    const baselineOrder = Array.isArray(availableSectionIds) && availableSectionIds.length
      ? availableSectionIds
      : DEFAULT_UI_PREFERENCES[preferenceKey]
    const currentOrder = applyOrderedSectionIds(baselineOrder, currentPreferences[preferenceKey])
    const currentIndex = currentOrder.indexOf(draggedId)
    if (currentIndex < 0) {
      return
    }
    const boundedTargetIndex = Math.max(0, Math.min(targetIndex, currentOrder.length))
    const [movedSection] = currentOrder.splice(currentIndex, 1)
    const adjustedTargetIndex = currentIndex < boundedTargetIndex ? boundedTargetIndex - 1 : boundedTargetIndex
    currentOrder.splice(adjustedTargetIndex, 0, movedSection)
    await updateUiPreferencesLocally({
      ...currentPreferences,
      layoutEditEnabled: currentPreferences.layoutEditEnabled || currentLayoutEditSnapshot != null,
      [preferenceKey]: currentOrder
    })
  }

  async function resetLayoutPreferences() {
    const currentPreferences = uiPreferencesRef.current
    await updateUiPreferencesLocally({
      ...currentPreferences,
      ...DEFAULT_UI_PREFERENCES,
      persistLayout: currentPreferences.persistLayout,
      language
    })
    pushNotification({ message: translatedNotification('notifications.layoutReset'), tone: 'success' })
  }

  async function toggleSection(sectionKey, onExpand) {
    const currentPreferences = uiPreferencesRef.current
    const wasCollapsed = currentPreferences[sectionKey]
    const nextPreferences = {
      ...currentPreferences,
      [sectionKey]: !currentPreferences[sectionKey]
    }
    commitUiPreferences(nextPreferences)
    if (nextPreferences.persistLayout) {
      await persistUiPreferences(nextPreferences)
    }
    if (wasCollapsed && typeof onExpand === 'function') {
      await onExpand()
    }
  }

  async function expandSection(sectionKey) {
    const currentPreferences = uiPreferencesRef.current
    if (!currentPreferences[sectionKey]) {
      return
    }
    const nextPreferences = {
      ...currentPreferences,
      [sectionKey]: false
    }
    commitUiPreferences(nextPreferences)
    if (nextPreferences.persistLayout) {
      await persistUiPreferences(nextPreferences)
    }
  }

  function handlePersistLayoutChange(enabled) {
    const currentPreferences = uiPreferencesRef.current
    const nextPreferences = {
      ...currentPreferences,
      persistLayout: enabled
    }
    commitUiPreferences(nextPreferences)
    void persistUiPreferences(nextPreferences)
  }

  function handleLayoutEditChange(enabled) {
    const currentPreferences = uiPreferencesRef.current
    if (enabled && !layoutEditSnapshotRef.current) {
      commitLayoutEditSnapshot(captureLayoutPreferences(currentPreferences))
    }
    const nextPreferences = {
      ...currentPreferences,
      layoutEditEnabled: enabled
    }
    commitUiPreferences(nextPreferences)
    if (!enabled) {
      void persistUiPreferences(nextPreferences)
    }
  }

  function startLayoutEditingFromPreferences() {
    setShowPreferencesDialog(false)
    handleLayoutEditChange(true)
  }

  async function commitLayoutEditingChanges() {
    const currentPreferences = uiPreferencesRef.current
    const nextPreferences = {
      ...currentPreferences,
      layoutEditEnabled: false
    }
    setDragState(null)
    commitLayoutEditSnapshot(null)
    commitUiPreferences(nextPreferences)
    await persistUiPreferences(nextPreferences)
  }

  async function discardLayoutEditingChanges() {
    const currentPreferences = uiPreferencesRef.current
    const restoredPreferences = applyLayoutPreferences({
      ...currentPreferences,
      layoutEditEnabled: false
    }, layoutEditSnapshotRef.current)
    setDragState(null)
    commitLayoutEditSnapshot(null)
    commitUiPreferences(restoredPreferences)
    await persistUiPreferences(restoredPreferences)
  }

  function handleQuickSetupVisibilityChange(workspaceKey, visible, allStepsComplete) {
    const currentPreferences = uiPreferencesRef.current
    const dismissedKey = workspaceKey === 'admin' ? 'adminQuickSetupDismissed' : 'quickSetupDismissed'
    const pinnedVisibleKey = workspaceKey === 'admin' ? 'adminQuickSetupPinnedVisible' : 'quickSetupPinnedVisible'
    const collapsedKey = workspaceKey === 'admin' ? 'adminQuickSetupCollapsed' : 'quickSetupCollapsed'
    const nextPreferences = {
      ...currentPreferences,
      [pinnedVisibleKey]: visible,
      [dismissedKey]: visible ? false : allStepsComplete,
      [collapsedKey]: visible ? false : true
    }
    commitUiPreferences(nextPreferences)
    if (nextPreferences.persistLayout) {
      void persistUiPreferences(nextPreferences)
    }
  }

  function handleLanguageChange(nextLanguage) {
    const normalizedLanguage = normalizeLocale(nextLanguage)
    const currentPreferences = uiPreferencesRef.current
    const nextPreferences = {
      ...currentPreferences,
      language: normalizedLanguage
    }
    commitUiPreferences(nextPreferences)
    setLanguage(normalizedLanguage)
    void persistUiPreferences(nextPreferences)
  }

  function resetLayoutState() {
    commitUiPreferences(DEFAULT_UI_PREFERENCES)
    setUiPreferencesLoadedForUserId(null)
    setShowPreferencesDialog(false)
    setShowNotificationsDialog(false)
    setDragState(null)
    commitLayoutEditSnapshot(null)
  }

  async function replaceNotificationHistory(notificationHistory) {
    const currentPreferences = uiPreferencesRef.current
    const nextPreferences = {
      ...currentPreferences,
      notificationHistory
    }
    commitUiPreferences(nextPreferences)
    await persistUiPreferences(nextPreferences)
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
    uiPreferences,
    replaceNotificationHistory
  }
}
