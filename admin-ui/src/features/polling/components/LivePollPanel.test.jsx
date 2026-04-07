import { render, screen } from '@testing-library/react'
import LivePollPanel from './LivePollPanel'
import { translate } from '@/lib/i18n'

const t = (key, params) => translate('en', key, params)

describe('LivePollPanel', () => {
  it('shows determinate per-source progress while a source is running', () => {
    render(
      <LivePollPanel
        livePoll={{
          running: true,
          state: 'RUNNING',
          viewerCanControl: true,
          ownerUsername: 'admin',
          sources: [
            {
              sourceId: 'source-1',
              label: 'Main Inbox',
              state: 'RUNNING',
              actionable: true,
              position: 1,
              attempt: 1,
              totalMessages: 50,
              processedMessages: 3,
              totalBytes: 6 * 1024 * 1024,
              processedBytes: 1536 * 1024,
              fetched: 3,
              imported: 2,
              duplicates: 1
            }
          ]
        }}
        onMoveNext={() => {}}
        onPause={() => {}}
        onResume={() => {}}
        onRetry={() => {}}
        onStop={() => {}}
        t={t}
      />
    )

    expect(screen.getByText('Processing 3 / 50 emails (1.5 MB / 6 MB)')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Processing 3 / 50 emails' })).toBeInTheDocument()
    expect(screen.getByText('Fetched 3, imported 2, duplicates 1')).toBeInTheDocument()
  })
})
