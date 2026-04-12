import { browserRuntime, setIcon } from './browser.js'

const ICON_SIZES = [16, 32]
const DEFAULT_ICON_PATHS = Object.freeze({
  16: 'icon16.png',
  32: 'icon32.png'
})

const iconImageCache = new Map()

/**
 * Maps the current extension status into a toolbar icon variant. The default
 * state preserves the shared InboxBridge icon, while transient states overlay
 * a compact status marker that works alongside the badge.
 */
export function toolbarIconStateForStatus(status, errorState = null) {
  if (errorState?.kind === 'signed-out') {
    return 'signed-out'
  }
  if (errorState) {
    return 'error'
  }
  if (status?.poll?.running) {
    return 'polling'
  }
  const errorCount = Number(status?.summary?.errorSourceCount || 0)
  if (errorCount > 0) {
    return 'error'
  }
  return 'default'
}

export async function setToolbarIconForState(state, deps = {}) {
  const imageData = await loadToolbarIconImageData(state, deps)
  if (!imageData) {
    return false
  }
  await (deps.setIcon || setIcon)(imageData)
  return true
}

async function loadToolbarIconImageData(state, deps) {
  if (iconImageCache.has(state)) {
    return iconImageCache.get(state)
  }

  const promise = generateToolbarIconImageData(state, deps).catch((error) => {
    iconImageCache.delete(state)
    throw error
  })
  iconImageCache.set(state, promise)
  return promise
}

async function generateToolbarIconImageData(state, deps) {
  const runtime = deps.runtime || browserRuntime()
  const fetchImpl = deps.fetchImpl || globalThis.fetch
  const createImageBitmapImpl = deps.createImageBitmapImpl || globalThis.createImageBitmap
  const OffscreenCanvasImpl = deps.OffscreenCanvasImpl || globalThis.OffscreenCanvas
  if (!runtime?.getURL || !fetchImpl || !createImageBitmapImpl || !OffscreenCanvasImpl) {
    return null
  }

  const imageData = {}
  for (const size of ICON_SIZES) {
    const iconBitmap = await fetchIconBitmap(DEFAULT_ICON_PATHS[size], runtime, fetchImpl, createImageBitmapImpl)
    imageData[size] = renderIconVariant(iconBitmap, size, state, OffscreenCanvasImpl)
  }
  return imageData
}

async function fetchIconBitmap(path, runtime, fetchImpl, createImageBitmapImpl) {
  const response = await fetchImpl(runtime.getURL(path))
  const blob = await response.blob()
  return createImageBitmapImpl(blob)
}

function renderIconVariant(bitmap, size, state, OffscreenCanvasImpl) {
  const canvas = new OffscreenCanvasImpl(size, size)
  const context = canvas.getContext('2d')
  context.clearRect(0, 0, size, size)
  context.drawImage(bitmap, 0, 0, size, size)

  if (state !== 'default') {
    drawStateOverlay(context, size, state)
  }

  return context.getImageData(0, 0, size, size)
}

function drawStateOverlay(context, size, state) {
  const radius = size <= 16 ? 5.25 : 9
  const centerX = size - radius - 1
  const centerY = size - radius - 1
  const styles = overlayStyleForState(state)

  context.save()
  context.beginPath()
  context.arc(centerX, centerY, radius, 0, Math.PI * 2)
  context.fillStyle = styles.fill
  context.fill()
  context.lineWidth = size <= 16 ? 1.4 : 2
  context.strokeStyle = styles.stroke
  context.stroke()

  if (state === 'polling') {
    drawPollingSyncGlyph(context, centerX, centerY, radius, styles.glyph, size)
    context.restore()
    return
  }

  context.fillStyle = styles.glyph
  context.font = `${size <= 16 ? '700 8px' : '700 14px'} sans-serif`
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.fillText(state === 'signed-out' ? '?' : '!', centerX, centerY + (size <= 16 ? 0.4 : 0.6))
  context.restore()
}

function overlayStyleForState(state) {
  if (state === 'polling') {
    return {
      fill: '#1f6feb',
      stroke: 'rgba(255, 255, 255, 0.92)',
      glyph: '#ffffff'
    }
  }
  if (state === 'signed-out') {
    return {
      fill: '#5b6475',
      stroke: 'rgba(255, 255, 255, 0.9)',
      glyph: '#ffffff'
    }
  }
  return {
    fill: '#cf222e',
    stroke: 'rgba(255, 255, 255, 0.92)',
    glyph: '#ffffff'
  }
}

function drawPollingSyncGlyph(context, centerX, centerY, radius, color, size) {
  const arcRadius = radius * 0.52
  const lineWidth = size <= 16 ? 1.4 : 2
  const headLength = size <= 16 ? 2.1 : 3.4

  context.strokeStyle = color
  context.fillStyle = color
  context.lineWidth = lineWidth
  context.lineCap = 'round'

  context.beginPath()
  context.arc(centerX, centerY, arcRadius, Math.PI * 0.22, Math.PI * 1.3)
  context.stroke()
  drawArrowHead(context, centerX, centerY, arcRadius, Math.PI * 1.3, headLength)

  context.beginPath()
  context.arc(centerX, centerY, arcRadius, Math.PI * 1.22, Math.PI * 0.3)
  context.stroke()
  drawArrowHead(context, centerX, centerY, arcRadius, Math.PI * 0.3, headLength)
}

function drawArrowHead(context, centerX, centerY, radius, angle, headLength) {
  const tipX = centerX + Math.cos(angle) * radius
  const tipY = centerY + Math.sin(angle) * radius
  const spread = Math.PI / 5.6
  const leftAngle = angle + Math.PI - spread
  const rightAngle = angle + Math.PI + spread

  context.beginPath()
  context.moveTo(tipX, tipY)
  context.lineTo(tipX + Math.cos(leftAngle) * headLength, tipY + Math.sin(leftAngle) * headLength)
  context.lineTo(tipX + Math.cos(rightAngle) * headLength, tipY + Math.sin(rightAngle) * headLength)
  context.closePath()
  context.fill()
}
