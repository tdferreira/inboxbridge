import { getCurrentFormattingTimeZone } from './timeZonePreferences'

export function statsTimezoneHeader(timeZone = getCurrentFormattingTimeZone()) {
  if (!timeZone) {
    return {}
  }
  return { 'X-InboxBridge-Timezone': timeZone }
}
