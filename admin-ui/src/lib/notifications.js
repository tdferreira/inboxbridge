import { formatPollError } from '@/lib/formatters'
import { translate } from '@/lib/i18n'

export function translatedNotification(key, params = {}) {
  return { kind: 'translation', key, params }
}

export function pollErrorNotification(value) {
  return { kind: 'pollError', value }
}

export function resolveNotificationContent(content, language = 'en') {
  if (content == null) {
    return ''
  }
  if (typeof content === 'string') {
    return content
  }
  if (typeof content !== 'object') {
    return String(content)
  }
  if (content.kind === 'translation' && content.key) {
    const resolvedParams = Object.fromEntries(
      Object.entries(content.params || {}).map(([key, value]) => [key, resolveNotificationContent(value, language)])
    )
    return translate(language, content.key, resolvedParams)
  }
  if (content.kind === 'pollError') {
    if (Array.isArray(content.value)) {
      return content.value.map((entry) => formatPollError(entry, language)).filter(Boolean).join('\n')
    }
    return formatPollError(content.value, language)
  }
  return String(content.message || '')
}

export function notificationDeduplicationKey(notification, language = 'en') {
  if (!notification || typeof notification !== 'object') {
    return null
  }

  if (typeof notification.groupKey === 'string' && notification.groupKey.trim()) {
    return `group:${notification.groupKey.trim()}`
  }

  const tone = notification.tone || 'success'
  if (tone !== 'error' && tone !== 'warning') {
    return null
  }

  const resolvedMessage = resolveNotificationContent(notification.message, language).trim()
  const resolvedCopyText = resolveNotificationContent(
    notification.copyText ?? notification.message,
    language
  ).trim()

  if (!resolvedMessage && !resolvedCopyText) {
    return null
  }

  return `tone:${tone}|message:${resolvedMessage}|copy:${resolvedCopyText}`
}
