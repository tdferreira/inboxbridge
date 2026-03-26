export function apiErrorText(response, fallback) {
  return response.text().then((text) => {
    if (!text) return fallback
    try {
      const parsed = JSON.parse(text)
      return parsed.details || parsed.message || fallback
    } catch {
      return text
    }
  })
}
