import { getCurrentFormattingTimeZone } from '@/lib/timeZonePreferences'

export function statsTimezoneHeader(timeZone = getCurrentFormattingTimeZone()) {
  if (!timeZone) {
    return {}
  }
  return { 'X-InboxBridge-Timezone': timeZone }
}
