import { cp, mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const rootDir = resolve(__dirname, '..')
const workspaceDir = resolve(rootDir, '..')
const srcDir = join(rootDir, 'src')
const sharedSrcDir = join(workspaceDir, 'shared', 'src')
const distDir = join(rootDir, 'dist', 'firefox')

const packageJson = JSON.parse(await readFile(join(rootDir, 'package.json'), 'utf8'))
const manifest = JSON.parse(await readFile(join(srcDir, 'manifest.json'), 'utf8'))
manifest.version = packageJson.version

async function rewriteSharedImports(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  await Promise.all(entries.map(async (entry) => {
    const fullPath = join(directory, entry.name)
    if (entry.isDirectory()) {
      await rewriteSharedImports(fullPath)
      return
    }
    if (!entry.name.endsWith('.js')) {
      return
    }
    const source = await readFile(fullPath, 'utf8')
    await writeFile(fullPath, source.replaceAll('../../shared/src/', './shared/'))
  }))
}

await rm(distDir, { recursive: true, force: true })
await mkdir(distDir, { recursive: true })
await cp(srcDir, distDir, { recursive: true })
await cp(sharedSrcDir, join(distDir, 'shared'), { recursive: true })
await rewriteSharedImports(distDir)
await writeFile(join(distDir, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n')

console.log(`Built Firefox extension in ${distDir}`)
