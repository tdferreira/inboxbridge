import { spawnSync } from 'node:child_process'

const [, , ...vitestArgs] = process.argv
const [majorString] = process.versions.node.split('.')
const nodeMajor = Number.parseInt(majorString, 10)

const nodeArgs = []
const env = { ...process.env }
const hasMaxWorkersArg = vitestArgs.some((arg) => arg === '--maxWorkers' || arg.startsWith('--maxWorkers='))
const defaultMaxWorkers = env.VITEST_MAX_WORKERS || (env.CI ? '1' : '50%')

if (Number.isFinite(nodeMajor) && nodeMajor >= 25) {
  env.NODE_OPTIONS = [process.env.NODE_OPTIONS, '--no-webstorage'].filter(Boolean).join(' ')
}

nodeArgs.push(
  '--max-old-space-size=4096',
  './node_modules/vitest/vitest.mjs',
  ...vitestArgs,
  ...(hasMaxWorkersArg ? [] : ['--maxWorkers', defaultMaxWorkers])
)

const result = spawnSync(process.execPath, nodeArgs, {
  stdio: 'inherit',
  env
})

if (typeof result.status === 'number') {
  process.exit(result.status)
}

if (result.error) {
  throw result.error
}

process.exit(1)
