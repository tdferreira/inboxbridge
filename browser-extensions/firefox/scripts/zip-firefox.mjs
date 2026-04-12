import { mkdir, readFile } from 'node:fs/promises'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'

const execFileAsync = promisify(execFile)
const __dirname = dirname(fileURLToPath(import.meta.url))
const rootDir = resolve(__dirname, '..')
const distDir = join(rootDir, 'dist', 'firefox')
const packagesDir = join(rootDir, 'dist', 'packages')
const packageJson = JSON.parse(await readFile(join(rootDir, 'package.json'), 'utf8'))
const packageName = `inboxbridge-firefox-v${packageJson.version}.xpi`

await mkdir(packagesDir, { recursive: true })
await execFileAsync('node', [join(rootDir, 'scripts', 'build-firefox.mjs')], { cwd: rootDir })
await execFileAsync('zip', ['-qr', join(packagesDir, packageName), '.'], { cwd: distDir })

console.log(`Packaged Firefox extension in ${join(packagesDir, packageName)}`)
