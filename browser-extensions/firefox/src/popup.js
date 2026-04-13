import { runPoll, fetchStatus } from '../../shared/src/api.js'
import { onMessage, openOptionsPage, openTab, sendMessage } from '../../shared/src/browser.js'
import { clearConfig, loadConfig, saveUserPreferences } from '../../shared/src/config.js'
import { localizePopupPage, resolveLanguagePreference, translate } from '../../shared/src/i18n.js'
import { createPopupController } from '../../shared/src/popup-controller.js'
import { deriveStatusView, disconnectedView, escapeHtml } from '../../shared/src/popup-view.js'
import { applyThemePreference } from '../../shared/src/theme.js'

export function clearStatusBanner(target) {
  target.hidden = true
  target.className = 'status-banner'
  target.textContent = ''
}

export function showStatusBanner(target, tone, text) {
  target.hidden = false
  target.className = `status-banner ${tone}`
  target.textContent = text
}

const controller = createPopupController({
  deps: {
    applyThemePreference,
    clearConfig,
    clearStatusBanner,
    deriveStatusView,
    disconnectedView,
    escapeHtml,
    fetchStatus,
    localizePopupPage,
    loadConfig,
    onMessage,
    openOptionsPage,
    openTab,
    resolveLanguagePreference,
    runPoll,
    saveUserPreferences,
    sendMessage,
    showStatusBanner,
    targetDocument: document,
    translate
  },
  elements: {
    attentionCount: document.getElementById('attention-count'),
    connectionCopy: document.getElementById('connection-copy'),
    errorList: document.getElementById('error-list'),
    healthyState: document.getElementById('healthy-state'),
    metricDuplicates: document.getElementById('metric-duplicates'),
    metricErrors: document.getElementById('metric-errors'),
    metricFetched: document.getElementById('metric-fetched'),
    metricImported: document.getElementById('metric-imported'),
    openAppButton: document.getElementById('open-app'),
    openRemoteButton: document.getElementById('open-remote'),
    openSettingsButton: document.getElementById('open-settings'),
    popupStatus: document.getElementById('popup-status'),
    refreshStatusButton: document.getElementById('refresh-status'),
    runPollButton: document.getElementById('run-poll'),
    summaryCard: document.getElementById('summary-card'),
    statusPill: document.getElementById('status-pill'),
    updatedAt: document.getElementById('updated-at')
  }
})

controller.initialize()
void controller.refreshPopup()
