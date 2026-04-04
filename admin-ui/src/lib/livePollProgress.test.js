import { formatLiveProgressLabel, formatLiveProgressSummary, isLiveSourceFinalizing } from './livePollProgress'
import { translate } from './i18n'

describe('livePollProgress', () => {
  const t = (key, params) => translate('en', key, params)

  it('formats combined progress while a source is still processing messages', () => {
    const source = {
      state: 'RUNNING',
      totalMessages: 50,
      processedMessages: 3,
      totalBytes: 6 * 1024 * 1024,
      processedBytes: 1536 * 1024
    }

    expect(isLiveSourceFinalizing(source)).toBe(false)
    expect(formatLiveProgressLabel(source, t)).toBe('Processing 3 / 50 emails')
    expect(formatLiveProgressSummary(source, 'en', t)).toBe('Processing 3 / 50 emails (1.5 MB / 6 MB)')
  })

  it('switches to finalizing copy once the fetched batch has been fully processed', () => {
    const source = {
      state: 'RUNNING',
      totalMessages: 50,
      processedMessages: 50,
      totalBytes: Math.round(2.9 * 1024 * 1024),
      processedBytes: Math.round(2.9 * 1024 * 1024)
    }

    expect(isLiveSourceFinalizing(source)).toBe(true)
    expect(formatLiveProgressLabel(source, t)).toBe('Finalizing 50 emails')
    expect(formatLiveProgressSummary(source, 'en', t)).toBe('Finalizing 50 emails (2.9 MB)')
  })
})
