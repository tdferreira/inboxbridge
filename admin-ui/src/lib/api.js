export function apiErrorText(response, fallback) {
  return response.text().then((text) => {
    if (!text) return fallback
    const trimmed = text.trim()
    const looksLikeHtml = trimmed.startsWith('<!DOCTYPE') || trimmed.startsWith('<html') || trimmed.startsWith('<HTML')
    try {
      const parsed = JSON.parse(text)
      return parsed.details || parsed.message || fallback
    } catch {
      if (looksLikeHtml) {
        const statusSuffix = response?.status
          ? ` (${response.status}${response.statusText ? ` ${response.statusText}` : ''})`
          : ''
        return `${fallback}${statusSuffix}`
      }
      return text
    }
  })
}
