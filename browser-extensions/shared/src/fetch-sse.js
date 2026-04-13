import { createInvalidExtensionAuthError } from './auth-errors.js'

const STREAM_TIMEOUT_MS = 60_000

/**
 * Opens an authenticated SSE stream over fetch so the extension can keep using
 * bearer-token auth instead of weakening the model for EventSource.
 */
export function subscribeToJsonSse({
  fetchImpl = globalThis.fetch,
  onDisconnect,
  onEvent,
  timeoutMs = STREAM_TIMEOUT_MS,
  token,
  url
}) {
  const controller = new AbortController()
  const completed = consume()

  async function consume() {
    const timer = setTimeout(() => controller.abort(new Error('InboxBridge live updates timed out.')), timeoutMs)
    try {
      const response = await fetchImpl(url, {
        headers: {
          Accept: 'text/event-stream',
          Authorization: `Bearer ${token}`,
          'Cache-Control': 'no-cache'
        },
        signal: controller.signal
      })
      const payload = await parseEventStreamResponse(response, onEvent)
      onDisconnect?.(payload)
    } catch (error) {
      if (controller.signal.aborted) {
        return
      }
      onDisconnect?.(error)
    } finally {
      clearTimeout(timer)
    }
  }

  return {
    close() {
      controller.abort()
    },
    completed
  }
}

async function parseEventStreamResponse(response, onEvent) {
  if (response.status === 401) {
    throw createInvalidExtensionAuthError('The saved InboxBridge sign-in is no longer valid.')
  }
  if (!response.ok) {
    throw new Error(`InboxBridge returned ${response.status}.`)
  }
  const reader = response.body?.getReader?.()
  if (!reader) {
    throw new Error('InboxBridge live updates are unavailable in this browser.')
  }

  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      buffer += decoder.decode()
      flushBufferedEvents(buffer, onEvent)
      return null
    }
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split(/\r?\n\r?\n/g)
    buffer = parts.pop() || ''
    for (const part of parts) {
      emitParsedEvent(part, onEvent)
    }
  }
}

function flushBufferedEvents(buffer, onEvent) {
  const trimmed = buffer.trim()
  if (!trimmed) {
    return
  }
  emitParsedEvent(trimmed, onEvent)
}

function emitParsedEvent(chunk, onEvent) {
  const lines = chunk.split(/\r?\n/g)
  let data = ''
  for (const line of lines) {
    if (!line || line.startsWith(':')) {
      continue
    }
    if (line.startsWith('data:')) {
      data += `${line.slice(5).trimStart()}\n`
    }
  }
  if (!data) {
    return
  }
  onEvent?.(JSON.parse(data.trimEnd()))
}
