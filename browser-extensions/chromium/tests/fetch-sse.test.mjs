import test from 'node:test'
import assert from 'node:assert/strict'

import { subscribeToJsonSse } from '../../shared/src/fetch-sse.js'

function streamFromChunks(chunks) {
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(new TextEncoder().encode(chunk))
      }
      controller.close()
    }
  })
}

test('subscribeToJsonSse parses JSON events from an authenticated SSE stream', async () => {
  const events = []
  const stream = subscribeToJsonSse({
    fetchImpl: async (url, options) => {
      assert.equal(url, 'https://mail.example.com/api/extension/events')
      assert.equal(options.headers.Authorization, 'Bearer token-1')
      return {
        ok: true,
        status: 200,
        body: streamFromChunks([
          'data: {"type":"poll-run-started","poll":{"running":true}}\n\n',
          'data: {"type":"keepalive"}\n\n'
        ])
      }
    },
    onEvent(event) {
      events.push(event)
    },
    token: 'token-1',
    url: 'https://mail.example.com/api/extension/events'
  })

  await stream.completed

  assert.deepEqual(events, [
    { type: 'poll-run-started', poll: { running: true } },
    { type: 'keepalive' }
  ])
})

test('subscribeToJsonSse surfaces unauthorized failures clearly', async () => {
  let disconnectedError = null
  const stream = subscribeToJsonSse({
    fetchImpl: async () => ({
      ok: false,
      status: 401,
      body: streamFromChunks([])
    }),
    onDisconnect(error) {
      disconnectedError = error
    },
    token: 'token-1',
    url: 'https://mail.example.com/api/extension/events'
  })

  await stream.completed

  assert.match(disconnectedError.message, /no longer valid/i)
})
