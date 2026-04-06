export const MAX_NOTIFICATION_HISTORY = 50
export const NOTIFICATION_GROUPING_WINDOW_MS = 8_000

export function normalizeNotificationHistory(notifications) {
  if (!Array.isArray(notifications) || !notifications.length) {
    return []
  }

  return notifications
    .filter((notification) => notification && typeof notification === 'object')
    .filter((notification) => typeof notification.id === 'string' && notification.id.trim())
    .filter((notification) => notification.message != null || notification.copyText != null)
    .map((notification) => {
      const createdAt = Number.isFinite(notification.createdAt) ? notification.createdAt : Date.now()
      const autoCloseMs = Number.isFinite(notification.autoCloseMs) ? notification.autoCloseMs : null
      const age = Date.now() - createdAt
      const floatingVisible = notification.floatingVisible !== false
        && !(autoCloseMs && age >= autoCloseMs)
      return {
        autoCloseMs,
        copyText: notification.copyText ?? notification.message,
        createdAt,
        floatingVisible,
        groupKey: typeof notification.groupKey === 'string' && notification.groupKey ? notification.groupKey : null,
        id: notification.id.trim(),
        repeatCount: Number.isFinite(notification.repeatCount) && notification.repeatCount > 1
          ? Math.floor(notification.repeatCount)
          : 1,
        message: notification.message,
        targetId: typeof notification.targetId === 'string' && notification.targetId ? notification.targetId : null,
        tone: normalizeNotificationTone(notification.tone)
      }
    })
    .slice(-MAX_NOTIFICATION_HISTORY)
}

export function notificationHistoriesEqual(left, right) {
  return JSON.stringify(normalizeNotificationHistory(left)) === JSON.stringify(normalizeNotificationHistory(right))
}

function normalizeNotificationTone(tone) {
  switch (tone) {
    case 'error':
    case 'info':
    case 'success':
    case 'warning':
      return tone
    default:
      return 'success'
  }
}
