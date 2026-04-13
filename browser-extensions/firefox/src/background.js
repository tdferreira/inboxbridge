import { fetchStatus, subscribeExtensionEvents } from '../../shared/src/api.js'
import {
  browserRuntime,
  createContextMenu,
  createNotification,
  clearBadge,
  createAlarm,
  onAlarm,
  onContextMenuClicked,
  onInstalled,
  onMessage,
  openOptionsPage,
  removeAllContextMenus,
  sendMessage,
  setBadge
} from '../../shared/src/browser.js'
import { badgeStateForStatus } from '../../shared/src/badge-state.js'
import { registerBackgroundController } from '../../shared/src/background-controller.js'
import { clearConfig, loadConfig, saveUserPreferences } from '../../shared/src/config.js'
import { resolveLanguagePreference, translate } from '../../shared/src/i18n.js'
import { runPoll } from '../../shared/src/api.js'
import {
  mergeLivePollIntoStatus,
  shouldOverlayLiveStatus,
  shouldRefreshStatusFromLiveEvent
} from '../../shared/src/live-status.js'
import { withBrowserNotificationDefaults } from '../../shared/src/notifications.js'
import { loadCachedStatus, saveCachedStatus } from '../../shared/src/status-cache.js'
import { setToolbarIconForState, toolbarIconStateForStatus } from '../../shared/src/toolbar-icon.js'

async function applyBadge(status, errorMessage = null) {
  const badge = badgeStateForStatus(status, errorMessage)
  if (badge.clear) {
    await clearBadge(badge.title)
    return
  }
  await setBadge(badge.text, badge.color, badge.title)
}

async function applyIcon(status, errorState = null) {
  await setToolbarIconForState(toolbarIconStateForStatus(status, errorState))
}

async function showBrowserNotification(notificationId, options) {
  await createNotification(notificationId, withBrowserNotificationDefaults(options, {
    iconUrl: browserRuntime().getURL('icon128.png')
  }))
}

registerBackgroundController({
  deps: {
    applyBadge,
    applyIcon,
    createContextMenu,
    createNotification: showBrowserNotification,
    clearBadge,
    clearConfig,
    createAlarm,
    fetchStatus,
    loadCachedStatus,
    loadConfig,
    mergeLivePollIntoStatus,
    onAlarm,
    onContextMenuClicked,
    onInstalled,
    onMessage,
    openOptionsPage,
    removeAllContextMenus,
    runPoll,
    saveCachedStatus,
    saveUserPreferences,
    sendMessage,
    resolveLanguagePreference,
    shouldOverlayLiveStatus,
    shouldRefreshStatusFromLiveEvent,
    subscribeExtensionEvents,
    translate
  }
})
